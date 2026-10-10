package com.undersigned.api

import com.undersigned.protocol.Registry
import com.undersigned.protocol.ReviewMemo
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Pays the network fee for quick reviews (owner, 10 Oct 2026: reviews "without" MWA signing and the Seed Vault, on-chain
 * with an app key). The phone builds a transaction whose fee payer is [Registry.QUICK_RELAYER], signs it with its own app
 * key, and sends it here; this checks it is nothing but a quick review (or the pieces of its long text), adds the fee
 * payer's signature and submits it. The relayer can pay fees and nothing else: it signs no memo and moves no lamports.
 *
 * What it accepts, exactly: two signatures (relayer, app key); only the Memo program (with the app key as its one
 * account) and one System transfer of 0 lamports from the relayer to itself carrying the registry reference; memos that
 * decode to a review with its quick marker (plus the review's own facts, follow-up link and long-text head) or only
 * long-text pieces; the app key's signature valid. Limits per app key, per IP and per day keep the fee budget bounded.
 */
class Relayer(private val db: Db, private val rpc: Rpc, keyFile: File, val address: String = Registry.QUICK_RELAYER) {
    private val priv = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))

    init { db.exec("CREATE TABLE IF NOT EXISTS relay_log (sig TEXT PRIMARY KEY, day TEXT NOT NULL, app_key TEXT NOT NULL, ip TEXT NOT NULL, reviews INTEGER NOT NULL, at INTEGER NOT NULL)") }

    class Refused(val status: Int, message: String) : Exception(message)

    companion object {
        const val PER_KEY_REVIEWS_DAY = 3
        const val PER_KEY_TX_DAY = 3 * (1 + com.undersigned.protocol.LongText.MAX_PARTS)
        const val PER_IP_TX_DAY = 60
        const val PER_IP_TX_HOUR = 10
        const val GLOBAL_TX_DAY = 300
        /** One IPv6 /64 is one network (an address there is free to change); IPv4 stays per address. Audit, 10 Oct 2026. */
        fun network(ip: String): String = if (':' in ip) ip.split(':').let { p -> (p.take(4) + List(4 - minOf(4, p.size)) { "0" }).joinToString(":") + "::/64" } else ip
        const val FEE_LAMPORTS = 10_000L   // two signatures (relayer + app key) at 5,000 each; measured on mainnet 9 Oct 2026
        private val ALLOWED = setOf(ReviewMemo.Review::class, ReviewMemo.Quick::class, ReviewMemo.Use::class, ReviewMemo.Time::class, ReviewMemo.Activity::class,
            ReviewMemo.LongTextHead::class, ReviewMemo.TextPart::class, ReviewMemo.FollowUp::class)

        data class Ix(val program: Int, val accounts: List<Int>, val data: ByteArray)
        data class Parsed(val signatures: List<ByteArray>, val message: ByteArray, val header: Triple<Int, Int, Int>, val keys: List<String>, val ixs: List<Ix>)

        private fun compact(b: ByteArray, at: IntArray): Int {
            var v = 0; var shift = 0
            while (true) { val x = b[at[0]++].toInt() and 0xff; v = v or ((x and 0x7f) shl shift); if (x and 0x80 == 0) return v; shift += 7; require(shift <= 14) { "bad length" } }
        }

        /** A legacy (unversioned) transaction: signatures, then the message. Throws on anything malformed. */
        fun parse(tx: ByteArray): Parsed {
            val at = intArrayOf(0)
            val n = compact(tx, at); require(n in 1..4) { "signatures" }
            val sigs = (0 until n).map { tx.copyOfRange(at[0], at[0] + 64).also { at[0] += 64 } }
            val msgStart = at[0]
            require(tx[at[0]].toInt() and 0x80 == 0) { "versioned transactions are not accepted" }
            val header = Triple(tx[at[0]].toInt() and 0xff, tx[at[0] + 1].toInt() and 0xff, tx[at[0] + 2].toInt() and 0xff); at[0] += 3
            val k = compact(tx, at); require(k in 1..16) { "accounts" }
            val keys = (0 until k).map { Base58.encode(tx.copyOfRange(at[0], at[0] + 32)).also { at[0] += 32 } }
            at[0] += 32                                   // recent blockhash
            val m = compact(tx, at); require(m in 1..16) { "instructions" }
            val ixs = (0 until m).map {
                val p = tx[at[0]++].toInt() and 0xff
                val a = compact(tx, at); val accts = (0 until a).map { tx[at[0]++].toInt() and 0xff }
                val d = compact(tx, at); val data = tx.copyOfRange(at[0], at[0] + d); at[0] += d
                Ix(p, accts, data)
            }
            require(at[0] == tx.size) { "trailing bytes" }
            return Parsed(sigs, tx.copyOfRange(msgStart, tx.size), header, keys, ixs)
        }
    }

    private fun day() = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
    private fun count(sql: String, vararg a: Any?) = db.query(sql, *a) { it.getInt(1) }.firstOrNull() ?: 0

    /** What a phone needs to build a quick review: the fee payer, a recent blockhash and the limits. */
    fun info(): JSONObject = JSONObject().put("fee_payer", address).put("blockhash", runCatching { rpc.latestBlockhash() }.getOrNull() ?: JSONObject.NULL)
        .put("registry", Config.REGISTRY).put("memo_program", Config.MEMO_PROGRAM)
        .put("limits", JSONObject().put("reviews_per_key_per_day", PER_KEY_REVIEWS_DAY).put("transactions_per_day", GLOBAL_TX_DAY))
        .put("paused", count("SELECT count(*) FROM relay_log WHERE day = ?", day()) >= GLOBAL_TX_DAY)

    /** Checks [txBase64] (see the class header), co-signs it as fee payer and submits it. Returns the signature. */
    fun relay(txBase64: String, ip: String): String {
        val raw = runCatching { Base64.getDecoder().decode(txBase64.trim()) }.getOrNull()?.takeIf { it.size in 100..1232 } ?: throw Refused(400, "send {\"tx\": base64 of a signed legacy transaction}")
        val p = runCatching { parse(raw) }.getOrElse { throw Refused(400, "unreadable transaction: ${it.message}") }
        if (p.signatures.size != 2 || p.header.first != 2) throw Refused(400, "a quick review has exactly two signers: the relayer and the app key")
        if (p.keys[0] != address) throw Refused(400, "the fee payer must be $address")
        val appKey = p.keys[1]; if (appKey == address) throw Refused(400, "the app key cannot be the relayer")
        val memoIdx = p.keys.indexOf(Config.MEMO_PROGRAM); val sysIdx = p.keys.indexOf("11111111111111111111111111111111"); val regIdx = p.keys.indexOf(Config.REGISTRY)
        val memos = ArrayList<ReviewMemo>(); var carrier = 0
        for (ix in p.ixs) {
            when (ix.program) {
                memoIdx -> {
                    if (ix.accounts != listOf(1)) throw Refused(400, "a memo must name only the app key")
                    val m = ReviewMemo.decode(String(ix.data, Charsets.UTF_8)) ?: throw Refused(400, "not an Undersigned record")
                    if (m::class !in ALLOWED) throw Refused(400, "only quick reviews are relayed")
                    memos += m
                }
                sysIdx -> {
                    // transfer (index 2) of 0 lamports, relayer -> relayer, with the registry reference: what indexes the record
                    val d = ix.data
                    val isTransfer = d.size == 12 && d[0].toInt() == 2 && d[1].toInt() == 0 && d[2].toInt() == 0 && d[3].toInt() == 0 && (4 until 12).all { d[it].toInt() == 0 }
                    if (!isTransfer || ix.accounts != listOf(0, 0, regIdx) || regIdx < 0) throw Refused(400, "the only transfer allowed is 0 lamports to the relayer itself, with the registry reference")
                    carrier++
                }
                else -> throw Refused(400, "only the Memo and System programs are allowed")
            }
        }
        if (carrier != 1 || memos.isEmpty()) throw Refused(400, "one carrier transfer and at least one record")
        val reviews = memos.count { it is ReviewMemo.Review }
        if (reviews > 1) throw Refused(400, "one review per transaction")
        if (reviews == 1 && ReviewMemo.Quick !in memos) throw Refused(400, "a relayed review must carry the quick marker")
        if (reviews == 0 && memos.any { it !is ReviewMemo.TextPart }) throw Refused(400, "without a review, only long-text pieces")
        val pub = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + Base58.decode(appKey)))
        if (!Signature.getInstance("Ed25519").run { initVerify(pub); update(p.message); verify(p.signatures[1]) }) throw Refused(400, "the app key's signature does not match")
        // Limits: per app key, per network (hour and day), per day for everyone. The counts and the reservation are one
        // transaction, so parallel requests cannot overshoot; a send that fails releases its reservation.
        val today = day(); val net = network(ip); val nowS = System.currentTimeMillis() / 1000
        val reservation = "pending:" + Base64.getEncoder().encodeToString(p.signatures[1]).take(40)
        db.tx {
            if (count("SELECT count(*) FROM relay_log WHERE day = ?", today) >= GLOBAL_TX_DAY) throw Refused(429, "quick reviews are paused for today; they resume at 00:00 UTC, or sign with your wallet")
            if (count("SELECT count(*) FROM relay_log WHERE day = ? AND ip = ?", today, net) >= PER_IP_TX_DAY) throw Refused(429, "too many quick reviews from this network today")
            if (count("SELECT count(*) FROM relay_log WHERE ip = ? AND at > ?", net, nowS - 3600) >= PER_IP_TX_HOUR) throw Refused(429, "too many quick reviews from this network this hour; try again later or sign with your wallet")
            if (count("SELECT count(*) FROM relay_log WHERE day = ? AND app_key = ?", today, appKey) >= PER_KEY_TX_DAY ||
                (reviews == 1 && count("SELECT coalesce(sum(reviews), 0) FROM relay_log WHERE day = ? AND app_key = ?", today, appKey) >= PER_KEY_REVIEWS_DAY))
                throw Refused(429, "$PER_KEY_REVIEWS_DAY quick reviews a day from one phone")
            db.exec("INSERT OR IGNORE INTO relay_log (sig, day, app_key, ip, reviews, at) VALUES (?, ?, ?, ?, ?, ?)", reservation, today, appKey, net, reviews, nowS)
        }
        val sig = Signature.getInstance("Ed25519").run { initSign(priv); update(p.message); sign() }
        val out = ByteArrayOutputStream().apply { write(2); write(sig); write(p.signatures[1]); write(p.message) }.toByteArray()
        val txSig = runCatching { rpc.sendTransaction(Base64.getEncoder().encodeToString(out)) }.getOrElse {
            db.exec("DELETE FROM relay_log WHERE sig = ?", reservation); throw Refused(502, "the network did not take it: ${it.message?.take(120)}") }
        db.exec("UPDATE relay_log SET sig = ? WHERE sig = ?", txSig, reservation)
        return txSig
    }

    /** Fees the relayer paid on [day] (lamports): one signature's fee per relayed transaction. */
    fun feesOn(day: String): Long = count("SELECT count(*) FROM relay_log WHERE day = ?", day) * FEE_LAMPORTS
}
