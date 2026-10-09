package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** Quick reviews: the marker, their own average per app, and who wrote a relayed transaction. */
class QuickReviewsTest {
    @Test fun markerRoundTripsAndScoresAreTheirOwn() {
        assertEquals(ReviewMemo.Quick, ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Quick)))
        fun r(id: String, who: String, t: Long, stars: Int) = LedgerEntry("$id:0", who, t, ReviewMemo.Review("com.a.b", stars, listOf(ReviewMemo.Tag.WORKS), 3, null))
        fun q(id: String, who: String, t: Long) = LedgerEntry("$id:1", who, t, ReviewMemo.Quick)
        val entries = listOf(r("a", "K1", 1, 2), q("a", "K1", 1), r("b", "K1", 2, 4), q("b", "K1", 2),   // K1 changed its mind: latest counts
            r("c", "K2", 3, 5), q("c", "K2", 3), r("d", "W", 4, 1))                                              // W signed normally: not quick
        assertEquals(setOf("a:0", "b:0", "c:0"), QuickReviews.ids(entries))
        val s = QuickReviews.scores(entries).getValue("com.a.b")
        assertEquals(2, s.count); assertEquals(4.5, s.mean)
    }

    @Test fun aRelayedTransactionIsWrittenByItsSecondSigner() {
        assertEquals("APPKEY", Registry.author(listOf(Registry.QUICK_RELAYER, "APPKEY")))
        assertEquals("WALLET", Registry.author(listOf("WALLET")))
        assertEquals("WALLET", Registry.author(listOf("WALLET", "OTHER")))      // only the relayer hands authorship on
    }
}
