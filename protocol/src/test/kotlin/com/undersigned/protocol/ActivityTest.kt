package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The `act` record: months used of the last six, last use, reinstalls; attached only to its own review. */
class ActivityTest {
    private val day = 86_400_000L

    @Test fun roundTripsAndRejectsBadValues() {
        val a = ReviewMemo.Activity("com.example.app", 5, Recency.WEEK, 2)
        val wire = ReviewMemo.encode(a)
        assertEquals("SRL1|act|com.example.app|m5|r1|i2", wire)
        assertEquals(a, ReviewMemo.decode(wire))
        for (bad in listOf("SRL1|act|com.example.app|m7|r1|i2", "SRL1|act|com.example.app|5|r1|i2", "SRL1|act|com.example.app|m5|rx|i2",
                "SRL1|act|com.example.app|m5|r1|i10", "SRL1|act|com.example.app|m5|r1", "SRL1|act|bad pkg|m5|r1|i2")) assertNull(ReviewMemo.decode(bad), bad)
    }

    @Test fun recencyBoundaries() {
        val now = 100 * day
        assertEquals(Recency.TODAY, Recency.of(now - 1, now))
        assertEquals(Recency.WEEK, Recency.of(now - day, now))
        assertEquals(Recency.WEEK, Recency.of(now - 6 * day, now))
        assertEquals(Recency.MONTH, Recency.of(now - 7 * day, now))
        assertEquals(Recency.QUARTER, Recency.of(now - 30 * day, now))
        assertEquals(Recency.OLDER, Recency.of(now - 90 * day, now))
        assertEquals(Recency.NEVER, Recency.of(0, now))
        assertEquals(Recency.TODAY, Recency.of(now + day, now))          // a clock running backwards reads as today, never negative
    }

    @Test fun attachesOnlyToTheSameSignersReviewOfTheSameAppInTheSameTransaction() {
        val rev = LedgerEntry("tx1:0", "w1", 1, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, null))
        val act = LedgerEntry("tx1:1", "w1", 1, ReviewMemo.Activity("com.a.b", 4, Recency.MONTH, 0))
        val otherApp = LedgerEntry("tx2:1", "w2", 1, ReviewMemo.Activity("com.x.y", 6, Recency.TODAY, 0))
        val rev2 = LedgerEntry("tx2:0", "w2", 1, ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.WORKS), 3, null))
        val elsewhere = LedgerEntry("tx3:0", "w2", 1, ReviewMemo.Activity("com.a.b", 6, Recency.TODAY, 0))   // another transaction
        val idx = Usage.activity(listOf(rev, act, otherApp, rev2, elsewhere))
        assertEquals(mapOf("tx1:0" to act.memo), idx)
    }
}
