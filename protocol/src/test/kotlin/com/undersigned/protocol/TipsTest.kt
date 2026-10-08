package com.undersigned.protocol

import com.undersigned.protocol.ReviewMemo.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tips go straight to the app's proven publisher wallet. Properties: a tip counts only to a PROVEN publisher, only when the
 * transaction really credited it, never from the publisher or an operator wallet; a tipper's review is never weighted up and
 * never moves the signed score; it shows in its own labelled score.
 */
class TipsTest {
    private fun id(c: Char) = "1" + c.toString().repeat(86)
    private val pkg = "com.example.app"
    private val dev = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val other = "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"
    private val vp = mapOf(pkg to setOf(dev))
    private val attested = { _: String -> SignerFacts(true, 0.0) }
    private val words6 = "Swaps settle fast and hand-offs just work"
    private fun tip(c: Char, who: String, to: String = dev) = LedgerEntry(id(c), who, 100, ReviewMemo.Tip(pkg, to))
    private fun rev(c: Char, who: String, rating: Int) = LedgerEntry(id(c), who, 200, ReviewMemo.Review(pkg, rating, listOf(Tag.WORKS), 1, words6))
    private val sol = { _: LedgerEntry -> Tips.Credit(10_000_000L, 0L) }
    private fun close(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-9, "$a != $b")

    @Test fun `tip record round-trips and rejects a bad recipient`() {
        val t = ReviewMemo.Tip(pkg, dev)
        assertEquals(t, ReviewMemo.decode(ReviewMemo.encode(t)))
        assertNull(ReviewMemo.decode("SRL1|tip|$pkg|not-an-address"))
    }

    @Test fun `a tip counts only to a proven publisher, only when credited, never from the publisher or an operator`() {
        assertEquals(1, Tips.tips(listOf(tip('A', "fan")), vp, sol).size)
        assertTrue(Tips.tips(listOf(tip('A', "fan", to = other)), vp, sol).isEmpty())                     // not a proven publisher
        assertTrue(Tips.tips(listOf(tip('A', "fan")), emptyMap(), sol).isEmpty())                         // claimed, not proven
        assertTrue(Tips.tips(listOf(tip('A', "fan")), vp) { Tips.Credit(999_999L, 0L) }.isEmpty())        // under 0.001 SOL
        assertTrue(Tips.tips(listOf(tip('A', "fan")), vp) { null }.isEmpty())                             // credit unknown
        assertEquals(1, Tips.tips(listOf(tip('A', "fan")), vp) { Tips.Credit(0L, 1_000_000L) }.size)      // 1 SKR
        assertTrue(Tips.tips(listOf(tip('A', dev)), vp, sol).isEmpty())                                   // the publisher tipping itself
        assertTrue(Tips.tips(listOf(tip('A', Operator.WALLETS.first())), vp, sol).isEmpty())
    }

    @Test fun `tipping never moves the signed score and shows in its own score`() {
        val entries = listOf(tip('T', "fan"), rev('A', "fan", 5), rev('B', "other", 1))
        val tippers = Tips.tippers(Tips.tips(entries, vp, sol))
        val signed = Ranking.score(entries, vp, facts = attested).single()
        val without = Ranking.score(entries.drop(1), vp, facts = attested).single()
        close(without.weightedMean, signed.weightedMean); assertEquals(2, signed.countedReviews)
        val seg = Ranking.segments(entries, vp, { false }, attested, tippers).tipped.getValue(pkg)
        assertEquals(1, seg.countedReviews); close(5.0, seg.weightedMean)
    }

    @Test fun `totals count tips and distinct tippers per app`() {
        val t = Tips.tips(listOf(tip('A', "fan"), tip('B', "fan"), tip('C', "x")), vp, sol)
        val tot = Tips.totals(t).getValue(pkg)
        assertEquals(3, tot.tips); assertEquals(2, tot.tippers); assertEquals(30_000_000L, tot.lamports)
    }
}
