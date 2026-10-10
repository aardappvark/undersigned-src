package com.undersigned.api

import com.undersigned.protocol.Payments
import org.json.JSONArray
import org.json.JSONObject

/**
 * Payment analytics across the store (owner, 10 Oct 2026: "graphs, leaderboards by dApp and treasury to show txs and
 * purchases that we can see from the blockchain with the confirmed or suspected wallet(s)"). Everything here is built from
 * payments already held in pay_rows, never from the network, so it is cheap to rebuild and cached five minutes.
 *
 * Two sources, always told apart: CONFIRMED treasuries (the payments board: a publisher's signed listing proven by the
 * website the store lists) and FOUND wallets (written into an app's store build, paid by 5+ wallets, not confirmed by
 * anyone). A found wallet is a suspicion with evidence, never a fact, and every row and chart says which it is.
 *
 * Receipts are gross: payments INTO the wallet from other wallets, valued in USD on the day paid. A game can take
 * deposits it later pays back out; nothing here nets outflows. Trading apps earn through swap fees that reach no
 * app wallet, so they are absent by construction.
 */
class Analytics(private val db: Db, private val prices: Prices, private val boards: Boards, private val found: () -> FoundTreasuries?,
                private val foundRows: () -> List<JSONObject>, private val categories: () -> Map<String, String?>) {

    data class Src(val packageName: String, val name: String, val wallet: String, val confirmed: Boolean, val payments: List<Payments.Payment>)
    data class Point(val bucket: String, val usd: Double, val payments: Int, val payers: Int)

    companion object {
        val WINDOWS = listOf("7d", "30d", "90d", "1y", "all")
        fun windowStart(window: String, now: Long): Long = when (window) {
            "7d" -> now - 7 * 86_400L; "30d" -> now - 30 * 86_400L; "90d" -> now - 90 * 86_400L; "1y" -> now - 365 * 86_400L; else -> 0L
        }
        fun bucket(t: Long, by: String): String {
            val d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(t), java.time.ZoneOffset.UTC)
            return when (by) {
                "day" -> d.toString()
                "month" -> d.toString().take(7)
                else -> d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()   // the Monday of the week
            }
        }
        fun r2(v: Double) = Math.round(v * 100.0) / 100.0
        /** A source is ranked for rising/falling only above this in one of the two windows. */
        const val MIN_BASE_USD = 20.0
    }

    private class Cached(val at: Long, val rows: List<BoardRow>, val srcs: List<Src>)
    @Volatile private var cache: Cached? = null

    /** Every source with its payments from the database (confirmed treasuries exclude their own wallets, as the board does). */
    fun sources(): List<Src> {
        val board = boards.rows
        cache?.let { c -> if (c.rows === board && System.currentTimeMillis() - c.at < 300_000) return c.srcs }   // a board refresh invalidates it
        val out = ArrayList<Src>()
        for (r in board.filter { it.confirmed }) {
            val byRef = Payments.isReference(r.claim.code)
            val key = if (byRef) "${r.claim.treasury}-${r.claim.code}" else r.claim.treasury
            val ps = db.query("SELECT sig, time, memo, payer, lamports, tokens FROM pay_rows WHERE addr_key = ? AND fetched = 1 AND payer IS NOT NULL", key) { rs ->
                if (!(byRef || Payments.memoMatches(rs.getString(3) ?: "", r.claim.code))) return@query null
                val t = rs.getString(6)?.let { JSONObject(it) } ?: JSONObject()
                Payments.Payment(rs.getString(1), rs.getString(4), rs.getLong(2), rs.getLong(5), t.keys().asSequence().associateWith { t.getLong(it) })
            }.filterNotNull().filter { it.payer != r.claim.treasury && it.payer !in r.own }
            out += Src(r.claim.packageName, r.name, r.claim.treasury, true, ps)
        }
        val f = found()
        // The found list has one row per app and wallet; a wallet written into several apps of one publisher is one source
        // here, named for all of them, so its payments are counted once (10 Oct 2026: a shared wallet read as three apps).
        if (f != null) for ((w, rows) in foundRows().groupBy { it.getString("wallet") }) {
            val first = rows.first()
            val name = rows.map { it.optString("name", it.getString("package")) }.distinct().let { if (it.size == 1) it[0] else it.take(3).joinToString(" / ") + (if (it.size > 3) " +${it.size - 3}" else "") }
            out += Src(first.getString("package"), name, w, false, f.payments(w))
        }
        if (board.isNotEmpty()) cache = Cached(System.currentTimeMillis(), board, out)   // an empty board (the first minute after a start) is never cached
        return out
    }

    /** One entry per dApp: its sources merged, payments deduplicated by signature (an app with two wallets counted once, 11 Oct 2026). */
    private fun byApp(srcs: List<Src>): List<Src> = srcs.groupBy { it.packageName }.map { (pkg, l) ->
        Src(pkg, l.first().name, l.map { it.wallet }.distinct().joinToString(","), l.any { it.confirmed }, l.flatMap { it.payments }.distinctBy { it.signature }) }

    private fun usd(p: Payments.Payment, h: Map<String, Map<String, Double>>) = Prices.usdWhenPaid(p, h)

    /**
     * dApps ranked by USD received in [window] ("7d", "30d", "90d", "1y", "all"), [include] = confirmed | found | all.
     * Each row: source, totals by rail, payers, payers new in the window, share of the window's total, and a weekly
     * series over the window (the last 12 weeks for "all").
     */
    fun leaderboard(window: String, include: String, limit: Int, now: Long = System.currentTimeMillis() / 1000, rail: String? = null): JSONObject {
        val h = prices.history(); val from = windowStart(window, now)
        val srcs = byApp(sources().filter { include == "all" || (include == "confirmed") == it.confirmed })
        val onRail = { p: Payments.Payment -> when (rail) { "sol" -> p.lamports > 0 && p.tokens.isEmpty(); "skr" -> (p.tokens[Config.SKR_MINT] ?: 0L) > 0; "usdc" -> (p.tokens[Config.USDC_MINT] ?: 0L) > 0; else -> true } }
        data class Row(val s: Src, val inWin: List<Payments.Payment>, val usd: Double, val unpriced: Int, val newPayers: Int)
        val rows = srcs.map { s ->
            val inWin = s.inWin(from).filter(onRail)
            val priced = inWin.map { usd(it, h) }
            val firstSeen = s.payments.groupBy { it.payer }.mapValues { (_, l) -> l.minOf { it.time } }
            Row(s, inWin, priced.filterNotNull().sum(), priced.count { it == null }, inWin.map { it.payer }.toSet().count { (firstSeen[it] ?: 0L) >= from })
        }.filter { it.inWin.isNotEmpty() }.sortedByDescending { it.usd }
        val total = rows.sumOf { it.usd }
        val sparkFrom = if (window == "all") now - 12 * 7 * 86_400L else from
        return JSONObject().put("window", window).put("include", include).put("rail", rail ?: "all").put("from", from).put("to", now)
            .put("total_usd", r2(total)).put("apps", rows.size).put("payments", rows.sumOf { it.inWin.size })
            .put("payers", rows.flatMap { r -> r.inWin.map { it.payer } }.toSet().size)
            .put("rows", JSONArray(rows.take(limit).mapIndexed { i, r ->
                JSONObject().put("rank", i + 1).put("package", r.s.packageName).put("name", r.s.name).put("wallet", r.s.wallet)
                    .put("source", if (r.s.confirmed) "confirmed" else "found").put("usd", r2(r.usd)).put("share", if (total > 0) r2(100 * r.usd / total) else 0.0)
                    .put("payments", r.inWin.size).put("payers", r.inWin.map { it.payer }.toSet().size).put("new_payers", r.newPayers).put("unpriced", r.unpriced)
                    .put("sol", r2(r.inWin.sumOf { it.lamports } / 1e9)).put("skr", r2(r.inWin.sumOf { it.tokens[Config.SKR_MINT] ?: 0L } / 1e6)).put("usdc", r2(r.inWin.sumOf { it.tokens[Config.USDC_MINT] ?: 0L } / 1e6))
                    .put("first", r.s.payments.minOfOrNull { it.time } ?: JSONObject.NULL).put("last", r.inWin.maxOf { it.time })
                    .put("spark", JSONArray(series(r.s.inWin(sparkFrom).filter(onRail), "week", h, sparkFrom, now).map { r2(it.usd) }))
            }))
            .put("label", "Confirmed: a treasury its publisher listed and proved. Found: a wallet written into the app's store build, paid by 5+ wallets, confirmed by no one; unconfirmed. USD on the day paid; receipts are gross, not revenue.")
    }

    /** Payments into every source per bucket (day | week | month) over [window], split by source kind. */
    fun storeSeries(window: String, by: String, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val h = prices.history(); val all = sources()
        // One start for every series, so confirmed and found line up bucket for bucket (window=all crashed the chart, 11 Oct 2026).
        val from = windowStart(window, now).takeIf { it > 0 } ?: (all.flatMap { it.payments }.minOfOrNull { it.time } ?: now)
        fun pts(ps: List<Payments.Payment>) = JSONArray(series(ps, by, h, from, now).map { p -> JSONObject().put("bucket", p.bucket).put("usd", r2(p.usd)).put("payments", p.payments).put("payers", p.payers) })
        val confirmed = all.filter { it.confirmed }.flatMap { it.inWin(from) }; val foundPs = all.filter { !it.confirmed }.flatMap { it.inWin(from) }
        return JSONObject().put("window", window).put("by", by).put("from", from).put("to", now)
            .put("confirmed", pts(confirmed)).put("found", pts(foundPs)).put("all", pts(confirmed + foundPs))
            .put("sources", JSONObject().put("confirmed", all.count { it.confirmed }).put("found", all.count { !it.confirmed }))
    }

    /** One app's series (all its sources) and its sources' totals. */
    fun appSeries(pkg: String, by: String, now: Long = System.currentTimeMillis() / 1000): JSONObject? {
        val h = prices.history()
        val mine = sources().filter { it.packageName == pkg }; if (mine.isEmpty()) return null
        val ps = mine.flatMap { it.payments }
        val from = ps.minOfOrNull { it.time } ?: now
        return JSONObject().put("package", pkg).put("name", mine.first().name).put("by", by)
            .put("sources", JSONArray(mine.map { s -> JSONObject().put("wallet", s.wallet).put("source", if (s.confirmed) "confirmed" else "found").put("payments", s.payments.size).put("usd", r2(s.payments.mapNotNull { usd(it, h) }.sum())) }))
            .put("series", JSONArray(series(ps, by, h, from, now).map { p -> JSONObject().put("bucket", p.bucket).put("usd", r2(p.usd)).put("payments", p.payments).put("payers", p.payers) }))
            .put("total_usd", r2(ps.mapNotNull { usd(it, h) }.sum())).put("payments", ps.size).put("payers", ps.map { it.payer }.toSet().size)
    }

    /** Wallets ranked by USD received in [window], each with the dApps it was found in or listed for. */
    fun treasuries(window: String, limit: Int, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val h = prices.history(); val from = windowStart(window, now)
        val byWallet = sources().groupBy { it.wallet }
        val rows = byWallet.map { (w, srcs) ->
            val ps = srcs.flatMap { it.inWin(from) }.distinctBy { it.signature }   // a treasury serving 28 apps by memo: every app's payments, once
            Triple(w, srcs, ps to ps.mapNotNull { usd(it, h) }.sum())
        }.filter { it.third.first.isNotEmpty() }.sortedByDescending { it.third.second }
        return JSONObject().put("window", window).put("rows", JSONArray(rows.take(limit).mapIndexed { i, (w, srcs, v) ->
            JSONObject().put("rank", i + 1).put("wallet", w).put("source", if (srcs.any { it.confirmed }) "confirmed" else "found")
                .put("apps", JSONArray(srcs.map { JSONObject().put("package", it.packageName).put("name", it.name) }))
                .put("usd", r2(v.second)).put("payments", v.first.size).put("payers", v.first.map { it.payer }.toSet().size)
                .put("sol", r2(v.first.sumOf { it.lamports } / 1e9)).put("skr", r2(v.first.sumOf { it.tokens[Config.SKR_MINT] ?: 0L } / 1e6)).put("usdc", r2(v.first.sumOf { it.tokens[Config.USDC_MINT] ?: 0L } / 1e6)) }))
    }

    private fun Src.inWin(from: Long, to: Long = Long.MAX_VALUE) = payments.filter { it.time >= from && it.time < to }

    // ---- Trends: categories, risers, newcomers, repeat payers, rails, timing (owner, 10 Oct 2026) ---------------------

    private data class Win(val from: Long, val to: Long, val prevFrom: Long)
    private fun win(window: String, now: Long): Win { val from = windowStart(window, now); val len = if (from > 0) now - from else 0L; return Win(from, now, if (from > 0) from - len else 0L) }
    /** A previous total under a dollar is "new" (a fraction of a cent once read as +1.8e11%, 11 Oct 2026). */
    private fun growth(cur: Double, prev: Double): Any = if (prev < 1.0) (if (cur > 0) "new" else JSONObject.NULL) else r2(100 * (cur - prev) / prev)

    /**
     * Categories ranked by USD received in [window], each with its change against the window before, how many of its
     * dApps were paid, and its three leaders. "all" has no previous window, so its change is null.
     */
    fun categoriesReport(window: String, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val h = prices.history(); val w = win(window, now); val cats = categories()
        data class Agg(var usd: Double = 0.0, var prev: Double = 0.0, var payments: Int = 0, val payers: HashSet<String> = HashSet(), val apps: HashMap<String, Double> = HashMap(), val appNames: HashMap<String, String> = HashMap(), val found: HashSet<String> = HashSet())
        val by = HashMap<String, Agg>()
        for (s in byApp(sources())) {
            val cat = cats[s.packageName] ?: "Uncategorised"; val a = by.getOrPut(cat) { Agg() }
            val cur = s.inWin(w.from); if (cur.isEmpty() && w.from > 0 && s.inWin(w.prevFrom, w.from).isEmpty()) continue
            val u = cur.mapNotNull { usd(it, h) }.sum()
            a.usd += u; a.payments += cur.size; cur.forEach { a.payers += it.payer }
            if (w.from > 0) a.prev += s.inWin(w.prevFrom, w.from).mapNotNull { usd(it, h) }.sum()
            if (cur.isNotEmpty()) { a.apps.merge(s.packageName, u, Double::plus); a.appNames[s.packageName] = s.name; if (!s.confirmed) a.found += s.packageName }
        }
        val total = by.values.sumOf { it.usd }
        val rows = by.entries.filter { it.value.usd > 0 || it.value.prev > 0 }.sortedByDescending { it.value.usd }.mapIndexed { i, (cat, a) ->
            JSONObject().put("rank", i + 1).put("category", cat).put("usd", r2(a.usd)).put("share", if (total > 0) r2(100 * a.usd / total) else 0.0)
                .put("change_pct", if (w.from > 0) growth(a.usd, a.prev) else JSONObject.NULL).put("previous_usd", if (w.from > 0) r2(a.prev) else JSONObject.NULL)
                .put("payments", a.payments).put("payers", a.payers.size).put("apps_paid", a.apps.size).put("of_which_found", a.found.size)
                .put("leaders", JSONArray(a.apps.entries.sortedByDescending { it.value }.take(3).map { e -> JSONObject().put("package", e.key).put("name", a.appNames[e.key]).put("usd", r2(e.value)) }))
        }
        return JSONObject().put("window", window).put("total_usd", r2(total)).put("categories", JSONArray(rows))
            .put("label", "Categories from the store's catalogue. Change compares this window with the one before it, same length. Found wallets count here as their app's, labelled in apps_paid/of_which_found.")
    }

    /**
     * dApps rising and falling: USD in [window] against the window before. Only sources with at least [MIN_BASE_USD] in
     * either window are ranked, so a first $3 does not read as +∞. Newcomers (first payment inside the window) are listed apart.
     */
    fun trending(window: String, limit: Int, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val h = prices.history(); val w = win(if (window == "all") "90d" else window, now)
        data class T(val s: Src, val cur: Double, val prev: Double, val curN: Int, val prevN: Int, val newPayers: Int, val repeat: Int)
        val ts = byApp(sources()).mapNotNull { s ->
            val cur = s.inWin(w.from); val prev = s.inWin(w.prevFrom, w.from)
            if (cur.isEmpty() && prev.isEmpty()) return@mapNotNull null
            val firstSeen = s.payments.groupBy { it.payer }.mapValues { (_, l) -> l.minOf { it.time } }
            val payers = cur.map { it.payer }.toSet()
            T(s, cur.mapNotNull { usd(it, h) }.sum(), prev.mapNotNull { usd(it, h) }.sum(), cur.size, prev.size, payers.count { (firstSeen[it] ?: 0L) >= w.from }, payers.count { (firstSeen[it] ?: 0L) < w.from })
        }
        fun row(t: T) = JSONObject().put("package", t.s.packageName).put("name", t.s.name).put("source", if (t.s.confirmed) "confirmed" else "found")
            .put("usd", r2(t.cur)).put("previous_usd", r2(t.prev)).put("change_pct", growth(t.cur, t.prev)).put("payments", t.curN).put("previous_payments", t.prevN)
            .put("new_payers", t.newPayers).put("repeat_payers", t.repeat)
        val ranked = ts.filter { maxOf(it.cur, it.prev) >= MIN_BASE_USD }
        val risers = ranked.filter { it.cur > it.prev }.sortedByDescending { if (it.prev > 0) it.cur / it.prev else Double.MAX_VALUE }.sortedByDescending { it.cur - it.prev }.take(limit)
        val fallers = ranked.filter { it.cur < it.prev }.sortedBy { it.cur - it.prev }.take(limit)
        val newcomers = ts.filter { t -> (t.s.payments.minOfOrNull { it.time } ?: 0L) >= w.from }.sortedByDescending { it.cur }.take(limit)
        return JSONObject().put("window", if (window == "all") "90d" else window).put("compared_with", "the ${label(if (window == "all") "90d" else window)} before")
            .put("rising", JSONArray(risers.map(::row))).put("falling", JSONArray(fallers.map(::row))).put("newcomers", JSONArray(newcomers.map(::row)))
            .put("min_base_usd", MIN_BASE_USD).put("label", "Rising and falling by the change in USD received between two equal windows; a source needs $MIN_BASE_USD in one of them to be ranked. Newcomers: first payment ever seen inside the window." +
                if (window == "1y") " For 1y the window before is only partly read, so falls are overstated." else "")
    }

    /** Where the money comes from and when: rails, repeat payers, concentration, payments by hour (UTC) and weekday. */
    fun patterns(window: String, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val h = prices.history(); val w = win(window, now)
        val srcs = sources(); val ps = srcs.flatMap { it.inWin(w.from) }
        val usdOf = ps.associate { it.signature to (usd(it, h) ?: 0.0) }
        val total = usdOf.values.sum()
        val sol = ps.filter { it.lamports > 0 && it.tokens.isEmpty() }; val skr = ps.filter { (it.tokens[Config.SKR_MINT] ?: 0L) > 0 }; val usdc = ps.filter { (it.tokens[Config.USDC_MINT] ?: 0L) > 0 }
        fun railJson(l: List<Payments.Payment>) = JSONObject().put("payments", l.size).put("usd", r2(l.sumOf { usdOf[it.signature] ?: 0.0 })).put("share", if (total > 0) r2(100 * l.sumOf { usdOf[it.signature] ?: 0.0 } / total) else 0.0)
        // Repeat payers: paid this app before the window too.
        // Distinct payers across the store: repeat = paid any app before the window.
        val firstSeen = srcs.flatMap { it.payments }.groupBy { it.payer }.mapValues { (_, l) -> l.minOf { it.time } }
        val winPayers = ps.map { it.payer }.toSet()
        val fresh = winPayers.count { (firstSeen[it] ?: 0L) >= w.from }; val repeat = winPayers.size - fresh
        val byApp = srcs.map { s -> s.inWin(w.from).sumOf { usdOf[it.signature] ?: 0.0 } }.sortedDescending()
        val top10 = byApp.take(10).sum()
        val hours = IntArray(24); val days = IntArray(7)
        for (p in ps) { val z = java.time.Instant.ofEpochSecond(p.time).atZone(java.time.ZoneOffset.UTC); hours[z.hour]++; days[z.dayOfWeek.value - 1]++ }
        val sizes = ps.map { usdOf[it.signature] ?: 0.0 }.sorted()
        return JSONObject().put("window", window).put("payments", ps.size).put("total_usd", r2(total))
            .put("rails", JSONObject().put("sol", railJson(sol)).put("skr", railJson(skr)).put("usdc", railJson(usdc)))
            .put("payers", JSONObject().put("repeat", repeat).put("new", fresh).put("repeat_share", if (repeat + fresh > 0) r2(100.0 * repeat / (repeat + fresh)) else 0.0))
            .put("concentration", JSONObject().put("top_10_apps_share", if (total > 0) r2(100 * top10 / total) else 0.0).put("apps_paid", byApp.count { it > 0 }))
            .put("payment_size_usd", JSONObject().put("median", if (sizes.isEmpty()) 0.0 else r2(sizes[sizes.size / 2])).put("p90", if (sizes.isEmpty()) 0.0 else r2(sizes[(sizes.size * 0.9).toInt().coerceAtMost(sizes.size - 1)])).put("max", r2(sizes.lastOrNull() ?: 0.0)))
            .put("by_hour_utc", JSONArray(hours.toList())).put("by_weekday", JSONArray(days.toList()))
            .put("label", "Payments by hour and weekday are counts, UTC, Monday first. Rails: a payment in SOL, in SKR, or in USDC.")
    }

    /** The dashboard in one answer: leaderboard, categories, trending, patterns, and the store series. */
    fun summary(window: String, now: Long = System.currentTimeMillis() / 1000): JSONObject =
        JSONObject().put("window", window).put("coverage", found()?.coverage()?.let { (listed, read) -> JSONObject().put("found_transactions_listed", listed).put("found_transactions_read", read).put("note", if (read < listed) "Found wallets' history is still being read; their figures are a floor." else "Found wallets' history fully read.") } ?: JSONObject.NULL)
            .put("leaderboard", leaderboard(window, "all", 10, now)).put("categories", categoriesReport(window, now))
            .put("trending", trending(window, 5, now)).put("patterns", patterns(window, now)).put("treasuries", treasuries(window, 10, now))
            .put("series", storeSeries(window, if (window == "7d") "day" else if (window == "all") "month" else "week", now))

    /** Buckets from [from] to [now] inclusive, every bucket present (zeros where nothing was paid). */
    fun series(ps: List<Payments.Payment>, by: String, h: Map<String, Map<String, Double>>, from0: Long, now: Long): List<Point> {
        // Weeks start on their Monday, so the first bucket is a whole week, not a false dip.
        val from = if (by == "week") java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(from0), java.time.ZoneOffset.UTC).let { d -> d.minusDays((d.dayOfWeek.value - 1).toLong()).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() } else from0
        val grouped = ps.filter { it.time >= from }.groupBy { bucket(it.time, by) }
        val step = when (by) { "day" -> 86_400L; "month" -> 0L; else -> 7 * 86_400L }
        val keys = LinkedHashSet<String>()
        if (by == "month") { var d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(from), java.time.ZoneOffset.UTC).withDayOfMonth(1)
            val end = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(now), java.time.ZoneOffset.UTC)
            while (!d.isAfter(end) && keys.size < 600) { keys += d.toString().take(7); d = d.plusMonths(1) } }
        else { var t = from; while (t <= now && keys.size < 2000) { keys += bucket(t, by); t += step }; keys += bucket(now, by) }
        return keys.map { k -> val l = grouped[k].orEmpty(); Point(k, l.mapNotNull { usd(it, h) }.sum(), l.size, l.map { it.payer }.toSet().size) }
    }

    // ---- Charts: plain SVG, dark, no script, so they embed anywhere and screenshot cleanly ---------------------------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun money(v: Double) = when { v >= 1_000_000 -> "$%.1fM".format(java.util.Locale.US, v / 1e6); v >= 10_000 -> "$%,.0fk".format(java.util.Locale.US, v / 1e3); v >= 1000 -> "$%,.1fk".format(java.util.Locale.US, v / 1e3); else -> "$%,.0f".format(java.util.Locale.US, v) }
    private val FONT = "font-family='Manrope, -apple-system, Segoe UI, Roboto, sans-serif'"

    /** Horizontal bars: the top [n] dApps in [window]; confirmed in green, found in amber. */
    fun chartTop(window: String, n: Int, include: String = "all"): String {
        val lb = leaderboard(window, include, n); val rows = lb.getJSONArray("rows")
        val w = 1200; val rowH = 44; val top = 110; val h = top + rows.length().coerceAtLeast(1) * rowH + 60
        val max = (0 until rows.length()).maxOfOrNull { rows.getJSONObject(it).getDouble("usd") }?.takeIf { it > 0 } ?: 1.0
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='$h' viewBox='0 0 $w $h' role='img' aria-label='Top dApps by payments received, ${esc(label(window))}'>")
        sb.append("<rect width='$w' height='$h' fill='#0E0B18' rx='18'/>")
        sb.append("<text x='40' y='46' $FONT font-size='26' font-weight='700' fill='#F2EEFF'>What dApps were paid · ${esc(label(window))}</text>")
        sb.append("<text x='40' y='76' $FONT font-size='15' fill='#B7AED1'>USD on the day paid · ${money(lb.getDouble("total_usd"))} across ${lb.getInt("apps")} dApps, ${lb.getInt("payers")} payers · <tspan fill='#14F195'>■</tspan> confirmed treasury  <tspan fill='#FFB547'>■</tspan> found in app code, not confirmed</text>")
        if (rows.length() == 0) sb.append("<text x='40' y='${top + 20}' $FONT font-size='16' fill='#6E6588'>No payments in this window yet.</text>")
        for (i in 0 until rows.length()) { val r = rows.getJSONObject(i); val y = top + i * rowH; val bw = (560 * r.getDouble("usd") / max).coerceAtLeast(2.0)   // leaves room for the label after the longest bar
            val col = if (r.getString("source") == "confirmed") "#14F195" else "#FFB547"
            sb.append("<text x='40' y='${y + 27}' $FONT font-size='16' fill='#F2EEFF'>${esc(r.getString("name").take(28))}</text>")
            sb.append("<rect x='330' y='${y + 10}' width='${"%.1f".format(java.util.Locale.US, bw)}' height='22' rx='6' fill='$col' opacity='0.9'/>")
            sb.append("<text x='${330 + bw + 12}' y='${y + 27}' $FONT font-size='15' fill='#F2EEFF'>${money(r.getDouble("usd"))} <tspan fill='#6E6588'>· ${r.getInt("payments")} payments · ${r.getInt("payers")} payers</tspan></text>") }
        sb.append("<text x='40' y='${h - 22}' $FONT font-size='12' fill='#6E6588'>undersigned.provenby.dev/payments · gross receipts into app wallets, from the Solana ledger · found wallets are unconfirmed</text></svg>")
        return sb.toString()
    }

    /** An area chart of a series (USD per bucket), confirmed and found stacked when both are given. */
    fun chartSeries(title: String, subtitle: String, pts: List<Point>, pts2: List<Point>? = null): String {
        val w = 1200; val h = 420; val l = 70; val r = 30; val t = 90; val b = 60
        val n = pts.size.coerceAtLeast(1)
        val tot = pts.indices.map { pts[it].usd + (pts2?.getOrNull(it)?.usd ?: 0.0) }
        val max = (tot.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
        fun x(i: Int) = l + (w - l - r) * i.toDouble() / (n - 1).coerceAtLeast(1)
        fun y(v: Double) = t + (h - t - b) * (1 - v / max)
        fun path(vals: List<Double>, base: List<Double>): String {
            val up = vals.indices.joinToString(" ") { i -> "${"%.1f".format(java.util.Locale.US, x(i))},${"%.1f".format(java.util.Locale.US, y(base[i] + vals[i]))}" }
            val down = vals.indices.reversed().joinToString(" ") { i -> "${"%.1f".format(java.util.Locale.US, x(i))},${"%.1f".format(java.util.Locale.US, y(base[i]))}" }
            return "M$up L$down Z".replace("M", "M ").replace("L", "L ")
        }
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='$h' viewBox='0 0 $w $h' role='img' aria-label='${esc(title)}'>")
        sb.append("<rect width='$w' height='$h' fill='#0E0B18' rx='18'/>")
        sb.append("<text x='40' y='44' $FONT font-size='26' font-weight='700' fill='#F2EEFF'>${esc(title)}</text><text x='40' y='70' $FONT font-size='15' fill='#B7AED1'>${esc(subtitle)}</text>")
        for (k in 0..4) { val v = max * k / 4; val yy = y(v); sb.append("<line x1='$l' x2='${w - r}' y1='${"%.1f".format(java.util.Locale.US, yy)}' y2='${"%.1f".format(java.util.Locale.US, yy)}' stroke='#231c33' stroke-width='1'/><text x='${l - 8}' y='${"%.1f".format(java.util.Locale.US, yy + 5)}' $FONT font-size='12' fill='#6E6588' text-anchor='end'>${money(v)}</text>") }
        val base = List(n) { 0.0 }
        if (pts2 != null) { sb.append("<path d='${path(pts2.map { it.usd }, base)}' fill='#FFB547' opacity='0.45'/>"); sb.append("<path d='${path(pts.map { it.usd }, pts2.map { it.usd })}' fill='#14F195' opacity='0.55'/>") }
        else sb.append("<path d='${path(pts.map { it.usd }, base)}' fill='#14F195' opacity='0.55'/>")
        val line = pts.indices.joinToString(" ") { i -> "${"%.1f".format(java.util.Locale.US, x(i))},${"%.1f".format(java.util.Locale.US, y(tot[i]))}" }
        sb.append("<polyline points='$line' fill='none' stroke='#F2EEFF' stroke-width='2' stroke-linejoin='round'/>")
        val every = (n / 8).coerceAtLeast(1)
        for (i in pts.indices step every) sb.append("<text x='${"%.1f".format(java.util.Locale.US, x(i))}' y='${h - 28}' $FONT font-size='12' fill='#6E6588' text-anchor='middle'>${esc(pts[i].bucket.let { if (it.length == 7) it else it.substring(5) })}</text>")
        if (pts2 != null) sb.append("<text x='${w - r}' y='70' $FONT font-size='13' fill='#B7AED1' text-anchor='end'><tspan fill='#14F195'>■</tspan> confirmed  <tspan fill='#FFB547'>■</tspan> found in app code</text>")
        sb.append("</svg>")
        return sb.toString()
    }

    fun label(window: String) = when (window) { "7d" -> "last 7 days"; "30d" -> "last 30 days"; "90d" -> "last 90 days"; "1y" -> "last 12 months"; else -> "all time" }
    fun pts(arr: JSONArray) = (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Point(o.getString("bucket"), o.getDouble("usd"), o.getInt("payments"), o.getInt("payers")) }
}
