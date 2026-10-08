package com.undersigned.protocol

import com.undersigned.protocol.ReviewMemo.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the chart draws: counts land in the right period, and each score is the one that stood at the end of it. */
class TimelineTest {
    private val D = Timeline.DAY
    private val t0 = 1_790_000_000L
    private val pkg = "com.example.app"
    private val attested = { _: String -> SignerFacts(true, 0.0) }
    private fun id(c: Char) = "1" + c.toString().repeat(86)
    private fun rev(c: Char, who: String, t: Long, rating: Int) =
        LedgerEntry(id(c), who, t, ReviewMemo.Review(pkg, rating, listOf(Tag.WORKS), 1, "Swaps settle fast and hand-offs just work"))

    @Test fun `bars never exceed the cap and cover the whole range`() {
        assertEquals(1L, Timeline.bucketDays(t0, t0 + 30 * D))
        assertEquals(10L, Timeline.bucketDays(t0, t0 + 365 * D))
        val b = Timeline.build(pkg, emptyList(), emptyList(), emptyMap(), attested, { false }, t0, t0 + 365 * D)
        assertTrue(b.size <= Timeline.MAX_BARS)
        assertEquals(t0, b.first().start); assertEquals(t0 + 365 * D, b.last().end)
    }

    @Test fun `store reviews split into Seeker owners and other wallets, in the period they were written`() {
        val store = listOf(Timeline.StorePoint(t0 + 1 * D + 5, 5, true), Timeline.StorePoint(t0 + 1 * D + 9, 1, false), Timeline.StorePoint(t0 + 3 * D, 4, false))
        val b = Timeline.build(pkg, store, emptyList(), emptyMap(), attested, { false }, t0, t0 + 5 * D)
        assertEquals(5, b.size)
        assertEquals(1 to 1, b[1].storeSeeker to b[1].storeOther)
        assertEquals(0 to 1, b[3].storeSeeker to b[3].storeOther)
        assertNull(b[0].storeMean)                       // nothing read yet
        assertEquals(3.0, b[1].storeMean!!, 1e-9)        // (5 + 1) / 2
        assertEquals(10.0 / 3, b[4].storeMean!!, 1e-9)   // running: (5 + 1 + 4) / 3
        assertEquals(3, b[4].storeRead)
    }

    @Test fun `the signed score appears when a review is signed and steps with each new one`() {
        val entries = listOf(rev('A', "a", t0 + 2 * D, 4), rev('B', "b", t0 + 4 * D, 2))
        val b = Timeline.build(pkg, emptyList(), entries, emptyMap(), attested, { false }, t0, t0 + 6 * D)
        assertNull(b[1].signedMean)
        assertEquals(4.0, b[2].signedMean!!, 1e-9); assertEquals(1, b[2].signed)
        assertEquals(3.0, b[5].signedMean!!, 1e-9); assertEquals(2, b[5].signedCounted)
        assertEquals(2, b.sumOf { it.signed })
    }

    @Test fun `the member line sits apart from the signed line only where Pro weight exists`() {
        val entries = listOf(LedgerEntry(id('P'), "member", t0, ReviewMemo.Pro(12)), rev('A', "member", t0 + D, 5), rev('B', "other", t0 + D, 1))
        val b = Timeline.build(pkg, emptyList(), entries, emptyMap(), attested, { true }, t0, t0 + 3 * D).last()
        assertEquals(3.0, b.signedMean!!, 1e-9)
        assertTrue(b.memberMean!! > b.signedMean!!)
        val noPro = Timeline.build(pkg, emptyList(), entries, emptyMap(), attested, { false }, t0, t0 + 3 * D).last()
        assertEquals(noPro.signedMean!!, noPro.memberMean!!, 1e-9)
    }

    @Test fun `uncounted wallets add no signed bar and no score`() {
        val entries = listOf(rev('A', "nogenesis", t0 + D, 5))
        val b = Timeline.build(pkg, emptyList(), entries, emptyMap(), { SignerFacts(false, 0.0) }, { false }, t0, t0 + 3 * D)
        assertEquals(0, b.sumOf { it.signed }); assertTrue(b.all { it.signedMean == null })
    }

    @Test fun `All starts at the earliest store or signed review`() {
        val store = listOf(Timeline.StorePoint(t0 + 9 * D, 5, false))
        assertEquals(t0 + 2 * D, Timeline.earliest(pkg, store, listOf(rev('A', "a", t0 + 2 * D, 4))))
        assertNull(Timeline.earliest(pkg, emptyList(), emptyList()))
    }
}
