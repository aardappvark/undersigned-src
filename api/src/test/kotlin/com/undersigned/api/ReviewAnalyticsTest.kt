package com.undersigned.api

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ReviewAnalyticsTest {
    private fun dir(): File {
        val d = File.createTempFile("storescan", "").apply { delete(); mkdirs() }
        File(d, "reviews.jsonl").writeText(listOf(
            """{"package":"a.one","id":"1","rating":5,"created":"2026-09-30T10:00:00Z","text":"Great wallet, fast and clear","domain":"x.skr","wallet":"W1","reply":"thanks"}""",
            """{"package":"a.one","id":"2","rating":3,"created":"2026-08-02T10:00:00Z","text":"ok","domain":"","wallet":"W2","reply":null}""",
            """{"package":"b.two","id":"3","rating":1,"created":"2026-10-05T10:00:00Z","text":"crashes","domain":"y.skr","wallet":"W3","reply":null}""",
            """{"package":"b.two","id":"3","rating":1,"created":"2026-10-05T10:00:00Z","text":"duplicate line","domain":"y.skr","wallet":"W3","reply":null}""",
        ).joinToString("\n"))
        return d
    }
    private val now = java.time.Instant.parse("2026-10-12T00:00:00Z").epochSecond
    private fun ra(signed: List<ReviewAnalytics.Rev> = emptyList()) = ReviewAnalytics(dir(), { mapOf("a.one" to "Wallets", "b.two" to "Games") }, { mapOf("a.one" to "One", "b.two" to "Two") }, { signed })

    @Test fun storeRowsAreDeduplicatedAndSummarised() {
        val o = ra().summary("store", "all", null, null, "month", 10, now)
        assertEquals(3, o.getInt("reviews")); assertEquals(2, o.getInt("apps")); assertEquals(3.0, o.getDouble("mean"))
        assertEquals(listOf(1, 0, 1, 0, 1), (0 until 5).map { o.getJSONArray("stars").getInt(it) })
        assertEquals(33.33, o.getDouble("replied_share")); assertEquals(66.67, o.getDouble("short_share")); assertEquals(66.67, o.getDouble("skr_share"))
        val cats = o.getJSONArray("categories"); assertEquals("Wallets", cats.getJSONObject(0).getString("category")); assertEquals(2, cats.getJSONObject(0).getInt("reviews"))
        assertEquals("One", o.getJSONArray("top_apps").getJSONObject(0).getString("name"))
        assertTrue(o.getJSONArray("series").length() >= 3)
    }

    @Test fun windowsCategoriesAndPackagesNarrow() {
        val a = ra()
        assertEquals(2, a.summary("store", "30d", null, null, "day", 10, now).getInt("reviews"))
        assertEquals(1, a.summary("store", "all", "Games", null, "month", 10, now).getInt("reviews"))
        assertEquals(2, a.summary("store", "all", null, "a.one", "month", 10, now).getInt("reviews"))
    }

    @Test fun signedSourcesFilterByClass() {
        val rows = listOf(ReviewAnalytics.Rev("a.one", 4, now - 100, 7, true, false, setOf("organic", "pro")), ReviewAnalytics.Rev("a.one", 5, now - 200, 2, false, false, setOf("ours")),
            ReviewAnalytics.Rev("b.two", 2, now - 300, 0, false, true, setOf("quick")))
        val a = ra(rows)
        assertEquals(3, a.summary("all", "all", null, null, "week", 10, now).getInt("reviews"))
        assertEquals(1, a.summary("pro", "all", null, null, "week", 10, now).getInt("reviews"))
        assertEquals(1, a.summary("ours", "all", null, null, "week", 10, now).getInt("reviews"))
        assertEquals(0, a.summary("promoted", "all", null, null, "week", 10, now).getInt("reviews"))
    }

    @Test fun csvAndChartsRender() {
        val a = ra(); val o = a.summary("store", "all", null, null, "month", 10, now)
        assertTrue(a.csv(o).startsWith("section,key,reviews")); assertTrue(a.csv(o).contains("category,\"Wallets\",2,4.0"))
        assertEquals(4, a.storeCsv(10).trim().lines().size); assertTrue(!a.storeCsv(10).contains("Great wallet"))
        val cats = o.getJSONArray("categories").let { c -> (0 until c.length()).map { c.getJSONObject(it) } }
        assertTrue(a.chartBars("t", "s", cats, "category", "store", "f").contains("Wallets"))
        val pts = o.getJSONArray("series").let { s -> (0 until s.length()).map { ReviewAnalytics.Point(s.getJSONObject(it).getString("bucket"), s.getJSONObject(it).getInt("reviews"), s.getJSONObject(it).getDouble("mean")) } }
        assertTrue(a.chartSeries("t", "s", pts, "store", "f").contains("<polyline"))
        assertTrue(a.chartStars("t", "s", o.getJSONArray("stars"), "store", "f").contains("5★"))
    }
}
