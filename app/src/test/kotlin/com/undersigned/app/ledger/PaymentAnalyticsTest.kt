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
}
