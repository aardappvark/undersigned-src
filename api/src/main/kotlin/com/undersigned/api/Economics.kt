package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the service costs against what it brings in, per UTC day (owner, 10 Oct 2026: "some sort of measure for the api
 * over time, otherwise we steadily cost ourselves money"). Read on this machine only, at /v1/admin/economics, and by the
 * watchdog, which posts a notification when [alerts] is not empty.
 *
 *  - Cost: fresh Claude answers, from the tokens Claude reported, by who they were for (anon, pro, pass, credits, system),
 *    at [Config.AI_PRICE_IN] / [Config.AI_PRICE_OUT]. Everything else the server uses is free (public RPC, prices, the store
 *    feed, the tunnel); the Mac's power is not counted.
 *  - API revenue: payments redeemed for keys (passes and per-call credits).
 *  - App revenue: Pro, promotion and listing payments credited to the treasury, from the ledger.
 *  USD is on the day of each payment where a price is held, else the latest price held.
 */
class Economics(private val db: Db, private val history: () -> Map<String, Map<String, Double>>) {
    private val day = { t: Long -> java.time.Instant.ofEpochSecond(t).toString().take(10) }
    private fun r4(v: Double) = Math.round(v * 10_000) / 10_000.0

    private fun usd(dayKey: String, lamports: Long, skrRaw: Long, h: Map<String, Map<String, Double>>): Double {
        fun px(mint: String) = h[mint]?.get(dayKey) ?: h[mint]?.toSortedMap()?.values?.lastOrNull() ?: 0.0
        return lamports / 1e9 * px(Prices.SOL) + skrRaw / 1e6 * px(Config.SKR_MINT)
    }

    fun report(days: Int = 30, today: String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()): JSONObject {
        val h = runCatching { history() }.getOrDefault(emptyMap())
        val from = java.time.LocalDate.parse(today).minusDays(days - 1L).toString()
        val cost = HashMap<String, MutableMap<String, Triple<Int, Long, Long>>>()
        db.query("SELECT day, segment, calls, in_tok, out_tok FROM ai_segment WHERE day >= ?", from) { rs ->
            cost.getOrPut(rs.getString(1)) { HashMap() }[rs.getString(2)] = Triple(rs.getInt(3), rs.getLong(4), rs.getLong(5)) }
        val api = HashMap<String, Double>()
        db.query("SELECT at, rail, amount FROM redeemed") { rs -> Triple(rs.getLong(1), rs.getString(2), rs.getLong(3)) }.forEach { (at, rail, amount) ->
            val d = day(at); if (d >= from) api.merge(d, usd(d, if (rail == "SOL") amount else 0, if (rail == "SKR") amount else 0, h), Double::plus) }
        val app = HashMap<String, Double>()
        db.query("SELECT r.block_time, t.lamports, t.skr FROM records r JOIN txmeta t ON t.sig = r.sig WHERE r.memo LIKE 'SRL1|pro|%' OR r.memo LIKE 'SRL1|bst|%' OR r.memo LIKE 'SRL1|trs|%' OR r.memo LIKE 'SRL1|trd|%'") { rs ->
            Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3)) }.forEach { (t, lam, skr) ->
            val d = day(t); if (d >= from) app.merge(d, usd(d, maxOf(0, lam), maxOf(0, skr), h), Double::plus) }
        val rows = (0 until days).map { java.time.LocalDate.parse(from).plusDays(it.toLong()).toString() }.map { d ->
            val segs = cost[d].orEmpty()
            val segJson = JSONObject(); var c = 0.0
            segs.forEach { (s, v) -> val u = (v.second * Config.AI_PRICE_IN + v.third * Config.AI_PRICE_OUT) / 1e6; c += u
                segJson.put(s, JSONObject().put("answers", v.first).put("input_tokens", v.second).put("output_tokens", v.third).put("usd", r4(u))) }
            JSONObject().put("day", d).put("ai_cost_usd", r4(c)).put("ai_by_caller", segJson)
                .put("api_revenue_usd", r4(api[d] ?: 0.0)).put("app_revenue_usd", r4(app[d] ?: 0.0))
                .put("margin_usd", r4((api[d] ?: 0.0) + (app[d] ?: 0.0) - c))
        }
        fun window(n: Int): JSONObject {
            val w = rows.takeLast(n)
            val c = w.sumOf { it.getDouble("ai_cost_usd") }; val ra = w.sumOf { it.getDouble("api_revenue_usd") }; val rp = w.sumOf { it.getDouble("app_revenue_usd") }
            return JSONObject().put("days", n).put("ai_cost_usd", r4(c)).put("api_revenue_usd", r4(ra)).put("app_revenue_usd", r4(rp)).put("margin_usd", r4(ra + rp - c))
                .put("answers", w.sumOf { r -> r.getJSONObject("ai_by_caller").let { o -> o.keys().asSequence().sumOf { o.getJSONObject(it).getInt("answers") } } })
        }
        val week = window(7); val todayRow = rows.last()
        val alerts = ArrayList<String>()
        if (todayRow.getDouble("ai_cost_usd") > Config.ECON_DAY_ALERT_USD) alerts += "AI cost today is $${todayRow.getDouble("ai_cost_usd")} (alert above $${Config.ECON_DAY_ALERT_USD})."
        if (week.getDouble("ai_cost_usd") > week.getDouble("api_revenue_usd") + week.getDouble("app_revenue_usd") + Config.ECON_WEEK_FLOOR_USD)
            alerts += "AI cost over 7 days ($${week.getDouble("ai_cost_usd")}) is more than revenue ($${r4(week.getDouble("api_revenue_usd") + week.getDouble("app_revenue_usd"))}) plus $${Config.ECON_WEEK_FLOOR_USD}."
        val anonToday = todayRow.getJSONObject("ai_by_caller").optJSONObject("anon")?.getInt("answers") ?: 0
        return JSONObject().put("today", todayRow).put("last_7_days", week).put("last_30_days", window(minOf(30, days)))
            .put("anon_pool_today", JSONObject().put("used", anonToday).put("of", Config.AI_ANON_POOL_DAY))
            .put("alerts", JSONArray(alerts)).put("days", JSONArray(rows))
            .put("prices", JSONObject().put("claude_input_per_mtok", Config.AI_PRICE_IN).put("claude_output_per_mtok", Config.AI_PRICE_OUT))
            .put("note", "Cost counts fresh Claude answers only (cached and local answers are free). Revenue: API keys redeemed, and Pro, promotion and listing payments to the treasury, in USD on the day paid.")
    }
}
