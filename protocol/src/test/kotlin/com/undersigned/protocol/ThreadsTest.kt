package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The second round: a publisher's fix notice, the reviewer's follow-up, the star change, and the publisher's record. */
class ThreadsTest {
    private fun sig(c: Char) = c.toString().repeat(87)
    private val pub = "PUB"; private val rev = "REV"
    private val verified = mapOf("com.a.b" to setOf(pub))
    private fun review(id: String, signer: String, t: Long, stars: Int, pkg: String = "com.a.b") = LedgerEntry(id, signer, t, ReviewMemo.Review(pkg, stars, listOf(ReviewMemo.Tag.CRASHES), 3, null))

    @Test fun recordsRoundTripAndRejectBadInput() {
        val fix = ReviewMemo.Fixed(sig('a'), 42, "Fixed the crash on launch")
        assertEquals(fix, ReviewMemo.decode(ReviewMemo.encode(fix)))
        assertEquals(ReviewMemo.Fixed(sig('a'), 42, null), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Fixed(sig('a'), 42, "  "))))
        assertEquals(ReviewMemo.FollowUp(sig('b')), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.FollowUp(sig('b')))))
        assertNull(ReviewMemo.decode("SRL1|fix|${sig('a')}|0|"))
        assertNull(ReviewMemo.decode("SRL1|fix|${sig('a')}|x|"))
        assertNull(ReviewMemo.decode("SRL1|fup|short"))
    }

    @Test fun aFollowUpAfterAFixShowsBothStarsAndTheChange() {
        val first = review(sig('r'), rev, 100, 2)
        val fix = LedgerEntry(sig('f'), pub, 200, ReviewMemo.Fixed(sig('r'), 43, "Fixed in 1.4"))
        val second = review(sig('s') + ":0", rev, 300, 5)
        val link = LedgerEntry(sig('s') + ":1", rev, 300, ReviewMemo.FollowUp(sig('r')))
        val rounds = Threads.rounds(listOf(first, fix, second, link), verified)
        val r = rounds.getValue(second.signature)
        assertEquals(2, r.before); assertEquals(5, r.after); assertEquals(3, r.delta); assertEquals(43, r.fix?.versionCode)
        val st = Threads.stats(listOf(first, fix, second, link), "com.a.b", verified)
        assertEquals(1, st.fixes); assertEquals(1, st.followUpsAfterFix); assertEquals(3.0, st.meanDelta); assertEquals(100 / 3600.0, st.medianHoursToFix!!, 1e-9)
    }

    @Test fun fixesFromOthersOrBeforeTheReviewAndForeignFollowUpsDoNotCount() {
        val first = review(sig('r'), rev, 100, 2)
        val strangerFix = LedgerEntry(sig('x'), "STRANGER", 200, ReviewMemo.Fixed(sig('r'), 43, null))
        val earlyFix = LedgerEntry(sig('y'), pub, 50, ReviewMemo.Fixed(sig('r'), 43, null))
        assertTrue(Threads.fixes(listOf(first, strangerFix, earlyFix), verified).isEmpty())
        // Someone else's review cannot be a follow-up to it, nor a review of another app, nor one in another transaction.
        val other = review(sig('o') + ":0", "OTHER", 300, 5); val otherLink = LedgerEntry(sig('o') + ":1", "OTHER", 300, ReviewMemo.FollowUp(sig('r')))
        val wrongApp = review(sig('w') + ":0", rev, 300, 5, "com.x.y"); val wrongLink = LedgerEntry(sig('w') + ":1", rev, 300, ReviewMemo.FollowUp(sig('r')))
        val alone = LedgerEntry(sig('z') + ":0", rev, 300, ReviewMemo.FollowUp(sig('r')))
        assertTrue(Threads.rounds(listOf(first, other, otherLink, wrongApp, wrongLink, alone), verified).isEmpty())
    }
}
