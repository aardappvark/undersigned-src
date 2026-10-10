package com.undersigned.app.ledger

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** The summary shape as the live API served it on 10 Oct 2026, trimmed; change_pct as a number, "new" and null. */
class PaymentAnalyticsTest {
    private val json = """
    {"window":"30d",
     "leaderboard":{"total_usd":59480.05,"payments":4539,"payers":899,"apps":39,
       "rows":[{"rank":1,"package":"live.photofinish","name":"Photo Finish Live","wallet":"FTxK","source":"found","usd":39953.88,"share":67.17,"payments":1028,"payers":186,"new_payers":67,"spark":[4540.52,8417.24]},
               {"rank":2,"package":"a.b","name":"","wallet":"W2","source":"confirmed","usd":10,"share":1,"payments":1,"payers":1,"new_payers":0,"spark":[]}]},
     "categories":{"categories":[{"rank":1,"category":"Games","usd":59480.05,"share":100,"change_pct":-57.89,"apps_paid":35,"leaders":[{"package":"live.photofinish","name":"Photo Finish Live","usd":39953.88}]},
                                 {"rank":2,"category":"Tools","usd":5,"share":0,"change_pct":"new","apps_paid":1,"leaders":[]},
                                 {"rank":3,"category":"Other","usd":0,"share":0,"change_pct":null,"apps_paid":0,"leaders":[]}]},
     "trending":{"rising":[{"package":"x","name":"X","source":"found","usd":1931.22,"previous_usd":1328.4,"change_pct":45.38,"payments":613,"new_payers":38}],
                 "falling":[],"newcomers":[{"package":"n","name":"N","source":"found","usd":345.12,"previous_usd":0,"change_pct":"new","payments":181,"new_payers":52}],"min_base_usd":20},
     "patterns":{"rails":{"sol":{"payments":3228,"usd":8176.79,"share":13.76},"usdc":{"payments":1218,"usd":51148.79,"share":86.06}},
                 "payers":{"repeat":502,"new":521,"repeat_share":49.07},"concentration":{"top_10_apps_share":98.21,"apps_paid":39},
                 "payment_size_usd":{"median":0.63,"p90":17,"max":4371.44},"by_weekday":[655,575,531,813,778,602,585]},
     "treasuries":{"rows":[{"rank":1,"wallet":"FTxK","source":"found","apps":[{"package":"live.photofinish","name":"Photo Finish Live"}],"usd":39953.88,"payments":1028,"payers":186}]},
     "series":{"confirmed":[{"bucket":"2026-09-07","usd":0,"payments":0,"payers":0}],"found":[{"bucket":"2026-09-07","usd":5719.13,"payments":736,"payers":246}]}}
    """.trimIndent()

    @Test fun parsesTheLiveShape() {
        val s = PaymentAnalytics.parse(JSONObject(json))
        assertEquals("30d", s.window)
        assertNull(s.coverage) // the live server did not send it on 10 Oct; the screen then shows no coverage line
        assertEquals(59480.05, s.totalUsd, 1e-9)
        assertEquals(2, s.leaders.size)
        assertFalse(s.leaders[0].confirmed); assertTrue(s.leaders[1].confirmed)
        assertEquals("a.b", s.leaders[1].name) // a blank name falls back to the package
        assertEquals(listOf(4540.52, 8417.24), s.leaders[0].spark)
        assertEquals(-57.89, s.categories[0].change!!, 1e-9); assertFalse(s.categories[0].isNew)
        assertNull(s.categories[1].change); assertTrue(s.categories[1].isNew)
        assertNull(s.categories[2].change); assertFalse(s.categories[2].isNew)
        assertTrue(s.newcomers.single().isNew)
        assertEquals(0, s.rails.getValue("skr").payments) // a missing rail reads as zero
        assertEquals(7, s.byWeekday.size); assertEquals(813, s.byWeekday[3])
        assertEquals(17.0, s.p90, 1e-9)
        assertEquals("Photo Finish Live", s.wallets.single().apps.single().name)
        assertEquals(5719.13, s.foundSeries.single().usd, 1e-9)
    }

    @Test fun percentSigns() {
        assertEquals("+45%", PaymentAnalytics.pct(45.38))
        assertEquals("−58%", PaymentAnalytics.pct(-57.89))
        assertEquals("+0%", PaymentAnalytics.pct(0.0))
        assertNull(PaymentAnalytics.pct(null))
    }

    @Test fun emptyObjectDoesNotThrow() {
        val s = PaymentAnalytics.parse(JSONObject("{}"))
        assertTrue(s.leaders.isEmpty() && s.categories.isEmpty() && s.byWeekday.isEmpty())
    }

    // ---- One app's history (shapes as the live API served them for live.photofinish on 10 Oct 2026, trimmed) ----

    private val week = """{"payers":3765,"package":"live.photofinish","sources":[{"wallet":"FTxK","payments":41244,"usd":4352952.42,"source":"found"},{"wallet":"W2","payments":3,"usd":9.5,"source":"confirmed"}],
      "series":[{"bucket":"2026-10-05","payers":81,"usd":11442.05,"payments":259},{"bucket":"2026-09-28","payers":65,"usd":7638.54,"payments":244},{"bucket":"2026-09-21","payers":84,"usd":11488.55,"payments":278},{"bucket":"2025-02-24","payers":148,"usd":43109,"payments":488}],
      "by":"week","total_usd":4352952.42,"payments":41244,"name":""}"""

    @Test fun parsesAnAppsHistorySortedOldestFirst() {
        val h = PaymentAnalytics.parseHistory(JSONObject(week))
        assertEquals("live.photofinish", h.name) // blank name falls back to the package
        assertEquals(listOf("2025-02-24", "2026-09-21", "2026-09-28", "2026-10-05"), h.series.map { it.bucket })
        assertFalse(h.sources[0].confirmed); assertTrue(h.sources[1].confirmed)
        assertEquals(3765, h.payers); assertEquals(41244, h.payments)
    }

    @Test fun readsTheServersWindowTotalsWithDistinctPayers() {
        // Distinct payers in a window can be far below the weekly counts added up (the same wallet pays in several weeks).
        val j = JSONObject(week).put("windows", JSONObject("""{"7d":{"usd":11442.05,"payments":259,"payers":81},"30d":{"usd":30569.14,"payments":781,"payers":150},
          "90d":{"usd":1,"payments":1,"payers":1},"1y":{"usd":2,"payments":2,"payers":2},"all":{"usd":4352952.42,"payments":41244,"payers":3765},"2y":{"usd":9,"payments":9,"payers":9}}"""))
        val w = PaymentAnalytics.parseHistory(j).windows
        assertEquals(PaymentAnalytics.WINDOWS.toSet(), w.keys) // an unknown window is ignored
        assertEquals(PaymentAnalytics.WindowTotal(30569.14, 781, 150), w["30d"])
        assertTrue(w.getValue("30d").payers < 81 + 65 + 84) // the old "summed by week" figure for those weeks
        assertEquals(3765, w.getValue("all").payers)
        // An older server without the field: no totals, so the screen falls back to the labelled weekly sum.
        assertTrue(PaymentAnalytics.parseHistory(JSONObject(week)).windows.isEmpty())
    }

    @Test fun deskSparklineIsTwelveWeeksOldestFirstWithGapsAsZero() {
        val h = PaymentAnalytics.parseHistory(JSONObject(week))
        // Saturday 10 Oct 2026: the current week is the one starting Monday 5 Oct.
        val w = PaymentAnalytics.lastWeeks(h.series, 12, java.time.LocalDate.parse("2026-10-10"))
        assertEquals(12, w.size)
        assertEquals(listOf(11488.55, 7638.54, 11442.05), w.takeLast(3))
        assertEquals(0.0, w.first(), 0.0)              // 20 Jul: no payments that week, drawn as 0, not skipped
        assertEquals(30569.14, w.sum(), 0.001)        // the Feb 2025 week is outside the 12
        // On a Monday the new week is the last point (still 0) and the oldest of the previous run drops off.
        assertEquals(0.0, PaymentAnalytics.lastWeeks(h.series, 12, java.time.LocalDate.parse("2026-10-12")).last(), 0.0)
        assertTrue(PaymentAnalytics.lastWeeks(emptyList(), 12, java.time.LocalDate.parse("2026-10-10")).all { it == 0.0 })
        // 30 days: the server's window total when sent, else the overlapping weeks.
        assertEquals(30569.14, PaymentAnalytics.last30Usd(h, java.time.LocalDate.parse("2026-10-10")), 0.001)
        val withWindows = PaymentAnalytics.parseHistory(JSONObject(week).put("windows", JSONObject("""{"30d":{"usd":27000.5,"payments":700,"payers":120}}""")))
        assertEquals(27000.5, PaymentAnalytics.last30Usd(withWindows, java.time.LocalDate.parse("2026-10-10")), 0.0)
    }

    @Test fun windowKeepsTheWeeksThatOverlapIt() {
        val s = PaymentAnalytics.parseHistory(JSONObject(week)).series
        val today = java.time.LocalDate.parse("2026-10-10")
        // 7 days back is 3 Oct: the week of 28 Sep ends 5 Oct (inside), the week of 21 Sep ends 28 Sep (outside).
        assertEquals(listOf("2026-09-28", "2026-10-05"), PaymentAnalytics.weeksIn(s, "7d", today).map { it.bucket })
        assertEquals(listOf("2026-09-21", "2026-09-28", "2026-10-05"), PaymentAnalytics.weeksIn(s, "30d", today).map { it.bucket })
        // Boundary: a week ending exactly on the window's first day is outside it.
        assertEquals(listOf("2026-10-05"), PaymentAnalytics.weeksIn(s, "7d", java.time.LocalDate.parse("2026-10-12")).map { it.bucket })
        assertEquals(4, PaymentAnalytics.weeksIn(s, "all", today).size)
        assertEquals(3, PaymentAnalytics.weeksIn(s, "1y", today).size)
        assertTrue(PaymentAnalytics.weeksIn(emptyList(), "90d", today).isEmpty())
    }

    @Test fun parsesAFoundWalletsReading() {
        val j = JSONObject("""{"package":"live.photofinish","name":"Photo Finish Live","wallets":[{"coverage":{"transactions_listed":286563,"transactions_read":71850,"history_complete":false},
          "wallet":"FTxK","top_payer_share":0.16,"payments":41331,"repeat_payers":1995,"median_payment_usd":20,"usdc":4360677.51,"payers":3765,"sol":0.0685,"skr":0,
          "top_10_payers_share":0.57,"monthly":[{"payers":93,"month":"2026-10","new_payers":21,"payments":335,"usd":14468.06,"returning_payers":72},{"payers":116,"month":"2025-02","new_payers":116,"payments":379,"usd":32453,"returning_payers":0}]}]}""")
        val f = PaymentAnalytics.parseFound(j).single()
        assertEquals(0.16, f.topPayerShare, 1e-9); assertEquals(0.57, f.top10Share, 1e-9)
        assertEquals(1995, f.repeatPayers); assertEquals(20.0, f.medianUsd, 1e-9)
        assertFalse(f.complete); assertEquals(71850, f.txRead); assertEquals(286563, f.txListed)
        assertEquals(listOf("2025-02", "2026-10"), f.monthly.map { it.month })
        assertEquals(72, f.monthly.last().returningPayers)
        assertTrue(PaymentAnalytics.parseFound(JSONObject("{}")).isEmpty())
    }
}
