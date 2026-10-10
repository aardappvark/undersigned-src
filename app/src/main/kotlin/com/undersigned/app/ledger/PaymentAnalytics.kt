package com.undersigned.app.ledger

import org.json.JSONArray
import org.json.JSONObject

/**
 * Store-wide payment analytics from the Undersigned API (`/v1/payments/summary`, owner 10 Oct 2026): what dApps and their
 * treasuries were paid over a window, by category, rising and falling, and how. "Confirmed" = a treasury its publisher listed
 * and proved; "found" = a wallet written into the app's store build, confirmed by no one. USD on the day paid, gross receipts.
 * Parsed leniently: a missing field reads as zero or empty, so a newer server never breaks the screen.
 */
object PaymentAnalytics {
    val WINDOWS = listOf("7d", "30d", "90d", "1y", "all")

    data class Coverage(val listed: Int, val read: Int, val note: String)
    data class Bucket(val bucket: String, val usd: Double, val payments: Int, val payers: Int)
    data class Leader(val rank: Int, val pkg: String, val name: String, val wallet: String, val confirmed: Boolean, val usd: Double, val share: Double,
        val payments: Int, val payers: Int, val newPayers: Int, val spark: List<Double>)
    data class AppRef(val pkg: String, val name: String, val usd: Double)
    /** [change]: percent against the window before; null = no figure; [isNew] = nothing paid in the window before. */
    data class Category(val rank: Int, val name: String, val usd: Double, val share: Double, val change: Double?, val isNew: Boolean, val appsPaid: Int, val leaders: List<AppRef>)
    data class Mover(val pkg: String, val name: String, val confirmed: Boolean, val usd: Double, val previousUsd: Double, val change: Double?, val isNew: Boolean, val payments: Int, val newPayers: Int)
    data class Rail(val payments: Int, val usd: Double, val share: Double)
    data class Wallet(val rank: Int, val wallet: String, val confirmed: Boolean, val apps: List<AppRef>, val usd: Double, val payments: Int, val payers: Int)
    data class Summary(
        val window: String, val coverage: Coverage?,
        val totalUsd: Double, val payments: Int, val payers: Int, val apps: Int,
        val leaders: List<Leader>, val categories: List<Category>,
        val rising: List<Mover>, val falling: List<Mover>, val newcomers: List<Mover>, val minBaseUsd: Double,
        val rails: Map<String, Rail>, val repeatShare: Double, val top10Share: Double,
        val median: Double, val p90: Double, val max: Double, val byWeekday: List<Int>,
        val wallets: List<Wallet>, val confirmedSeries: List<Bucket>, val foundSeries: List<Bucket>,
    )
    data class AppSeries(val pkg: String, val name: String, val series: List<Bucket>, val totalUsd: Double, val payments: Int, val payers: Int, val anyFound: Boolean)

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.doubles(): List<Double> = if (this == null) emptyList() else (0 until length()).map { optDouble(it, 0.0).takeIf { d -> !d.isNaN() } ?: 0.0 }
    private fun JSONObject.d(k: String) = optDouble(k, 0.0).takeIf { !it.isNaN() } ?: 0.0
    private fun buckets(a: JSONArray?) = a.objects().map { Bucket(it.optString("bucket"), it.d("usd"), it.optInt("payments"), it.optInt("payers")) }
    private fun refs(a: JSONArray?) = a.objects().map { AppRef(it.optString("package"), it.optString("name").ifBlank { it.optString("package") }, it.d("usd")) }
    /** change_pct is a number, the string "new", or null. */
    private fun change(o: JSONObject): Pair<Double?, Boolean> = when (val v = o.opt("change_pct")) {
        is Number -> v.toDouble() to false
        is String -> if (v == "new") null to true else (v.toDoubleOrNull() to false)
        else -> null to false
    }
    private fun mover(o: JSONObject): Mover { val (c, n) = change(o)
        return Mover(o.optString("package"), o.optString("name").ifBlank { o.optString("package") }, o.optString("source") == "confirmed", o.d("usd"), o.d("previous_usd"), c, n, o.optInt("payments"), o.optInt("new_payers")) }

    fun parse(j: JSONObject): Summary {
        val lb = j.optJSONObject("leaderboard") ?: JSONObject()
        val cats = j.optJSONObject("categories") ?: JSONObject()
        val tr = j.optJSONObject("trending") ?: JSONObject()
        val pt = j.optJSONObject("patterns") ?: JSONObject()
        val se = j.optJSONObject("series") ?: JSONObject()
        val cov = j.optJSONObject("coverage")?.let { Coverage(it.optInt("found_transactions_listed"), it.optInt("found_transactions_read"), it.optString("note")) }
        val railsJ = pt.optJSONObject("rails") ?: JSONObject()
        val size = pt.optJSONObject("payment_size_usd") ?: JSONObject()
        return Summary(
            window = j.optString("window"), coverage = cov,
            totalUsd = lb.d("total_usd"), payments = lb.optInt("payments"), payers = lb.optInt("payers"), apps = lb.optInt("apps"),
            leaders = lb.optJSONArray("rows").objects().map { Leader(it.optInt("rank"), it.optString("package"), it.optString("name").ifBlank { it.optString("package") }, it.optString("wallet"),
                it.optString("source") == "confirmed", it.d("usd"), it.d("share"), it.optInt("payments"), it.optInt("payers"), it.optInt("new_payers"), it.optJSONArray("spark").doubles()) },
            categories = cats.optJSONArray("categories").objects().map { val (c, n) = change(it)
                Category(it.optInt("rank"), it.optString("category"), it.d("usd"), it.d("share"), c, n, it.optInt("apps_paid"), refs(it.optJSONArray("leaders"))) },
            rising = tr.optJSONArray("rising").objects().map(::mover), falling = tr.optJSONArray("falling").objects().map(::mover),
            newcomers = tr.optJSONArray("newcomers").objects().map(::mover), minBaseUsd = tr.d("min_base_usd"),
            rails = listOf("sol", "skr", "usdc").associateWith { k -> railsJ.optJSONObject(k)?.let { Rail(it.optInt("payments"), it.d("usd"), it.d("share")) } ?: Rail(0, 0.0, 0.0) },
            repeatShare = pt.optJSONObject("payers")?.d("repeat_share") ?: 0.0,
            top10Share = pt.optJSONObject("concentration")?.d("top_10_apps_share") ?: 0.0,
            median = size.d("median"), p90 = size.d("p90"), max = size.d("max"),
            byWeekday = pt.optJSONArray("by_weekday").doubles().map { it.toInt() },
            wallets = (j.optJSONObject("treasuries")?.optJSONArray("rows")).objects().map { Wallet(it.optInt("rank"), it.optString("wallet"), it.optString("source") == "confirmed",
                refs(it.optJSONArray("apps")), it.d("usd"), it.optInt("payments"), it.optInt("payers")) },
            confirmedSeries = buckets(se.optJSONArray("confirmed")), foundSeries = buckets(se.optJSONArray("found")),
        )
    }

    fun parseApp(j: JSONObject): AppSeries = AppSeries(j.optString("package"), j.optString("name"), buckets(j.optJSONArray("series")), j.d("total_usd"), j.optInt("payments"), j.optInt("payers"),
        j.optJSONArray("sources").objects().any { it.optString("source") != "confirmed" })

    // ---- One app's payment history (owner, 10 Oct 2026): /v1/payments/app/{pkg}/series and /v1/payments/found/{pkg} ----------

    /** A wallet an app was paid into: its publisher's confirmed treasury, or one found in the app's code and confirmed by no one. */
    data class Source(val wallet: String, val confirmed: Boolean, val payments: Int, val usd: Double)
    /** One window's totals as the API computed them from the payments themselves: [payers] is DISTINCT wallets in the window. */
    data class WindowTotal(val usd: Double, val payments: Int, val payers: Int)
    /**
     * One app's series by week or month, with its sources and all-time totals ([payers] = distinct paying wallets). [windows]
     * holds the server's per-window totals ("7d" … "all"); empty from a server older than 11 Oct 2026.
     */
    data class History(val pkg: String, val name: String, val sources: List<Source>, val series: List<Bucket>, val totalUsd: Double, val payments: Int, val payers: Int,
        val windows: Map<String, WindowTotal> = emptyMap())
    data class FoundMonth(val month: String, val payments: Int, val payers: Int, val newPayers: Int, val returningPayers: Int, val usd: Double)
    /**
     * A found wallet's own reading: amounts per rail in tokens (not USD), repeat payers, concentration as fractions 0..1, the
     * median payment in USD, and how much of its history has been read ([txRead] of [txListed]).
     */
    data class FoundWallet(val wallet: String, val sol: Double, val skr: Double, val usdc: Double, val payments: Int, val payers: Int, val repeatPayers: Int,
        val topPayerShare: Double, val top10Share: Double, val medianUsd: Double, val txListed: Int, val txRead: Int, val complete: Boolean, val monthly: List<FoundMonth>)

    fun parseHistory(j: JSONObject): History = History(j.optString("package"), j.optString("name").ifBlank { j.optString("package") },
        j.optJSONArray("sources").objects().map { Source(it.optString("wallet"), it.optString("source") == "confirmed", it.optInt("payments"), it.d("usd")) },
        buckets(j.optJSONArray("series")).sortedBy { it.bucket }, j.d("total_usd"), j.optInt("payments"), j.optInt("payers"),
        j.optJSONObject("windows")?.let { o -> WINDOWS.mapNotNull { w -> o.optJSONObject(w)?.let { t -> w to WindowTotal(t.d("usd"), t.optInt("payments"), t.optInt("payers")) } }.toMap() } ?: emptyMap())

    fun parseFound(j: JSONObject): List<FoundWallet> = j.optJSONArray("wallets").objects().map { w ->
        val cov = w.optJSONObject("coverage") ?: JSONObject()
        FoundWallet(w.optString("wallet"), w.d("sol"), w.d("skr"), w.d("usdc"), w.optInt("payments"), w.optInt("payers"), w.optInt("repeat_payers"),
            w.d("top_payer_share"), w.d("top_10_payers_share"), w.d("median_payment_usd"), cov.optInt("transactions_listed"), cov.optInt("transactions_read"),
            cov.optBoolean("history_complete", true),
            w.optJSONArray("monthly").objects().map { FoundMonth(it.optString("month"), it.optInt("payments"), it.optInt("payers"), it.optInt("new_payers"), it.optInt("returning_payers"), it.d("usd")) }
                .sortedBy { it.month })
    }

    /** Days in a window; null for "all". */
    fun windowDays(window: String): Int? = when (window) { "7d" -> 7; "30d" -> 30; "90d" -> 90; "1y" -> 365; else -> null }

    /**
     * The weekly buckets (keyed by the week's first day, "2026-10-05") that overlap the last [window] up to [today]: a week counts
     * when it ends after the window starts. "all" keeps everything; an unreadable key is kept rather than silently dropped.
     */
    fun weeksIn(series: List<Bucket>, window: String, today: java.time.LocalDate): List<Bucket> {
        val days = windowDays(window) ?: return series
        val start = today.minusDays(days.toLong())
        return series.filter { b -> runCatching { java.time.LocalDate.parse(b.bucket.take(10)).plusDays(7).isAfter(start) }.getOrDefault(true) }
    }

    /**
     * The last [n] weeks up to [today] as USD per week, oldest first, a missing week as 0 (the desk sparkline, 1.1.6). Weeks are
     * keyed by their Monday ("2026-10-05"), as the API buckets them; a key that is not a date is ignored.
     */
    fun lastWeeks(series: List<Bucket>, n: Int, today: java.time.LocalDate): List<Double> {
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val byWeek = series.mapNotNull { b -> runCatching { java.time.LocalDate.parse(b.bucket.take(10)) }.getOrNull()?.let { it to b.usd } }
            .groupBy({ it.first.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }, { it.second }).mapValues { it.value.sum() }
        return (n - 1 downTo 0).map { byWeek[monday.minusWeeks(it.toLong())] ?: 0.0 }
    }

    /** USD over the last 30 days: the server's own window total when it sent one, else the weeks overlapping the window. */
    fun last30Usd(h: History, today: java.time.LocalDate): Double = h.windows["30d"]?.usd ?: weeksIn(h.series, "30d", today).sumOf { it.usd }

    /** "+45%", "−58%" (true minus sign), or null when there is no number. */
    fun pct(change: Double?): String? = change?.let { if (it >= 0) String.format(java.util.Locale.US, "+%.0f%%", it) else String.format(java.util.Locale.US, "−%.0f%%", -it) }
}
