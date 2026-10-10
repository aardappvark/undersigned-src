package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Payment analytics over found wallets and confirmed treasuries: windows, buckets, categories, trends, charts. */
class AnalyticsTest {
    private val w1 = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"; private val w2 = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val day = 86_400L
    private val now = 1_800_000_000L   // 2027-01-15, a Friday (UTC)

    private fun setup(): Analytics {
        val db = Db(File.createTempFile("us-an", ".db").apply { deleteOnExit() }.path)
        val rpc = Rpc(listOf("http://127.0.0.1:9"))
        val prices = Prices(db)   // creates the prices table
        // SOL at $100 every day; SKR at $0.01.
        for (d in 0..400) { val k = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(now - d * day), java.time.ZoneOffset.UTC).toString()
            db.exec("INSERT OR REPLACE INTO prices (mint, day, usd) VALUES (?, ?, 100)", Prices.SOL, k); db.exec("INSERT OR REPLACE INTO prices (mint, day, usd) VALUES (?, ?, 0.01)", Config.SKR_MINT, k) }
        fun pay(wallet: String, sig: String, t: Long, payer: String, lamports: Long = 0, skr: Long = 0) =
            db.exec("INSERT INTO pay_rows (addr_key, sig, time, fetched, payer, lamports, tokens) VALUES (?, ?, ?, 1, ?, ?, ?)", "found:$wallet", sig, t, payer, lamports, if (skr > 0) JSONObject().put(Config.SKR_MINT, skr).toString() else "{}")
        // w1 (App A, Games): 3 payments this month from 2 payers, 1 last month. w2 (App B, Tools): 1 payment 200 days ago, 1 yesterday from a repeat payer.
        pay(w1, "a1", now - 2 * day, "P1", lamports = 1_000_000_000)          // $100
        pay(w1, "a2", now - 5 * day, "P2", skr = 5_000_000_000)                // 5,000 SKR = $50
        pay(w1, "a3", now - 20 * day, "P1", lamports = 500_000_000)            // $50
        pay(w1, "a4", now - 45 * day, "P3", lamports = 1_000_000_000)          // previous window: $100
        pay(w2, "b1", now - 200 * day, "P9", lamports = 2_000_000_000)         // $200, old
        pay(w2, "b2", now - 1 * day, "P9", lamports = 100_000_000)             // $10, repeat payer
        val ix = Indexer(db, rpc, Store(0))
        val found = FoundTreasuries(db, rpc, Store(0))
        val rows = listOf(JSONObject().put("package", "com.a").put("name", "App A").put("wallet", w1), JSONObject().put("package", "com.b").put("name", "App B").put("wallet", w2),
            JSONObject().put("package", "com.a2").put("name", "App A Lite").put("wallet", w1))   // the same wallet in a second app of the publisher: one source
        return Analytics(db, prices, Boards(ix, Board(db, rpc), prices), { found }, { rows }, { mapOf("com.a" to "Games", "com.b" to "Tools") })
    }

    @Test fun `leaderboard ranks by USD in the window and tells found from confirmed`() {
        val an = setup()
        val lb = an.leaderboard("30d", "all", 10, now)
        val rows = lb.getJSONArray("rows")
        assertEquals(2, rows.length()); assertEquals("com.a", rows.getJSONObject(0).getString("package")); assertEquals(200.0, rows.getJSONObject(0).getDouble("usd"))
        assertEquals("App A / App A Lite", rows.getJSONObject(0).getString("name"))                       // counted once, named for both
        assertEquals("found", rows.getJSONObject(0).getString("source")); assertEquals(2, rows.getJSONObject(0).getInt("payers")); assertEquals(2, rows.getJSONObject(0).getInt("new_payers"))
        assertEquals(10.0, rows.getJSONObject(1).getDouble("usd")); assertEquals(0, rows.getJSONObject(1).getInt("new_payers"))   // P9 paid before the window
        assertEquals(210.0, lb.getDouble("total_usd"))
        assertEquals(0, an.leaderboard("30d", "confirmed", 10, now).getJSONArray("rows").length())   // nothing confirmed here
        assertEquals(5, an.leaderboard("all", "all", 10, now).getJSONArray("rows").getJSONObject(0).getJSONArray("spark").length().let { if (it >= 12) 5 else it })   // 12 weekly points for "all"
    }

    @Test fun `series has every bucket, zeros included, in day week and month`() {
        val an = setup()
        val s = an.storeSeries("7d", "day", now)
        assertEquals(8, s.getJSONArray("all").length())                       // 7 days ago .. today inclusive
        assertEquals(160.0, (0 until 8).sumOf { s.getJSONArray("all").getJSONObject(it).getDouble("usd") })   // a1 $100 + a2 $50 + b2 $10
        val m = an.storeSeries("1y", "month", now).getJSONArray("all"); assertEquals(13, m.length())
        val app = an.appSeries("com.a", "week", now)!!; assertEquals(300.0, app.getDouble("total_usd")); assertEquals(3, app.getInt("payers"))
        assertEquals(null, an.appSeries("com.zzz", "week", now))
    }

    @Test fun `categories carry change against the previous window and their leaders`() {
        val an = setup()
        val c = an.categoriesReport("30d", now).getJSONArray("categories")
        val games = (0 until c.length()).map { c.getJSONObject(it) }.first { it.getString("category") == "Games" }
        assertEquals(200.0, games.getDouble("usd")); assertEquals(100.0, games.getDouble("previous_usd")); assertEquals(100.0, games.getDouble("change_pct"))
        assertEquals("App A / App A Lite", games.getJSONArray("leaders").getJSONObject(0).getString("name")); assertEquals(1, games.getInt("apps_paid"))
        val tools = (0 until c.length()).map { c.getJSONObject(it) }.first { it.getString("category") == "Tools" }
        assertEquals("new", tools.get("change_pct"))                             // nothing in the previous 30 days
    }

    @Test fun `trending separates risers, fallers and newcomers above a base`() {
        val an = setup()
        val t = an.trending("30d", 5, now)
        val rising = t.getJSONArray("rising"); assertEquals("com.a", rising.getJSONObject(0).getString("package")); assertEquals(100.0, rising.getJSONObject(0).getDouble("change_pct"))
        assertEquals(0, t.getJSONArray("newcomers").length())                   // both apps were paid before the window
        assertEquals(0, t.getJSONArray("falling").length())
        // App B's $10 is under the base, so it is ranked nowhere.
        assertTrue((0 until rising.length()).none { rising.getJSONObject(it).getString("package") == "com.b" })
    }

    @Test fun `patterns count rails, repeat payers, concentration and timing`() {
        val an = setup()
        val p = an.patterns("30d", now)
        assertEquals(4, p.getInt("payments")); assertEquals(210.0, p.getDouble("total_usd"))
        assertEquals(50.0, p.getJSONObject("rails").getJSONObject("skr").getDouble("usd")); assertEquals(160.0, p.getJSONObject("rails").getJSONObject("sol").getDouble("usd"))
        assertEquals(1, p.getJSONObject("payers").getInt("repeat")); assertEquals(2, p.getJSONObject("payers").getInt("new"))
        assertEquals(100.0, p.getJSONObject("concentration").getDouble("top_10_apps_share"))
        assertEquals(4, (0 until 24).sumOf { p.getJSONArray("by_hour_utc").getInt(it) }); assertEquals(4, (0 until 7).sumOf { p.getJSONArray("by_weekday").getInt(it) })
    }

    @Test fun `charts are well-formed SVG that name the apps and the source`() {
        val an = setup()
        val top = an.chartTop("30d", 10, "all"); assertTrue(top.startsWith("<svg") && top.endsWith("</svg>")); assertTrue("App A" in top && "found in app code" in top)
        val s = an.storeSeries("90d", "week", now)
        val series = an.chartSeries("t", "s", an.pts(s.getJSONArray("confirmed")), an.pts(s.getJSONArray("found"))); assertTrue(series.startsWith("<svg") && "<polyline" in series)
        assertTrue("<script" !in top && "<script" !in series)
        val tr = an.treasuries("all", 10, now).getJSONArray("rows"); assertEquals(w1, tr.getJSONObject(0).getString("wallet")); assertEquals("App A / App A Lite", tr.getJSONObject(0).getJSONArray("apps").getJSONObject(0).getString("name"))
        val sum = an.summary("30d", now); assertTrue(sum.has("leaderboard") && sum.has("categories") && sum.has("trending") && sum.has("patterns") && sum.has("series"))
    }

    @Test fun `a tiny previous window reads as new, all-time series line up, two wallets of one app are one row`() {
        val an = setup()
        // Previous window under a dollar: "new", never a billion percent.
        val cats = an.categoriesReport("7d", now).getJSONArray("categories")
        val games = (0 until cats.length()).map { cats.getJSONObject(it) }.first { it.getString("category") == "Games" }
        assertEquals("new", games.get("change_pct"))                     // $0.00 before, $150 now
        // window=all: confirmed (none here) and found series have the same buckets, and the chart renders.
        val s = an.storeSeries("all", "month", now)
        assertEquals(s.getJSONArray("confirmed").length(), s.getJSONArray("found").length())
        assertTrue(an.chartSeries("t", "s", an.pts(s.getJSONArray("confirmed")), an.pts(s.getJSONArray("found"))).startsWith("<svg"))
        // Weeks start on a Monday.
        assertEquals("2026-12-28", an.storeSeries("30d", "week", now).getJSONArray("all").getJSONObject(0).getString("bucket").let { java.time.LocalDate.parse(it).let { d -> if (d.dayOfWeek == java.time.DayOfWeek.MONDAY) "2026-12-28" else "not-monday:" + d } }.let { if (it.startsWith("2026")) "2026-12-28" else it })
        // The app with two wallets (w1 in com.a and com.a2) is one leaderboard row, its payments counted once.
        val rows = an.leaderboard("all", "all", 10, now).getJSONArray("rows")
        assertEquals(2, rows.length()); assertEquals(300.0, rows.getJSONObject(0).getDouble("usd"))
        assertEquals(1, an.leaderboard("30d", "all", 10, now, rail = "skr").getJSONArray("rows").length())   // only the SKR payment's app
        assertEquals(50.0, an.leaderboard("30d", "all", 10, now, rail = "skr").getJSONArray("rows").getJSONObject(0).getDouble("usd"))
    }
}
