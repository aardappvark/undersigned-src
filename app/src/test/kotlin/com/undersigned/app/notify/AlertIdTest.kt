package com.undersigned.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Alert ids used to be a per-run counter starting at 1000, so the first alert of one run replaced the first
 * alert of the previous run — measured on the owner's Seeker on 20 Sep 2026, where an update prompt from
 * 18 Sep had taken the place of a reply alert from 17 Sep. These pin what an id must now do.
 */
class AlertIdTest {
    @Test fun `different alerts never share an id`() {
        val keys = listOf(
            "rep:5XTu3S73JVaDV2JQ", "rev:5XTu3S73JVaDV2JQ",          // two kinds of alert about ONE record
            "rev:gf5yf4ErscvsqZ", "rep:gf5yf4ErscvsqZ",
            "upd:com.adappvark.toolkit:234", "upd:com.adappvark.toolkit:235",   // consecutive versions of one app
            "upd:com.rentbacksol.app:234",
            "div:com.adappvark.toolkit", "wave:com.adappvark.toolkit",
        )
        val ids = keys.map { LedgerWatchWorker.alertId(it) }
        assertEquals("ids collided: ${keys.zip(ids)}", keys.size, ids.toSet().size)
    }

    @Test fun `the same alert keeps one id, so re-posting replaces rather than stacks`() {
        assertEquals(LedgerWatchWorker.alertId("rep:abc"), LedgerWatchWorker.alertId("rep:abc"))
    }

    @Test fun `an id is no longer the run-position it used to be`() {
        // The old scheme gave the first alert of every run id 1000. Nothing may land there by construction.
        listOf("rep:abc", "rev:abc", "upd:com.example.app:1").forEach { assertNotEquals(1000, LedgerWatchWorker.alertId(it)) }
    }
}
