package com.undersigned.app.ui

import com.undersigned.app.data.CatalogueApp
import com.undersigned.app.data.WeeklyCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The weekly check's timing, ported with its rules from the Toolkit's WeeklyCatalogueCheckTest. 5 Oct 2026 is a Monday. */
class CatalogueWeekTest {
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private fun t(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    @Test fun `every day of a week shares that week's Monday`() {
        (5..11).forEach { assertEquals(d(5), WeeklyCatalogue.weekBatch(d(it))) }
        assertEquals(d(12), WeeklyCatalogue.weekBatch(d(12)))
    }

    @Test fun `runs anchor on Monday 01 00 and before it this week is last Monday`() {
        assertEquals(t(5, 1), WeeklyCatalogue.thisWeeksRun(t(5, 1)))
        assertEquals(t(5, 1).minusWeeks(1), WeeklyCatalogue.thisWeeksRun(t(5, 0, 59)))
        assertEquals(t(5, 1), WeeklyCatalogue.thisWeeksRun(t(11, 23, 59)))
    }

    @Test fun `each phone starts at its own offset after Monday 01 00`() {
        assertEquals(t(5, 1, 47), WeeklyCatalogue.nextStart(t(5, 1, 20), 47))      // this week's start still ahead
        assertEquals(t(12, 1, 47), WeeklyCatalogue.nextStart(t(5, 2, 0), 47))      // past it: next Monday's
        assertEquals(t(12, 1, 0), WeeklyCatalogue.nextStart(t(5, 1, 0), 0))
        assertEquals(t(19, 1, 5), WeeklyCatalogue.nextStart(t(5, 2, 0), 5, notBefore = d(19)))
    }

    @Test fun `a running job never books its own week`() {
        assertEquals(d(12), WeeklyCatalogue.followingWeek(d(5), d(5)))
        assertEquals(d(12), WeeklyCatalogue.followingWeek(d(5), d(4)))   // fired on the Sunday before (travel west)
        assertEquals(d(19), WeeklyCatalogue.followingWeek(d(12), d(6)))
    }

    @Test fun `a catch-up waits for this phone's own start and the week counts as done once checked`() {
        assertFalse(WeeklyCatalogue.catchUpDue(t(5, 1, 20), 47))
        assertTrue(WeeklyCatalogue.catchUpDue(t(5, 1, 47), 47))
        assertTrue(WeeklyCatalogue.catchUpDue(t(8, 9), 47))
        assertTrue(WeeklyCatalogue.isDoneForWeek("2026-10-05", d(9)))
        assertFalse(WeeklyCatalogue.isDoneForWeek("2026-10-05", d(12)))
        assertFalse(WeeklyCatalogue.isDoneForWeek(null, d(9)))
    }

    @Test fun `Browse offers Update once the list predates this Monday 01 00`() {
        val z = ZoneId.of("Europe/Budapest")
        val sundayNight = ZonedDateTime.of(t(4, 22), z).toInstant().toEpochMilli()
        assertTrue(WeeklyCatalogue.isLastWeeks(sundayNight, ZonedDateTime.of(t(5, 1, 1), z)))
        assertFalse(WeeklyCatalogue.isLastWeeks(sundayNight, ZonedDateTime.of(t(5, 0, 59), z)))
        assertTrue(WeeklyCatalogue.isLastWeeks(0L, ZonedDateTime.of(t(9, 12), z)))
        assertTrue(WeeklyCatalogue.isStale(d(1), d(9))); assertFalse(WeeklyCatalogue.isStale(d(2), d(9)))
    }

    @Test fun `New is the latest batch only and shipped apps are never New`() {
        val list = listOf(CatalogueApp("a.b", "A", "", null), CatalogueApp("c.d", "C", "", null, "2026-09-28"),
            CatalogueApp("e.f", "E", "", null, "2026-10-05"), CatalogueApp("g.h", "G", "", null, "2026-10-05"))
        assertEquals(setOf("e.f", "g.h"), WeeklyCatalogue.newThisWeek(list).map { it.packageName }.toSet())
        assertTrue(WeeklyCatalogue.newThisWeek(list.take(1)).isEmpty())
    }
}
