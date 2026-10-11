package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Moderation hides text only, only from listed wallets, the latest record per moderator wins, never on their own app. */
class ModerationTest {
    private val mod = "M0D"; private val other = "nobody"
    private fun id(c: Char) = "1" + c.toString().repeat(86)
    private val review = LedgerEntry(id('A'), "alice", 1, ReviewMemo.Review("com.x", 2, listOf(ReviewMemo.Tag.WORKS), 1, "my phone number is ..."))
    private val reply = LedgerEntry(id('B'), "bob", 2, ReviewMemo.Reply(id('A'), "abuse"))
    private fun hide(sig: Char, t: Long, target: String, by: String = mod) = LedgerEntry(id(sig), by, t, ReviewMemo.Moderate(target, ReviewMemo.ModReason.PERSONAL))
    private fun undo(sig: Char, t: Long, target: String, by: String = mod) = LedgerEntry(id(sig), by, t, ReviewMemo.Unmoderate(target))

    @Test fun `wire round trip, and garbage is rejected`() {
        val m = ReviewMemo.Moderate(id('A'), ReviewMemo.ModReason.ABUSE)
        assertEquals(m, ReviewMemo.decode(ReviewMemo.encode(m)))
        assertEquals(ReviewMemo.Unmoderate(id('A')), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Unmoderate(id('A')))))
        assertNull(ReviewMemo.decode("SRL1|mod|${id('A')}|because"))
        assertNull(ReviewMemo.decode("SRL1|mod|notanid|abuse"))
    }

    @Test fun `only listed wallets hide, and the latest record of each wins`() {
        val e = listOf(review, reply, hide('C', 3, id('A')), hide('D', 3, id('B'), by = other))
        assertEquals(mapOf(id('A') to ReviewMemo.ModReason.PERSONAL), Moderation.textHidden(e, moderators = setOf(mod)))
        assertTrue(Moderation.textHidden(e + undo('E', 4, id('A')), moderators = setOf(mod)).isEmpty())
        assertEquals(setOf(id('A')), Moderation.textHidden(e + undo('E', 4, id('A')) + hide('F', 5, id('A')), moderators = setOf(mod)).keys)
    }

    @Test fun `a moderator who publishes the app cannot hide its reviews or their replies`() {
        val e = listOf(review, reply, hide('C', 3, id('A')), hide('D', 3, id('B')))
        assertTrue(Moderation.textHidden(e, mapOf("com.x" to setOf(mod)), setOf(mod)).isEmpty())
        assertEquals(2, Moderation.textHidden(e, mapOf("com.y" to setOf(mod)), setOf(mod)).size)
    }

    @Test fun `an operator wallet cannot hide reviews of any app the operator publishes, whichever wallet proved it`() {
        val opMod = Operator.WALLETS.first(); val otherOp = Operator.WALLETS.last()
        val e = listOf(review, reply, hide('C', 3, id('A'), by = opMod), hide('D', 3, id('B'), by = opMod))
        assertTrue(Moderation.textHidden(e, mapOf("com.x" to setOf(otherOp)), setOf(opMod)).isEmpty())   // proved by ANOTHER operator wallet
        assertEquals(2, Moderation.textHidden(e, mapOf("com.x" to setOf("someone")), setOf(opMod)).size) // someone else's app: allowed
    }

    @Test fun `the rating still counts when the text is hidden`() {
        val e = listOf(review, hide('C', 3, id('A')))
        val score = Ranking.score(e, emptyMap()) { SignerFacts(true, 0.0) }.single()
        assertEquals(1, score.countedReviews); assertEquals(2.0, score.weightedMean)
    }

    @Test fun `a ban never removes the wallet's records about an app the banning moderator publishes`() {
        val ban = LedgerEntry(id('Z'), mod, 9, ReviewMemo.Ban("alice", ReviewMemo.ModReason.ABUSE))
        val otherApp = LedgerEntry(id('Y'), "alice", 1, ReviewMemo.Review("com.other", 1, listOf(ReviewMemo.Tag.CRASHES), 1, null))
        val e = listOf(review, reply, otherApp, ban)
        // No recusal: everything alice signed is dropped; bob's reply is his own record and stays, as before.
        assertEquals(listOf(id('B'), id('Z')), Moderation.excludeBanned(e, moderators = setOf(mod)).map { it.signature })
        // The moderator publishes com.x: alice's review of com.x (and its reply) stays; her review of com.other goes.
        val kept = Moderation.excludeBanned(e, moderators = setOf(mod), verifiedPublishers = mapOf("com.x" to setOf(mod))).map { it.signature }
        assertEquals(listOf(id('A'), id('B'), id('Z')), kept)
        assertEquals(mapOf("alice" to setOf(mod)), Moderation.bans(e, moderators = setOf(mod)))
    }
}
