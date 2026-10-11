package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Feature requests and crash reports: how fast a proven publisher decides, delivers and fixes. Hours, medians, counts only. */
class RequestsTest {
    private fun sig(c: Char) = c.toString().repeat(87)

    @Test fun `ack round trip`() {
        val m = ReviewMemo.Ack(sig('a'), true, "On the list for 1.3")
        assertEquals(m, ReviewMemo.decode(ReviewMemo.encode(m)))
        assertEquals(ReviewMemo.Ack(sig('a'), false, null), ReviewMemo.decode("SRL1|ack|${sig('a')}|n|"))
        assertNull(ReviewMemo.decode("SRL1|ack|${sig('a')}|maybe|"))
    }

    @Test fun `a request is decided in 3 hours and delivered in 2 days, a crash is fixed in 17 hours`() {
        val alice = "A".repeat(44); val bob = "B".repeat(44); val pub = "P".repeat(44); val h = 3600L
        val entries = listOf(
            LedgerEntry(sig('a'), alice, 1000, ReviewMemo.Review("com.x", 4, listOf(ReviewMemo.Tag.FEATURE_REQUEST), 1, "dark mode please")),
            LedgerEntry(sig('k'), pub, 1000 + 3 * h, ReviewMemo.Ack(sig('a'), true, "on the list")),
            LedgerEntry(sig('f'), pub, 1000 + 48 * h, ReviewMemo.Fixed(sig('a'), 2, "dark mode in 1.3")),
            LedgerEntry(sig('b'), bob, 2000, ReviewMemo.Review("com.x", 3, listOf(ReviewMemo.Tag.FEATURE_REQUEST), 1, "widgets")),
            LedgerEntry(sig('c'), bob, 5000, ReviewMemo.Review("com.x", 1, listOf(ReviewMemo.Tag.CRASHES), 1, "crashes on open")),
            LedgerEntry(sig('g'), pub, 5000 + 17 * h, ReviewMemo.Fixed(sig('c'), 3, null)),
            LedgerEntry(sig('z'), pub, 3000, ReviewMemo.Ack(sig('b'), false, null)),
        )
        val r = Reputation.requests(entries, "com.x", setOf(pub))
        assertEquals(2, r.total); assertEquals(2, r.answered); assertEquals(1, r.willBuild); assertEquals(1, r.delivered)
        assertEquals((3.0 + 1000 / 3600.0) / 2, r.medianDecisionHours!!, 1e-9)
        assertEquals(48.0, r.medianDeliverHours!!, 1e-9)
        val c = Reputation.crashes(entries, "com.x", setOf(pub))
        assertEquals(1, c.total); assertEquals(1, c.fixed); assertEquals(17.0, c.medianFixHours!!, 1e-9)
        // Nothing counts from a wallet that is not the proven publisher.
        assertEquals(0, Reputation.requests(entries, "com.x", emptySet()).answered)
    }
}
