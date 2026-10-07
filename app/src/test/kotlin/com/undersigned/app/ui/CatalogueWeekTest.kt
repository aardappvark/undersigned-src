package com.undersigned.app.ui

import com.undersigned.app.notify.CatalogueWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The Update banner turns on at Monday 07:00 on the phone's clock, not a minute earlier, and stays on until updated. */
class CatalogueWeekTest {
    private val z = ZoneId.of("Europe/Budapest")
    private fun t(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, z)   // 5 Oct 2026 is a Monday

    @Test fun `the week starts at Monday 07 00`() {
        assertEquals(t(5, 7), CatalogueWorker.weekStart(t(5, 7)))
        assertEquals(t(5, 7), CatalogueWorker.weekStart(t(11, 23, 59)))      // Sunday night: still this week
        assertEquals(t(5, 7).minusWeeks(1), CatalogueWorker.weekStart(t(5, 6, 59)))   // Monday before 07:00: last week's
    }

    @Test fun `a list from before this Monday morning is last week's`() {
        val sundayNight = t(4, 22).toInstant().toEpochMilli()
        assertTrue(CatalogueWorker.isLastWeeks(sundayNight, t(5, 7, 1)))
        assertFalse(CatalogueWorker.isLastWeeks(sundayNight, t(5, 6, 59)))
        assertFalse(CatalogueWorker.isLastWeeks(t(5, 8).toInstant().toEpochMilli(), t(9, 12)))
        assertTrue(CatalogueWorker.isLastWeeks(0L, t(9, 12)))                 // never updated: the bundled list
    }
}
