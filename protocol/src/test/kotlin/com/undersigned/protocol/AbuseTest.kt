package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Abusive language is caught through disguise, honest words pass, matching text is hidden automatically, and a moderator's ban excludes a wallet. */
class AbuseTest {
    private fun sig(c: Char) = c.toString().repeat(87)
    private val mod = Moderation.MODERATORS.first()
    private val troll = "T".repeat(44); private val other = "O".repeat(44)

    @Test fun `catches slurs and threats through case, leetspeak, spacing and repeats, and lets honest words through`() {
        for (bad in listOf("This app is SHIT", "f*ck this wallet", "F U C K off", "fuuuuck", "what a r3tard", "kill yourself dev", "KYS", "n1gger"))
            assertTrue(Abuse.blocked(bad), bad)
        for (ok in listOf("Scunthorpe council app, works", "a fair assessment of the fees", "shiitake mushrooms", "Swaps settle fast and the wallet hand-offs just work", "class action", "the dev passed my test"))
            assertFalse(Abuse.blocked(ok), ok)
        assertEquals(listOf("shit"), Abuse.match("This app is SHIT"))
    }

    @Test fun `own tags cannot carry a listed word`() {
        assertFalse(ReviewMemo.validCustomTag("shit-app")); assertTrue(ReviewMemo.validCustomTag("offline-maps"))
    }

    @Test fun `matching text is hidden automatically with the reason abuse and the rating still counts`() {
        val e = listOf(LedgerEntry(sig('a'), other, 100, ReviewMemo.Review("com.x", 1, listOf(ReviewMemo.Tag.CRASHES), 1, "the dev is a fucking idiot")),
                       LedgerEntry(sig('b'), troll, 101, ReviewMemo.Review("com.x", 4, listOf(ReviewMemo.Tag.WORKS), 1, "works for me")))
        val hidden = Moderation.textHidden(e)
        assertEquals(mapOf(sig('a') to ReviewMemo.ModReason.ABUSE), hidden)
        assertEquals(2, Ranking.score(e) { SignerFacts(true, 0.0) }.single().countedReviews)
    }

    @Test fun `ban and unban round-trip, a moderator ban excludes the wallet, the later record wins, keepers cannot be banned`() {
        val ban = ReviewMemo.Ban(troll, ReviewMemo.ModReason.ABUSE)
        assertEquals("SRL1|ban|$troll|abuse", ReviewMemo.encode(ban)); assertEquals(ban, ReviewMemo.decode(ReviewMemo.encode(ban)))
        assertEquals(ReviewMemo.Unban(troll), ReviewMemo.decode("SRL1|unb|$troll")); assertNull(ReviewMemo.decode("SRL1|ban|short|abuse"))
        val review = LedgerEntry(sig('r'), troll, 100, ReviewMemo.Review("com.x", 1, listOf(ReviewMemo.Tag.CRASHES), 1, null))
        val e1 = listOf(review, LedgerEntry(sig('m'), mod, 200, ban))
        assertEquals(setOf(troll), Moderation.banned(e1)); assertEquals(listOf(e1[1]), Moderation.excludeBanned(e1))
        val e2 = e1 + LedgerEntry(sig('u'), mod, 300, ReviewMemo.Unban(troll))
        assertTrue(Moderation.banned(e2).isEmpty()); assertEquals(3, Moderation.excludeBanned(e2).size)
        val e3 = listOf(LedgerEntry(sig('x'), other, 100, ReviewMemo.Ban(troll, ReviewMemo.ModReason.ABUSE)))   // not a moderator
        assertTrue(Moderation.banned(e3).isEmpty())
        val e4 = listOf(LedgerEntry(sig('y'), mod, 100, ReviewMemo.Ban(mod, ReviewMemo.ModReason.ABUSE)))
        assertTrue(Moderation.banned(e4).isEmpty(), "a moderator is never banned")
    }

    @Test fun maskedForReaders() {
        assertEquals("sh*t", Abuse.mask("shit")); assertEquals("f*cking", Abuse.mask("fucking"))
        assertEquals(listOf("sh*t", "f*cking"), Abuse.masked("This app is sh1t and the dev is f*cking slow"))
    }

    @Test fun namesWithDoubledLettersAreNotSlurs() {
        assertFalse(Abuse.blocked("Jaap is the same person"))
        assertEquals(listOf("fuck"), Abuse.match("fuuuck this"))
    }
}
