package com.undersigned.app.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallHistoryTest {
    private val D = 86_400_000L
    private fun run(vararg looks: Pair<Long, Long?>): InstallHistory.Presence? = looks.fold(null as InstallHistory.Presence?) { p, (now, fit) -> InstallHistory.observe(p, fit, now) }

    @Test fun oneInstallCountsFromItsFirstInstallTime() {
        val p = run(10 * D to 0L, 20 * D to 0L)!!
        assertEquals(30 * D, InstallHistory.totalMs(p, 30 * D)); assertEquals(0, p.reinstalls)
    }

    @Test fun anUninstallClosesAtTheLastSightingNotLater() {
        // Installed day 0, seen day 10, gone by day 15 (the removal is only noticed then), back day 20, looked at day 25.
        val p = run(10 * D to 0L, 15 * D to null, 25 * D to 20 * D)!!
        assertEquals(10 * D + 5 * D, InstallHistory.totalMs(p, 25 * D)); assertEquals(1, p.reinstalls)
    }

    @Test fun aReinstallBetweenTwoLooksIsCaught() {
        // Seen on day 10 with first install day 0; by day 30 its first-install time reads day 20: gone and back in between.
        val p = run(10 * D to 0L, 30 * D to 20 * D)!!
        assertEquals(10 * D + 10 * D, InstallHistory.totalMs(p, 30 * D)); assertEquals(1, p.reinstalls)
    }

    @Test fun noTimeIsCountedTwice() {
        // A first-install time earlier than the end of the last recorded period (clock change) starts at that end.
        val p = run(10 * D to 0L, 12 * D to 5 * D)!!
        assertEquals(10 * D + 2 * D, InstallHistory.totalMs(p, 12 * D))
    }

    @Test fun aClampedStartIsStillTheSameInstallOnLaterLooks() {
        // The review's case: once clamped, every later look used to count another reinstall and add a span.
        val p = run(10 * D to 0L, 12 * D to 5 * D, 14 * D to 5 * D, 16 * D to 5 * D)!!
        assertEquals(1, p.reinstalls); assertEquals(1, p.spans.size)
        assertEquals(10 * D + 6 * D, InstallHistory.totalMs(p, 16 * D))
    }

    @Test fun neverInstalledIsNothing() {
        assertNull(run(10 * D to null))
    }
}
