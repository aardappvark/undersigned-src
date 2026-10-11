package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reviewer's record beside each review: apps reviewed, follow-ups, follow-ups after a fix, fixes confirmed. Counts, never a weight. */
class StandingTest {
    private fun sig(c: Char) = c.toString().repeat(87)

    @Test fun `follow-ups after a publisher's fix are counted, and a better second rating confirms the fix`() {
        val alice = "A".repeat(44); val pub = "P".repeat(44)
        val entries = listOf(
            LedgerEntry(sig('a'), alice, 100, ReviewMemo.Review("com.x", 2, listOf(ReviewMemo.Tag.CRASHES), 1, null)),
            LedgerEntry(sig('b'), alice, 110, ReviewMemo.Review("com.y", 4, listOf(ReviewMemo.Tag.WORKS), 1, null)),
            LedgerEntry(sig('f'), pub, 200, ReviewMemo.Fixed(sig('a'), 2, "fixed")),
            LedgerEntry(sig('c'), alice, 300, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS), 2, null)),
            LedgerEntry(sig('c') + ":1", alice, 300, ReviewMemo.FollowUp(sig('a'))),
        )
        val verified = mapOf("com.x" to setOf(pub))
        val st = Reputation.standing(entries, verified) { SignerFacts(true, 0.0) }[alice]!!
        assertEquals(2, st.appsReviewed)
        assertEquals(1, st.followUps); assertEquals(1, st.followUpsAfterFix); assertEquals(1, st.fixesConfirmed)
        // Without the publisher's proof the fix notice is not a fix: the follow-up still counts, "after a fix" does not.
        val noProof = Reputation.standing(entries, emptyMap()) { SignerFacts(true, 0.0) }[alice]!!
        assertEquals(1, noProof.followUps); assertEquals(0, noProof.followUpsAfterFix); assertEquals(0, noProof.fixesConfirmed)
    }
}
