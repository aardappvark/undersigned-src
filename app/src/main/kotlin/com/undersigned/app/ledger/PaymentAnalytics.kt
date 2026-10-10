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

    /** "+45%", "−58%" (true minus sign), or null when there is no number. */
    fun pct(change: Double?): String? = change?.let { if (it >= 0) String.format(java.util.Locale.US, "+%.0f%%", it) else String.format(java.util.Locale.US, "−%.0f%%", -it) }
}
