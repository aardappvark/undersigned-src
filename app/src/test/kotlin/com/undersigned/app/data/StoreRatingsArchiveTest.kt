package com.undersigned.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class StoreRatingsArchiveTest {
    @Test fun recordsOneFilePerDayKeepsTheNewestAndReadsBack() {
        val dir = Files.createTempDirectory("ratings").toFile()
        val a = StoreRatingsArchive(dir)
        val t0 = 1_790_000_000L
        for (d in 0 until StoreRatingsArchive.KEEP_DAYS + 5) a.record(mapOf("com.a.b" to listOf(1, 2, 3, 4, d), "com.c.d" to listOf(0, 0, 0, 0, 1)), t0 + d * 86_400L)
        a.record(mapOf("com.a.b" to listOf(1, 2, 3, 4, 999)), t0 + (StoreRatingsArchive.KEEP_DAYS + 4) * 86_400L + 60)   // same day again: replaced
        assertEquals(StoreRatingsArchive.KEEP_DAYS, a.days().size)
        val s = a.snapshots("com.a.b")
        assertEquals(StoreRatingsArchive.KEEP_DAYS, s.size); assertEquals(5, s.first().byStar[4]); assertEquals(999, s.last().byStar[4])
        assertEquals(StoreRatingsArchive.KEEP_DAYS - 1, a.snapshots("com.c.d").size)   // last day's file no longer holds it
        dir.deleteRecursively()
    }
}
