package com.undersigned.app.ledger

import android.content.Context
import android.util.Log
import com.undersigned.app.AppConfig
import com.undersigned.app.solana.SolanaRpcClient
import com.undersigned.app.solana.TxBuilder
import com.undersigned.app.util.Base58
import com.undersigned.protocol.Payments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads what a treasury has been paid, for the publisher-confirmed payment board. Three addresses per treasury: the
 * wallet (SOL) and its SKR and USDC token accounts. A token transfer names the token ACCOUNT, not the wallet, so a
 * wallet-only read misses every SKR payment (measured on the fleet treasury 27 Sep 2026: 0 of its SKR payments appear
 * under the wallet). The treasury's other token accounts are almost all 1-unit airdrops (211 of 213) and are not read.
 *
 * Signature lists are cached per treasury and only the tail is re-read; a transaction is fetched only when its memo
 * starts with a code somebody asked about, and then never again. Everything is public chain data.
 */
class PaymentBoard(private val dir: File, private val rpc: SolanaRpcClient = SolanaRpcClient()) {
    constructor(context: Context) : this(File(context.filesDir, "payments"))
    init { dir.mkdirs() }
    private val lock = Mutex()

    /** One signature row, and the payment it made once fetched ([fetched] false = not looked at yet; [payment] null = paid nothing). */
    private data class Row(val sig: String, val time: Long, val memo: String, val fetched: Boolean, val payment: Payments.Payment?)
    private data class Cache(val rows: MutableMap<String, Row>, val newest: MutableMap<String, String>, val oldest: MutableMap<String, String>, val exhausted: MutableSet<String>)

    /** [complete] false: the treasury has more history than one read covers; a later read continues backwards. */
    data class Read(val payments: List<Payments.Payment>, val complete: Boolean, val readAt: Long)

    suspend fun read(treasury: String, code: String): Read = withContext(Dispatchers.IO) {
        lock.withLock {
            // A reference listing reads the reference account's own history (every transaction that names it, memo or
            // not) into its own cache; a memo listing reads the treasury's three addresses.
            val byRef = Payments.isReference(code)
            val key = if (byRef) "$treasury-$code" else treasury
            val c = load(key)
            val addrs = if (byRef) listOf(code) else addressesOf(treasury)
            for (addr in addrs) runCatching { sync(c, addr, keepAll = byRef) }.onFailure { Log.w(TAG, "sync ${addr.take(6)} stopped: ${it.message?.take(80)}") }
            val wanted = { r: Row -> byRef || Payments.memoMatches(r.memo, code) }
            // Fetch the rows this listing needs, once each.
            var n = 0
            for (r in c.rows.values.filter { !it.fetched && wanted(it) }.sortedByDescending { it.time }) {
                // Retried with backoff: a shared RPC key refuses bursts, and a skipped read used to vanish silently — a live
                // run counted 0 of ART's 3 payments and still said "complete" (28 Sep 2026).
                var res = rpc.getPaymentTx(r.sig, treasury)
                var attempt = 1
                while (res.isFailure && attempt < 4) { delay(600L * attempt); res = rpc.getPaymentTx(r.sig, treasury); attempt++ }
                if (res.isFailure) { Log.w(TAG, "tx ${r.sig.take(8)}: ${res.exceptionOrNull()?.message?.take(60)}"); continue }
                c.rows[r.sig] = r.copy(fetched = true, payment = res.getOrNull())
                if (++n % 5 == 0) delay(300)
            }
            save(key, c)
            // Only the rails the board reads: SOL, SKR and USDC. Anything else a transaction happened to credit is left out.
            val rails = setOf(AppConfig.Solana.SKR_MINT, USDC_MINT)
            val payments = c.rows.values.filter { it.fetched && it.payment != null && wanted(it) }
                .mapNotNull { it.payment?.let { p -> p.copy(tokens = p.tokens.filterKeys { k -> k in rails }) } }
            // Complete only when the history was read to its start AND every payment it holds was read.
            val unread = c.rows.values.count { !it.fetched && wanted(it) }
            Read(payments, addrs.all { it in c.exhausted } && unread == 0, System.currentTimeMillis())
        }
    }

    private suspend fun sync(c: Cache, addr: String, keepAll: Boolean) {
        // Tail: everything newer than the newest seen.
        var before: String? = null; var first: String? = null
        while (true) {
            val page = rpc.getSignaturesForAddress(addr, before = before, until = c.newest[addr]).getOrThrow()
            if (page.isEmpty()) break
            if (first == null) first = page.first().signature
            page.forEach { add(c, it, keepAll) }
            if (c.newest[addr] == null) { c.oldest[addr] = page.last().signature; if (page.size < AppConfig.Ledger.PAGE) c.exhausted.add(addr) }
            if (page.size < AppConfig.Ledger.PAGE || c.newest[addr] == null) break
            before = page.last().signature
        }
        if (first != null) c.newest[addr] = first
        // Backfill: older history, a bounded number of pages per read.
        var pages = 0
        while (addr !in c.exhausted && pages < BACKFILL_PAGES) {
            val from = c.oldest[addr] ?: break
            val page = rpc.getSignaturesForAddress(addr, before = from).getOrThrow()
            page.forEach { add(c, it, keepAll) }
            if (page.isNotEmpty()) c.oldest[addr] = page.last().signature
            if (page.size < AppConfig.Ledger.PAGE) c.exhausted.add(addr)
            pages++
        }
    }

    /** [keepAll]: a reference account's rows are all candidates; a treasury's only matter when they carry a memo. */
    private fun add(c: Cache, s: com.undersigned.app.solana.SignatureRow, keepAll: Boolean) {
        if (s.err || c.rows.containsKey(s.signature) || (!keepAll && s.memo.isNullOrBlank())) return
        c.rows[s.signature] = Row(s.signature, s.blockTime, s.memo ?: "", false, null)
    }

    private fun file(key: String) = File(dir, "$key.json")

    private fun load(treasury: String): Cache = runCatching {
        val o = JSONObject(file(treasury).readText())
        val rows = HashMap<String, Row>()
        val arr = o.getJSONArray("rows")
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val p = r.optJSONObject("p")?.let { p ->
                val t = p.optJSONObject("tok") ?: JSONObject()
                Payments.Payment(r.getString("s"), p.getString("payer"), r.getLong("t"), p.getLong("lam"), t.keys().asSequence().associateWith { t.getLong(it) })
            }
            rows[r.getString("s")] = Row(r.getString("s"), r.getLong("t"), r.getString("m"), r.getBoolean("f"), p)
        }
        fun map(k: String) = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) }.toMutableMap() } ?: HashMap()
        val ex = o.optJSONArray("ex")?.let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() } ?: HashSet()
        Cache(rows, map("newest"), map("oldest"), ex)
    }.getOrElse { Cache(HashMap(), HashMap(), HashMap(), HashSet()) }

    private fun save(treasury: String, c: Cache) {
        val arr = JSONArray()
        c.rows.values.forEach { r -> arr.put(JSONObject().put("s", r.sig).put("t", r.time).put("m", r.memo).put("f", r.fetched).apply {
            r.payment?.let { p -> put("p", JSONObject().put("payer", p.payer).put("lam", p.lamports).put("tok", JSONObject(p.tokens as Map<*, *>))) } }) }
        val o = JSONObject().put("rows", arr).put("newest", JSONObject(c.newest as Map<*, *>)).put("oldest", JSONObject(c.oldest as Map<*, *>)).put("ex", JSONArray(c.exhausted.toList()))
        runCatching { file(treasury).writeText(o.toString()) }
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    companion object {
        private const val TAG = "PaymentBoard"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val SOL_MINT = "So11111111111111111111111111111111111111112"
        private const val BACKFILL_PAGES = 5
        /** The treasury wallet, then its SKR and USDC associated token accounts. */
        fun addressesOf(treasury: String): List<String> {
            val owner = Base58.decode(treasury)
            return listOf(treasury) + listOf(AppConfig.Solana.SKR_MINT, USDC_MINT).map { Base58.encode(TxBuilder.associatedTokenAddress(owner, Base58.decode(it))) }
        }
    }
}

/**
 * Today's USD price per whole token, keyless (Jupiter lite API, measured 27 Sep 2026: `{mint: {usdPrice, decimals}}`).
 * Spot only: board totals are "at today's prices" and say so. Cached 30 minutes.
 */
object Prices {
    data class Quote(val usd: Double, val decimals: Int)
    @Volatile private var cache: Pair<Long, Map<String, Quote>> = 0L to emptyMap()

    suspend fun quotes(mints: Collection<String>): Map<String, Quote> = withContext(Dispatchers.IO) {
        val want = (mints + PaymentBoard.SOL_MINT).toSet()
        val (at, have) = cache
        if (System.currentTimeMillis() - at < 30 * 60_000L && want.all { it in have }) return@withContext have
        runCatching {
            val c = URL("https://lite-api.jup.ag/price/v3?ids=" + want.joinToString(",")).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() }); c.disconnect()
            val m = o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { q -> q.optDouble("usdPrice").takeIf { !it.isNaN() }?.let { k to Quote(it, q.optInt("decimals", 0)) } } }.toMap()
            cache = System.currentTimeMillis() to (have + m); have + m
        }.getOrElse { have }
    }

    /**
     * Daily USD closes for the last 365 days (CoinGecko, keyless; `days=max` is refused without a key, measured 21 Sep
     * 2026): SOL as "solana", SKR as "seeker". Day key yyyy-mm-dd (UTC) → price. Cached 12 hours; empty on failure.
     */
    @Volatile private var history: Pair<Long, Map<String, Map<String, Double>>> = 0L to emptyMap()
    suspend fun history(): Map<String, Map<String, Double>> = withContext(Dispatchers.IO) {
        val (at, h) = history
        if (System.currentTimeMillis() - at < 12 * 3600_000L && h.isNotEmpty()) return@withContext h
        val ids = mapOf(PaymentBoard.SOL_MINT to "solana", AppConfig.Solana.SKR_MINT to "seeker")
        val out = HashMap<String, Map<String, Double>>()
        for ((mint, id) in ids) runCatching {
            val c = URL("https://api.coingecko.com/api/v3/coins/$id/market_chart?vs_currency=usd&days=365&interval=daily").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("prices"); c.disconnect()
            out[mint] = (0 until arr.length()).associate { i -> val p = arr.getJSONArray(i); java.time.Instant.ofEpochMilli(p.getLong(0)).toString().take(10) to p.getDouble(1) }
        }
        if (out.size == ids.size) history = System.currentTimeMillis() to out
        out
    }

    /** USD of one payment on the day it was paid; null when that day's price is missing. USDC counts at 1. */
    fun usdWhenPaid(p: Payments.Payment, h: Map<String, Map<String, Double>>): Double? {
        val day = java.time.Instant.ofEpochSecond(p.time).toString().take(10)
        var total = if (p.lamports > 0) (h[PaymentBoard.SOL_MINT]?.get(day) ?: return null) * p.lamports / 1e9 else 0.0
        for ((mint, raw) in p.tokens) total += when (mint) {
            PaymentBoard.USDC_MINT -> raw / 1e6
            AppConfig.Solana.SKR_MINT -> (h[mint]?.get(day) ?: return null) * raw / 1e6
            else -> return null
        }
        return total
    }

    /** USD value of a tally at today's prices; null when a price is missing for anything it holds. */
    fun usd(lamports: Long, tokens: Map<String, Long>, q: Map<String, Quote>): Double? {
        val sol = q[PaymentBoard.SOL_MINT] ?: return null
        var total = lamports / 1e9 * sol.usd
        for ((mint, raw) in tokens) { val p = q[mint] ?: return null; total += raw / Math.pow(10.0, p.decimals.toDouble()) * p.usd }
        return total
    }
}
