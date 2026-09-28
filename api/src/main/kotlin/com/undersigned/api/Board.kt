package com.undersigned.api

import com.undersigned.protocol.Payments
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * What a listed treasury has been paid (port of the app's PaymentBoard; the cache lives in SQLite). A memo listing reads
 * the treasury wallet and its SKR and USDC token accounts, fetching only transactions whose memo starts with the code; a
 * reference listing reads the reference account's own history. Amounts come from balances, never from memos.
 */
class Board(private val db: Db, private val rpc: Rpc) {
    data class Read(val payments: List<Payments.Payment>, val complete: Boolean)

    @Synchronized fun read(treasury: String, code: String, backfillPages: Int = 5): Read {
        val byRef = Payments.isReference(code)
        val key = if (byRef) "$treasury-$code" else treasury
        val addrs = if (byRef) listOf(code) else addressesOf(treasury)
        var failed = false
        for (a in addrs) runCatching { sync(key, a, keepAll = byRef, backfillPages) }.onFailure { failed = true; log("board sync ${a.take(6)}: ${it.message?.take(80)}") }
        val wanted = { memo: String -> byRef || Payments.memoMatches(memo, code) }
        val unread = db.query("SELECT sig, memo FROM pay_rows WHERE addr_key = ? AND fetched = 0 ORDER BY time DESC", key) { it.getString(1) to (it.getString(2) ?: "") }.filter { wanted(it.second) }
        var missed = 0
        for ((sig, _) in unread) {
            var tx: TxInfo? = null
            for (attempt in 0 until 4) { tx = runCatching { rpc.tx(sig, treasury) }.getOrNull(); if (tx != null) break; Thread.sleep(600L * (attempt + 1)) }
            if (tx == null) { missed++; continue }
            val paid = tx.ok && (tx.lamportsIn > 0 || tx.tokensIn.isNotEmpty())
            db.exec("UPDATE pay_rows SET fetched = 1, payer = ?, lamports = ?, tokens = ? WHERE addr_key = ? AND sig = ?",
                if (paid) tx.feePayer else null, if (paid) tx.lamportsIn else 0L, if (paid) JSONObject(tx.tokensIn as Map<*, *>).toString() else null, key, sig)
        }
        val rails = setOf(Config.SKR_MINT, Config.USDC_MINT)
        val payments = db.query("SELECT sig, time, memo, payer, lamports, tokens FROM pay_rows WHERE addr_key = ? AND fetched = 1 AND payer IS NOT NULL", key) { rs ->
            if (!wanted(rs.getString(3) ?: "")) return@query null
            val t = rs.getString(6)?.let { JSONObject(it) } ?: JSONObject()
            Payments.Payment(rs.getString(1), rs.getString(4), rs.getLong(2), rs.getLong(5), t.keys().asSequence().filter { it in rails }.associateWith { t.getLong(it) })
        }.filterNotNull()
        val exhausted = addrs.all { db.get("pay_exhausted:$key:$it") == "1" }
        return Read(payments, !failed && missed == 0 && exhausted)
    }

    private fun sync(key: String, addr: String, keepAll: Boolean, backfillPages: Int) {
        val newestKey = "pay_newest:$key:$addr"; val oldestKey = "pay_oldest:$key:$addr"; val exKey = "pay_exhausted:$key:$addr"
        fun add(rows: List<SignatureRow>) = db.tx { rows.filter { !it.err && (keepAll || !it.memo.isNullOrBlank()) }.forEach {
            db.exec("INSERT OR IGNORE INTO pay_rows (addr_key, sig, time, memo, fetched) VALUES (?, ?, ?, ?, 0)", key, it.signature, it.blockTime, it.memo ?: "") } }
        val newest = db.get(newestKey)
        var before: String? = null; var first: String? = null
        while (true) {
            val page = rpc.signatures(addr, before = before, until = newest)
            if (page.isEmpty()) { if (newest == null && before == null) db.put(exKey, "1"); break }
            if (first == null) first = page.first().signature
            add(page)
            if (newest == null) { db.put(oldestKey, page.last().signature); if (page.size < 1000) db.put(exKey, "1") }
            if (page.size < 1000 || newest == null) break
            before = page.last().signature
        }
        first?.let { db.put(newestKey, it) }
        var pages = 0
        while (db.get(exKey) != "1" && pages++ < backfillPages) {
            val from = db.get(oldestKey) ?: break
            val page = rpc.signatures(addr, before = from)
            add(page)
            if (page.isNotEmpty()) db.put(oldestKey, page.last().signature)
            if (page.size < 1000) db.put(exKey, "1")
        }
    }

    companion object {
        /** The treasury wallet, then its SKR and USDC associated token accounts. */
        fun addressesOf(treasury: String): List<String> {
            val owner = Base58.decode(treasury)
            val tokenProgram = Base58.decode("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"); val ataProgram = Base58.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
            return listOf(treasury) + listOf(Config.SKR_MINT, Config.USDC_MINT).map { Base58.encode(Pda.find(listOf(owner, tokenProgram, Base58.decode(it)), ataProgram)) }
        }
    }
}

/**
 * Daily USD closes, last 365 days (CoinGecko, keyless; `days=max` is refused without a key): SOL as "solana", SKR as
 * "seeker". Cached 12 hours. A payment is valued on the day it was paid; USDC counts at 1.
 */
object Prices {
    const val SOL = "So11111111111111111111111111111111111111112"
    @Volatile private var cache: Pair<Long, Map<String, Map<String, Double>>> = 0L to emptyMap()

    fun history(): Map<String, Map<String, Double>> {
        val (at, h) = cache
        if (at > 0 && System.currentTimeMillis() - at < 12 * 3600_000L) return h
        val out = HashMap<String, Map<String, Double>>()
        for ((mint, id) in mapOf(SOL to "solana", Config.SKR_MINT to "seeker")) runCatching {
            val c = URL("https://api.coingecko.com/api/v3/coins/$id/market_chart?vs_currency=usd&days=365&interval=daily").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            val arr = try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("prices") } finally { c.disconnect() }
            out[mint] = (0 until arr.length()).associate { i -> val p = arr.getJSONArray(i); java.time.Instant.ofEpochMilli(p.getLong(0)).toString().take(10) to p.getDouble(1) }
        }
        // A partial answer is kept only 15 minutes, so a missing price is retried soon.
        cache = (if (out.size == 2) System.currentTimeMillis() else System.currentTimeMillis() - 12 * 3600_000L + 15 * 60_000L) to out
        return out
    }

    fun usdWhenPaid(p: Payments.Payment, h: Map<String, Map<String, Double>>): Double? {
        val day = java.time.Instant.ofEpochSecond(p.time).toString().take(10)
        var total = if (p.lamports > 0) (h[SOL]?.get(day) ?: return null) * p.lamports / 1e9 else 0.0
        for ((mint, raw) in p.tokens) total += when (mint) {
            Config.USDC_MINT -> raw / 1e6
            Config.SKR_MINT -> (h[mint]?.get(day) ?: return null) * raw / 1e6
            else -> return null
        }
        return total
    }
}
