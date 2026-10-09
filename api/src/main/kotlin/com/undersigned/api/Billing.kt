package com.undersigned.api

import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * API passes bought with SOL or SKR (docs/API.md §2). A pass is a key with a request allowance and an end date. It is
 * redeemed from one on-chain payment, and only by the wallet that paid: a copied transaction signature is useless without
 * the payer's signature over it. Each transaction redeems once.
 */
class Billing(private val db: Db, private val txOf: (String) -> TxInfo?, private val holdsGenesis: (String) -> Boolean,
              /** The payer's sanctions screen: true listed, false clear, null could not run (refused: fail closed, 9 Oct 2026). */
              private val screen: (String) -> Boolean? = { false }) {

    data class Pass(val key: String, val wallet: String, val requestsLeft: Long, val endsAt: Long)
    class Refused(val status: Int, message: String) : Exception(message)

    fun message(txSignature: String) = "Undersigned API key for $txSignature"

    /**
     * Checkout without message signing: the server hands out a random [secret] and the payment's memo carries only
     * `UNS:<commitment>`. Whoever holds the secret redeems; a copied transaction shows the commitment, never the secret.
     * Stateless: nothing is stored until redemption. (Seeker wallets pay a Solana Pay URL easily; signing a text is harder.)
     */
    data class Checkout(val secret: String, val memo: String)
    fun checkout(): Checkout { val s = Base58.encode(ByteArray(24).also { SecureRandom().nextBytes(it) }); return Checkout(s, "${Config.PAYMENT_MEMO}:${commitment(s)}") }

    /** Verifies the payment and the payer's signature, then creates the wallet's pass or tops it up. */
    /** Redeems with EITHER the paying wallet's [messageSignature] OR the checkout [secret] whose commitment is in the memo. */
    fun redeem(txSignature: String, messageSignature: String?, now: Long = System.currentTimeMillis() / 1000, secret: String? = null): Pass {
        if (!Regex("^[1-9A-HJ-NP-Za-km-z]{86,88}$").matches(txSignature)) throw Refused(400, "signature must be a base58 transaction signature")
        val already = db.query("SELECT key FROM redeemed WHERE sig = ?", txSignature) { it.getString(1) }.firstOrNull()
        val tx = txOf(txSignature) ?: throw Refused(404, "transaction not found yet; try again once it is confirmed")
        if (!tx.ok) throw Refused(402, "the transaction failed on-chain, so nothing was paid")
        if (Config.API_REFERENCE !in tx.accountKeys) throw Refused(402, "the payment does not carry the API reference account ${Config.API_REFERENCE}")
        // A payment that also names the registry is a Pro licence or a listing fee to the app's reader; one payment buys one thing.
        if (Config.REGISTRY in tx.accountKeys) throw Refused(402, "the payment also names the registry, so it is read as a Pro licence or listing fee, not an API pass")
        val memos = tx.memos.map { it.trim() }
        if (memos.none { it == Config.PAYMENT_MEMO || it.startsWith(Config.PAYMENT_MEMO + ":") }) throw Refused(402, "the payment does not carry the memo ${Config.PAYMENT_MEMO}")
        when {
            !secret.isNullOrBlank() -> if ("${Config.PAYMENT_MEMO}:${commitment(secret.trim())}" !in memos) throw Refused(403, "the secret does not match this payment's memo")
            !messageSignature.isNullOrBlank() -> if (!verifySignature(tx.feePayer, message(txSignature), messageSignature)) throw Refused(403, "the message signature is not the paying wallet's (sign \"${message(txSignature)}\" with ${tx.feePayer})")
            else -> throw Refused(400, "send the checkout secret, or the paying wallet's signature of \"${message(txSignature)}\"")
        }
        when (screen(tx.feePayer)) {
            true -> throw Refused(403, "this wallet address appears on a sanctions list; no pass can be issued")
            null -> throw Refused(503, "the sanctions check could not be completed just now; redeem again in a minute")
            false -> {}
        }
        // Already redeemed: the same proof gets the SAME key back (a lost key is recoverable by the payer), never a second pass.
        if (already != null) return pass(already) ?: throw Refused(409, "this payment has already been redeemed")
        val skr = tx.tokensIn[Config.SKR_MINT] ?: 0L
        val pass = when {
            tx.lamportsIn >= Config.passFloorLamports -> "SOL" to tx.lamportsIn
            skr >= Config.passFloorSkrRaw -> "SKR" to skr
            skr >= Config.passFloorSkrSgtRaw && holdsGenesis(tx.feePayer) -> "SKR" to skr
            else -> null
        }
        // Below the pass price: pay per call, at the per-call rate, from the minimum top-up up.
        if (pass == null) {
            val calls = when {
                tx.lamportsIn >= Config.lamports(Config.CREDITS_MIN_SOL) -> "SOL" to tx.lamportsIn / Config.perCallLamports
                skr >= Config.skrRaw(Config.CREDITS_MIN_SKR) -> "SKR" to skr / Config.perCallSkrRaw
                else -> throw Refused(402, "the payment credited the treasury ${tx.lamportsIn} lamports and $skr raw SKR, below the minimum top-up (${Config.CREDITS_MIN_SOL} SOL or ${Config.CREDITS_MIN_SKR} SKR)")
            }
            return db.tx {
                val cur = db.query("SELECT key, wallet, requests_left, ends_at FROM passes WHERE wallet = ? AND kind = 'credits' ORDER BY ends_at DESC LIMIT 1", tx.feePayer) { Pass(it.getString(1), it.getString(2), it.getLong(3), it.getLong(4)) }.firstOrNull()
                val p = if (cur != null) cur.copy(requestsLeft = maxOf(0, cur.requestsLeft) + calls.second, endsAt = now + Config.CREDITS_DAYS * 86_400).also {
                    db.exec("UPDATE passes SET requests_left = ?, ends_at = ? WHERE key = ?", it.requestsLeft, it.endsAt, it.key) }
                else Pass(newKey(), tx.feePayer, calls.second, now + Config.CREDITS_DAYS * 86_400).also {
                    db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created, kind) VALUES (?, ?, ?, ?, ?, 'credits')", it.key, it.wallet, it.requestsLeft, it.endsAt, now) }
                try { db.exec("INSERT INTO redeemed (sig, key, wallet, rail, amount, at) VALUES (?, ?, ?, ?, ?, ?)", txSignature, p.key, tx.feePayer, calls.first, if (calls.first == "SOL") tx.lamportsIn else skr, now) }
                catch (e: java.sql.SQLException) { throw Refused(409, "this payment has already been redeemed") }
                p
            }
        }
        val (rail, amount) = pass
        return db.tx {
            val current = passOf(tx.feePayer, now)
            val pass = if (current != null) {
                val p = current.copy(requestsLeft = current.requestsLeft + Config.PASS_REQUESTS, endsAt = maxOf(now, current.endsAt) + Config.PASS_DAYS * 86_400)
                db.exec("UPDATE passes SET requests_left = ?, ends_at = ? WHERE key = ?", p.requestsLeft, p.endsAt, p.key); p
            } else {
                val p = Pass(newKey(), tx.feePayer, Config.PASS_REQUESTS, now + Config.PASS_DAYS * 86_400)
                db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES (?, ?, ?, ?, ?)", p.key, p.wallet, p.requestsLeft, p.endsAt, now); p
            }
            // Two redemptions racing on one signature: the primary key lets exactly one through; the other is a replay.
            try { db.exec("INSERT INTO redeemed (sig, key, wallet, rail, amount, at) VALUES (?, ?, ?, ?, ?, ?)", txSignature, pass.key, tx.feePayer, rail, amount, now) }
            catch (e: java.sql.SQLException) { throw Refused(409, "this payment has already been redeemed") }
            pass
        }
    }

    /** The wallet's live pass (requests left, not ended), if any. */
    fun passOf(wallet: String, now: Long = System.currentTimeMillis() / 1000): Pass? =
        db.query("SELECT key, wallet, requests_left, ends_at FROM passes WHERE wallet = ? AND kind = 'pass' AND ends_at > ? AND requests_left > 0 ORDER BY ends_at DESC LIMIT 1", wallet, now) { Pass(it.getString(1), it.getString(2), it.getLong(3), it.getLong(4)) }.firstOrNull()

    fun pass(key: String): Pass? = db.query("SELECT key, wallet, requests_left, ends_at FROM passes WHERE key = ?", key) { Pass(it.getString(1), it.getString(2), it.getLong(3), it.getLong(4)) }.firstOrNull()

    /** "pass" or "credits"; null when the key is unknown. */
    fun kind(key: String): String? = db.query("SELECT kind FROM passes WHERE key = ?", key) { it.getString(1) }.firstOrNull()

    /** Spends one request. False when the key is unknown, ended or used up (the caller answers 402). */
    fun consume(key: String, now: Long = System.currentTimeMillis() / 1000): Boolean =
        db.exec("UPDATE passes SET requests_left = requests_left - 1 WHERE key = ? AND requests_left > 0 AND ends_at > ?", key, now) == 1

    private fun newKey(): String = "us_" + Base58.encode(ByteArray(24).also { SecureRandom().nextBytes(it) })

    companion object {
        /** First 16 bytes of SHA-256("undersigned-pass:" + secret), hex: short enough for a memo, far too wide to guess. */
        fun commitment(secret: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(("undersigned-pass:" + secret).toByteArray()).take(16).joinToString("") { "%02x".format(it) }

        private val X509_ED25519 = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        /**
         * Ed25519 check of [message] by [wallet]. Accepts what wallets produce: the raw UTF-8 message (Phantom, Solflare,
         * Seed Vault "sign message") or the Solana off-chain message v0 envelope (`solana sign-offchain-message`). The
         * signature may be base58, base64 or hex.
         */
        fun verifySignature(wallet: String, message: String, signature: String): Boolean {
            val pub = runCatching { Base58.decode(wallet) }.getOrNull()?.takeIf { it.size == 32 } ?: return false
            val sig = decode64Bytes(signature.trim()) ?: return false
            val key = runCatching { KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(X509_ED25519 + pub)) }.getOrNull() ?: return false
            val raw = message.toByteArray(Charsets.UTF_8)
            return listOf(raw, offchainV0(raw)).any { m -> runCatching { Signature.getInstance("Ed25519").run { initVerify(key); update(m); verify(sig) } }.getOrDefault(false) }
        }

        /** "\xffsolana offchain" · version 0 · format (0 = restricted ASCII, 1 = UTF-8) · u16 LE length · message. */
        fun offchainV0(message: ByteArray): ByteArray {
            val ascii = message.all { it in 0x20..0x7e }
            val header = byteArrayOf(0xff.toByte()) + "solana offchain".toByteArray() + byteArrayOf(0, if (ascii) 0 else 1, (message.size and 0xff).toByte(), (message.size shr 8 and 0xff).toByte())
            return header + message
        }

        private fun decode64Bytes(s: String): ByteArray? {
            runCatching { Base58.decode(s) }.getOrNull()?.takeIf { it.size == 64 }?.let { return it }
            runCatching { Base64.getDecoder().decode(s) }.getOrNull()?.takeIf { it.size == 64 }?.let { return it }
            if (s.length == 128 && s.all { it in "0123456789abcdefABCDEF" }) return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return null
        }
    }
}

/** Per-caller request budgets: a minute window for everyone, a day window for the free tier. In memory; a restart forgives. */
class RateLimiter(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val minute = ConcurrentHashMap<String, Pair<Long, Int>>()
    private val day = ConcurrentHashMap<String, Pair<Long, Int>>()
    private val ops = java.util.concurrent.atomic.AtomicLong()

    /** Windows that have passed are dropped, so a stream of new callers cannot grow the tables without bound. */
    fun sweep(now: Long = clock()) { minute.entries.removeIf { now - it.value.first >= 60_000 }; day.entries.removeIf { now - it.value.first >= 86_400_000 } }
    val size: Int get() = minute.size + day.size

    /** What is left for [caller] in each window, and seconds until each resets, for the X-RateLimit-* headers. */
    fun remaining(caller: String, perMinute: Int, perDay: Int?): Map<String, Long> {
        val now = clock(); val out = LinkedHashMap<String, Long>()
        val m = minute[caller]?.takeIf { now - it.first < 60_000 }
        out["minute_remaining"] = (perMinute - (m?.second ?: 0)).coerceAtLeast(0).toLong()
        out["minute_reset"] = m?.let { (60_000 - (now - it.first)) / 1000 } ?: 60
        if (perDay != null) {
            val d = day[caller]?.takeIf { now - it.first < 86_400_000 }
            out["day_remaining"] = (perDay - (d?.second ?: 0)).coerceAtLeast(0).toLong()
            out["day_reset"] = d?.let { (86_400_000 - (now - it.first)) / 1000 } ?: 86_400
        }
        return out
    }

    /** Null when allowed, else the window that ran out ("minute" or "day"). */
    fun take(caller: String, perMinute: Int, perDay: Int?): String? {
        val now = clock()
        if (ops.incrementAndGet() % 2000 == 0L) sweep(now)
        val m = minute.compute(caller) { _, v -> if (v == null || now - v.first >= 60_000) now to 1 else v.first to v.second + 1 }!!
        if (m.second > perMinute) return "minute"
        if (perDay != null) {
            val d = day.compute(caller) { _, v -> if (v == null || now - v.first >= 86_400_000) now to 1 else v.first to v.second + 1 }!!
            if (d.second > perDay) return "day"
        }
        return null
    }
}
