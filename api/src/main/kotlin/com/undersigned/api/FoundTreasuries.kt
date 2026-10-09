package com.undersigned.api

import com.undersigned.protocol.Operator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Treasuries FOUND IN APP CODE, not confirmed by the publisher (owner, 9 Oct 2026: "scrape suspected treasury wallets",
 * shown as a separate unconfirmed list). Each store build is read for wallet addresses written into it; an address counts
 * only when every one of these holds:
 *  - it is an ordinary wallet on-chain (System-owned, not a program, mint or token account);
 *  - every app it was found in comes from ONE publisher (an address in several publishers' apps is shared: the Seeker
 *    Genesis Token mint authority sat in three unrelated apps when this was measured, 9 Oct 2026);
 *  - it is not an Undersigned operator wallet, not on [NOT_TREASURIES], and not already a confirmed treasury;
 *  - in the transactions read, at least [MIN_PAYERS] different wallets sent it SOL, SKR or USDC.
 * It is never ranked with confirmed treasuries, is labelled as found in code, and drops out when the publisher confirms a
 * treasury for the app (it then shows on the board) or the operator hides it on a dispute.
 */
class FoundTreasuries(private val db: Db, private val rpc: Rpc, private val store: Store) {
    init {
        db.exec("CREATE TABLE IF NOT EXISTS apk_scan (pkg TEXT PRIMARY KEY, version TEXT NOT NULL, at INTEGER NOT NULL, size INTEGER NOT NULL, wallets TEXT NOT NULL, publisher TEXT, note TEXT)")
        db.exec("CREATE TABLE IF NOT EXISTS found_wallet (wallet TEXT PRIMARY KEY, measured_at INTEGER NOT NULL, read INTEGER NOT NULL, payments INTEGER NOT NULL, payers INTEGER NOT NULL, lamports INTEGER NOT NULL, skr INTEGER NOT NULL, usdc INTEGER NOT NULL, first INTEGER, last INTEGER)")
    }

    data class Scan(val pkg: String, val version: String, val wallets: Set<String>, val publisher: String?)
    data class Measure(val read: Int, val payments: Int, val payers: Int, val lamports: Long, val skr: Long, val usdc: Long, val first: Long?, val last: Long?)

    companion object {
        const val MIN_PAYERS = 5
        /** Builds of any size are read (owner, 9 Oct 2026: "we can still do them"); only free disk is checked, with this to spare. */
        const val DISK_SPARE = 10L * 1024 * 1024 * 1024
        const val CHUNK = 4 * 1024 * 1024
        private const val CARRY = 64   // longer than any key, so a key cut by a chunk edge is read whole in the next window
        const val SIGS_READ = 100
        const val TXS_READ = 40
        const val REMEASURE_MS = 7L * 86_400_000
        const val SYSTEM = "11111111111111111111111111111111"
        /** Addresses apps embed for reasons other than taking payment. */
        val NOT_TREASURIES = setOf(
            "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4",   // Seeker Genesis Token mint authority (apps check holdings)
        )
        private val B58 = Regex("(?<![1-9A-HJ-NP-Za-km-z])[1-9A-HJ-NP-Za-km-z]{32,44}(?![1-9A-HJ-NP-Za-km-z])")
        private val SKIP = listOf(".png", ".webp", ".jpg", ".jpeg", ".gif", ".ogg", ".mp3", ".wav", ".ttf", ".otf", ".mp4")

        /** Every string in the build that decodes to a 32-byte key: the dex files, JS bundles, native libraries and assets. */
        fun addresses(apk: File): Set<String> = ZipFile(apk).use { z ->
            val out = HashSet<String>()
            for (e in z.entries()) {
                val n = e.name.lowercase()
                if (e.isDirectory || n.startsWith("meta-inf/") || SKIP.any { n.endsWith(it) }) continue
                runCatching { z.getInputStream(e).use { scan(it, out) } }
            }
            out
        }

        /**
         * Reads a stream in [CHUNK]s, whatever its size, keeping the last [CARRY] bytes of each window in front of the next.
         * A match touching a window's end is left for the next window, which sees it whole. ISO-8859-1 keeps one char per byte,
         * so the pattern sees ASCII runs inside binary files too. A real key uses many of the 58 letters; "2222…2222"-style
         * placeholders (funded on-chain, seen in fun.cherry's build 9 Oct 2026) do not.
         */
        fun scan(inp: java.io.InputStream, out: MutableSet<String>) {
            val buf = ByteArray(CHUNK); var carry = ByteArray(0)
            while (true) {
                val n = inp.readNBytes(buf, 0, CHUNK)
                val last = n < CHUNK
                val window = String(carry + buf.copyOf(n), Charsets.ISO_8859_1)
                B58.findAll(window).forEach { m ->
                    if ((last || m.range.last < window.length - 1) && m.value.toSet().size >= 12 && Base58.decodePubkey(m.value) != null) out += m.value
                }
                if (last) break
                carry = window.takeLast(CARRY).toByteArray(Charsets.ISO_8859_1)
            }
        }

        /** SKR and USDC are both classic SPL Token mints (SKR's owner read on-chain 9 Oct 2026); the account is derived under it. */
        fun tokenProgramOf(@Suppress("UNUSED_PARAMETER") mint: String): String = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

        fun ata(owner: String, mint: String, tokenProgram: String = tokenProgramOf(mint)): String =
            Base58.encode(Pda.find(listOf(Base58.decode(owner), Base58.decode(tokenProgram), Base58.decode(mint)), Base58.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")))
    }

    // ---- Reading builds -------------------------------------------------------------------------------------------

    /** The next app to read: never read, or its listing moved since. Null when every listed build has been read. */
    fun nextToScan(catalogue: List<CatalogueApp>): CatalogueApp? {
        // A download the store refused or failed (HTTP 403 / 572 seen 9 Oct 2026) is tried again after six hours.
        val retryBefore = System.currentTimeMillis() - 6 * 3_600_000L
        val seen = db.query("SELECT pkg, version FROM apk_scan WHERE NOT (note LIKE 'HTTP %' AND at < ?)", retryBefore) { it.getString(1) to it.getString(2) }.toMap()
        return catalogue.firstOrNull { seen[it.packageName] == null } ?: catalogue.firstOrNull { it.updated.isNotBlank() && seen[it.packageName] != it.updated }
    }

    /** Reads one app's current store build and keeps the ordinary wallets written into it. */
    fun scan(app: CatalogueApp): Scan? {
        val d = store.details(app.packageName)
        val version = app.updated.ifBlank { d?.versionCode?.toString() ?: "?" }
        val uri = d?.apkUri ?: run { record(app.packageName, version, 0, emptySet(), d?.publisherName, "no build"); return null }
        val tmp = File.createTempFile("undersigned-scan-", ".apk")
        try {
            val c = URL(uri).openConnection() as HttpURLConnection
            var size = 0L
            try {
                c.connectTimeout = 10_000; c.readTimeout = 60_000; c.setRequestProperty("User-Agent", "Undersigned-API/1.0")
                if (c.responseCode != 200) { record(app.packageName, version, 0, emptySet(), d.publisherName, "HTTP ${c.responseCode}"); return null }
                if (tmp.usableSpace < maxOf(c.contentLengthLong, 0L) + DISK_SPARE) { record(app.packageName, version, c.contentLengthLong, emptySet(), d.publisherName, "HTTP wait: disk"); return null }
                c.inputStream.use { inp -> tmp.outputStream().use { out -> val buf = ByteArray(1 shl 16)
                    while (true) { val n = inp.read(buf); if (n < 0) break; size += n
                        if ((size and 0xFFFFFFF) < n && tmp.usableSpace < DISK_SPARE) { record(app.packageName, version, size, emptySet(), d.publisherName, "HTTP wait: disk"); return null }
                        out.write(buf, 0, n) } } }
            } finally { c.disconnect() }
            val found = runCatching { addresses(tmp) }.getOrElse { record(app.packageName, version, size, emptySet(), d.publisherName, "unreadable"); return null }
            val briefs = runCatching { rpc.briefs(found.toList()) }.getOrElse { log("found treasuries: accounts for ${app.packageName}: ${it.message?.take(60)}"); return null }
            val wallets = briefs.filterValues { it != null && it.owner == SYSTEM && !it.executable }.keys - NOT_TREASURIES
            record(app.packageName, version, size, wallets, d.publisherName, "${found.size} keys")
            return Scan(app.packageName, version, wallets, d.publisherName)
        } catch (e: Exception) { log("found treasuries: ${app.packageName}: ${e.message?.take(80)}"); return null } finally { tmp.delete() }
    }

    private fun record(pkg: String, version: String, size: Long, wallets: Set<String>, publisher: String?, note: String) =
        db.exec("INSERT OR REPLACE INTO apk_scan (pkg, version, at, size, wallets, publisher, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
            pkg, version, System.currentTimeMillis(), size, wallets.joinToString(","), publisher, note)

    // ---- Payments in ---------------------------------------------------------------------------------------------

    /**
     * What [wallet] received in its newest transactions: SOL to the wallet and SKR/USDC to its associated token accounts
     * (token payments are signed against the token account, never the wallet), each from a payer other than the wallet.
     */
    fun measure(wallet: String): Measure {
        val sigs = (listOf(wallet, ata(wallet, Config.SKR_MINT), ata(wallet, Config.USDC_MINT)).flatMap { a -> runCatching { rpc.signatures(a, SIGS_READ) }.getOrDefault(emptyList()) })
            .filter { !it.err }.distinctBy { it.signature }.sortedByDescending { it.blockTime }.take(TXS_READ)
        var payments = 0; val payers = HashSet<String>(); var lamports = 0L; var skr = 0L; var usdc = 0L; var first: Long? = null; var last: Long? = null
        sigs.forEach { s ->
            val t = runCatching { rpc.tx(s.signature, wallet) }.getOrNull() ?: return@forEach
            if (!t.ok || t.feePayer == wallet) return@forEach
            val inSkr = t.tokensIn[Config.SKR_MINT] ?: 0L; val inUsdc = t.tokensIn[Config.USDC_MINT] ?: 0L
            if (t.lamportsIn <= 0 && inSkr <= 0 && inUsdc <= 0) return@forEach
            payments++; payers += t.feePayer; lamports += maxOf(0, t.lamportsIn); skr += maxOf(0, inSkr); usdc += maxOf(0, inUsdc)
            first = minOf(first ?: t.blockTime, t.blockTime); last = maxOf(last ?: t.blockTime, t.blockTime)
        }
        val m = Measure(sigs.size, payments, payers.size, lamports, skr, usdc, first, last)
        db.exec("INSERT OR REPLACE INTO found_wallet (wallet, measured_at, read, payments, payers, lamports, skr, usdc, first, last) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            wallet, System.currentTimeMillis(), m.read, m.payments, m.payers, m.lamports, m.skr, m.usdc, m.first, m.last)
        return m
    }

    /** One round: read the next build, then measure any wallet not measured in a week. */
    fun tick(catalogue: List<CatalogueApp>) {
        nextToScan(catalogue)?.let { scan(it) }
        val now = System.currentTimeMillis()
        val measured = db.query("SELECT wallet, measured_at FROM found_wallet") { it.getString(1) to it.getLong(2) }.toMap()
        candidates().keys.filter { (measured[it] ?: 0L) < now - REMEASURE_MS }.take(3).forEach { w -> runCatching { measure(w) }.onFailure { log("found treasuries: measure: ${it.message?.take(60)}") } }
    }

    /** Wallet → (packages it was found in, publishers of those packages), before the payment test. */
    fun candidates(): Map<String, Pair<Set<String>, Set<String>>> {
        val rows = db.query("SELECT pkg, wallets, publisher FROM apk_scan WHERE wallets != ''") { Triple(it.getString(1), it.getString(2).split(',').filter(String::isNotBlank), it.getString(3)) }
        val out = HashMap<String, Pair<MutableSet<String>, MutableSet<String>>>()
        rows.forEach { (pkg, ws, pub) -> ws.forEach { w -> val e = out.getOrPut(w) { mutableSetOf<String>() to mutableSetOf() }; e.first += pkg; e.second += (pub?.trim()?.lowercase() ?: "?:$pkg") } }
        return out.filterKeys { it !in NOT_TREASURIES && !Operator.isOperator(it) }
    }

    /**
     * The list: one row per app and wallet that passes every test in the header, by app name (never ranked). [confirmed]
     * holds treasuries already confirmed for any app; [hidden] the operator's hide ids ("treasury:<pkg>:<wallet>").
     */
    fun list(names: Map<String, String>, confirmed: Set<String>, confirmedApps: Set<String>, hidden: Set<String>): List<JSONObject> {
        val m = db.query("SELECT wallet, measured_at, read, payments, payers, lamports, skr, usdc, first, last FROM found_wallet") { rs ->
            rs.getString(1) to JSONObject().put("measured_at", rs.getLong(2) / 1000).put("transactions_read", rs.getInt(3)).put("payments", rs.getInt(4)).put("payers", rs.getInt(5))
                .put("sol", rs.getLong(6) / 1e9).put("skr", rs.getLong(7) / 1e6).put("usdc", rs.getLong(8) / 1e6)
                .put("first", rs.getLong(9).takeIf { !rs.wasNull() } ?: JSONObject.NULL).put("last", rs.getLong(10).takeIf { !rs.wasNull() } ?: JSONObject.NULL)
        }.toMap()
        return candidates().flatMap { (w, v) ->
            val (pkgs, pubs) = v
            val meas = m[w] ?: return@flatMap emptyList()
            if (pubs.size != 1 || w in confirmed || meas.getInt("payers") < MIN_PAYERS) return@flatMap emptyList()
            pkgs.filter { it !in confirmedApps && "treasury:$it:$w" !in hidden }.map { pkg ->
                JSONObject(meas.toString()).put("package", pkg).put("name", names[pkg] ?: pkg).put("wallet", w).put("found_in", "the app's store build")
            }
        }.sortedBy { it.getString("name").lowercase() }
    }

    // ---- Full payment history and analytics (owner, 9 Oct 2026: "analytics that allows trends, dates") -----------------

    private val board = Board(db, rpc)
    private val prices = Prices(db)

    /** Wallets that pass the payment test (listed for at least one app), whatever the app-level hides. */
    fun listedWallets(): List<String> {
        val payers = db.query("SELECT wallet, payers FROM found_wallet") { it.getString(1) to it.getInt(2) }.toMap()
        return candidates().filter { (w, v) -> v.second.size == 1 && (payers[w] ?: 0) >= MIN_PAYERS }.keys.sorted()
    }

    /** One round of history reading: the least-read listed wallet gets one older page and up to [maxFetch] transactions. */
    fun historyTick(maxFetch: Int = 60) {
        val w = listedWallets().minByOrNull { board.allProgress(it).let { (all, left) -> if (all == 0) -1 else all - left } } ?: return
        if (board.allProgress(w).let { (all, left) -> all > 0 && left == 0 } && db.get("pay_exhausted:found:$w:$w") == "1") {
            // Complete: re-read the newest only, at most hourly.
            val k = "found_hist_at:$w"; if (System.currentTimeMillis() - (db.get(k)?.toLongOrNull() ?: 0L) < 3_600_000L) return; db.put(k, System.currentTimeMillis().toString())
        }
        board.readAll(w, backfillPages = 1, maxFetch = maxFetch)
    }

    private fun cached(wallet: String): List<com.undersigned.protocol.Payments.Payment> =
        db.query("SELECT sig, time, payer, lamports, tokens FROM pay_rows WHERE addr_key = ? AND fetched = 1 AND payer IS NOT NULL", "found:$wallet") { rs ->
            val t = rs.getString(5)?.let { JSONObject(it) } ?: JSONObject()
            com.undersigned.protocol.Payments.Payment(rs.getString(1), rs.getString(3), rs.getLong(2), rs.getLong(4), t.keys().asSequence().associateWith { t.getLong(it) })
        }

    private fun month(t: Long) = java.time.Instant.ofEpochSecond(t).toString().take(7)
    private fun day(t: Long) = java.time.Instant.ofEpochSecond(t).toString().take(10)
    private fun r2(v: Double) = Math.round(v * 100) / 100.0

    /**
     * The trends for one wallet's full history so far: totals, dates, a monthly series with new and returning payers,
     * the last twelve weeks, repeat rate and how much the largest payers account for. USD is on the day of each payment;
     * a payment on a day with no price is counted in [unpriced] and left out of USD, never guessed.
     */
    fun walletAnalytics(wallet: String): JSONObject {
        val ps = cached(wallet).sortedBy { it.time }
        val h = prices.history()
        val usdOf = ps.associate { it.signature to prices.usdWhenPaid(it, h) }
        val (listed, left) = board.allProgress(wallet)
        val o = JSONObject().put("wallet", wallet)
            .put("coverage", JSONObject().put("transactions_listed", listed).put("transactions_read", listed - left)
                .put("history_complete", left == 0 && Board.addressesOf(wallet).all { db.get("pay_exhausted:found:$wallet:$it") == "1" }))
        if (ps.isEmpty()) return o.put("payments", 0)
        val seen = HashSet<String>()
        val byMonth = ps.groupBy { month(it.time) }.toSortedMap().map { (m, l) ->
            val payers = l.map { it.payer }.toSet(); val fresh = payers.filter { it !in seen }; seen += payers
            JSONObject().put("month", m).put("payments", l.size).put("payers", payers.size).put("new_payers", fresh.size).put("returning_payers", payers.size - fresh.size)
                .put("sol", l.sumOf { it.lamports } / 1e9).put("skr", l.sumOf { it.tokens[Config.SKR_MINT] ?: 0L } / 1e6).put("usdc", l.sumOf { it.tokens[Config.USDC_MINT] ?: 0L } / 1e6)
                .put("usd", r2(l.sumOf { usdOf[it.signature] ?: 0.0 })).put("unpriced", l.count { usdOf[it.signature] == null })
        }
        val now = System.currentTimeMillis() / 1000
        val weeks = (11 downTo 0).map { k -> val to = now - k * 7 * 86_400L; val from = to - 7 * 86_400L
            val l = ps.filter { it.time in from until to }
            JSONObject().put("week_ending", day(to)).put("payments", l.size).put("payers", l.map { it.payer }.toSet().size).put("usd", r2(l.sumOf { usdOf[it.signature] ?: 0.0 })) }
        val perPayer = ps.groupBy { it.payer }
        val usdTotal = ps.sumOf { usdOf[it.signature] ?: 0.0 }
        val payerUsd = perPayer.mapValues { (_, l) -> l.sumOf { usdOf[it.signature] ?: 0.0 } }.values.sortedDescending()
        val pricedUsd = ps.mapNotNull { usdOf[it.signature] }.sorted()
        return o.put("payments", ps.size).put("payers", perPayer.size)
            .put("first_payment", day(ps.first().time)).put("last_payment", day(ps.last().time))
            .put("active_days", ps.map { day(it.time) }.toSet().size)
            .put("sol", ps.sumOf { it.lamports } / 1e9).put("skr", ps.sumOf { it.tokens[Config.SKR_MINT] ?: 0L } / 1e6).put("usdc", ps.sumOf { it.tokens[Config.USDC_MINT] ?: 0L } / 1e6)
            .put("usd_when_paid", r2(usdTotal)).put("unpriced", ps.count { usdOf[it.signature] == null })
            .put("median_payment_usd", pricedUsd.getOrNull(pricedUsd.size / 2)?.let(::r2) ?: JSONObject.NULL)
            .put("repeat_payers", perPayer.count { it.value.size >= 2 })
            .put("top_payer_share", if (usdTotal > 0) r2(payerUsd.first() / usdTotal) else JSONObject.NULL)
            .put("top_10_payers_share", if (usdTotal > 0) r2(payerUsd.take(10).sum() / usdTotal) else JSONObject.NULL)
            .put("monthly", JSONArray(byMonth)).put("last_12_weeks", JSONArray(weeks))
    }

    /** Store-wide monthly totals across every listed wallet (each wallet once, however many apps it was found in). */
    fun trends(): JSONObject {
        val h = prices.history()
        val all = listedWallets().flatMap { w -> cached(w).map { w to it } }
        val months = all.groupBy { month(it.second.time) }.toSortedMap().map { (m, l) ->
            JSONObject().put("month", m).put("payments", l.size).put("payers", l.map { it.second.payer }.toSet().size).put("wallets_paid", l.map { it.first }.toSet().size)
                .put("usd", r2(l.sumOf { prices.usdWhenPaid(it.second, h) ?: 0.0 })).put("unpriced", l.count { prices.usdWhenPaid(it.second, h) == null })
        }
        val progress = listedWallets().map { board.allProgress(it) }
        return JSONObject().put("wallets", listedWallets().size).put("monthly", JSONArray(months))
            .put("transactions_listed", progress.sumOf { it.first }).put("transactions_read", progress.sumOf { it.first - it.second })
    }

    fun stats(): JSONObject = JSONObject()
        .put("builds_read", db.query("SELECT count(*) FROM apk_scan WHERE note LIKE '% keys'") { it.getInt(1) }.first())
        .put("builds_skipped", db.query("SELECT count(*) FROM apk_scan WHERE note NOT LIKE '% keys'") { it.getInt(1) }.first())
        .put("wallets_found", candidates().size)
}
