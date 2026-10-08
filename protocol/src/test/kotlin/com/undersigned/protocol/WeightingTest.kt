package com.undersigned.protocol

import com.undersigned.protocol.ReviewMemo.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Weight = device × quality, the same in every score and never touched by payment. Pro and promoted reviews appear, at that
 * same weight, in their own labelled segment scores beside the signed score (compliance check, 9 Oct 2026). Properties:
 * paying never changes the signed score; segments only hold what was really paid; traders cannot buy into them.
 */
class WeightingTest {
    private fun id(c: Char) = "1" + c.toString().repeat(86)
    private val pkg = "com.example.app"
    private val attested = { _: String -> SignerFacts(true, 0.0) }
    private val words6 = "Swaps settle fast and hand-offs just work"
    private fun rev(c: Char, who: String, t: Long, rating: Int, text: String? = null) = LedgerEntry(id(c), who, t, ReviewMemo.Review(pkg, rating, listOf(Tag.WORKS), 1, text))
    private fun use(c: Char, who: String, t: Long) = LedgerEntry(id(c), who, t, ReviewMemo.Use(pkg, UseBucket.WEEKS))
    private fun pro(c: Char, who: String, t: Long) = LedgerEntry(id(c), who, t, ReviewMemo.Pro(12))
    private fun boost(c: Char, who: String, t: Long, target: String) = LedgerEntry(id(c), who, t, ReviewMemo.Boost(target))
    private fun close(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-9, "$a != $b")
    private val YEAR = 365L * 86_400L

    @Test fun `boost record round-trips and rejects a bad id`() {
        val b = ReviewMemo.Boost("${id('A')}:1")
        assertEquals(b, ReviewMemo.decode(ReviewMemo.encode(b)))
        assertNull(ReviewMemo.decode("SRL1|bst|not-a-signature"))
    }

    @Test fun `quality - bare review counts half, phone facts and a written note count fully, a hidden history halves it`() {
        val r = ReviewMemo.Review(pkg, 5, listOf(Tag.WORKS), 1, null)
        close(0.5, Weighting.quality(r, hasPhoneFact = false, penalised = false))
        close(0.75, Weighting.quality(r, hasPhoneFact = true, penalised = false))
        close(1.0, Weighting.quality(r.copy(text = words6), hasPhoneFact = true, penalised = false))
        close(0.75, Weighting.quality(r.copy(text = "too short note"), hasPhoneFact = true, penalised = false))
        close(0.25, Weighting.quality(r, hasPhoneFact = false, penalised = true))
        close(0.5, Weighting.quality(r.copy(text = words6), hasPhoneFact = true, penalised = true))
    }

    @Test fun `a full review outweighs a bare one in the score`() {
        val entries = listOf(rev('A', "spam", 10, 5), rev('B', "owner", 10, 1, words6), use('C', "owner", 10))
        close((5 * 0.5 + 1 * 1.0) / 1.5, Ranking.score(entries, facts = attested).single().weightedMean)
    }

    private val market = listOf(
        pro('P', "member", 100), rev('A', "member", 200, 5, words6), use('U', "member", 200), boost('X', "member", 300, id('A')),
        rev('B', "other", 200, 1, words6), use('V', "other", 200),
    )

    @Test fun `paying never changes the signed score`() {
        val unpaid = Ranking.score(market, paid = { false }, facts = attested).single()
        val paid = Ranking.score(market, paid = { true }, facts = attested).single()
        close(3.0, unpaid.weightedMean); close(unpaid.weightedMean, paid.weightedMean); assertEquals(2, paid.countedReviews)
    }

    @Test fun `segments hold exactly the Pro and promoted reviews, at their ordinary weight`() {
        val s = Ranking.segments(market, emptyMap(), { true }, attested)
        assertEquals(1, s.pro.getValue(pkg).countedReviews); close(5.0, s.pro.getValue(pkg).weightedMean)
        assertEquals(1, s.promoted.getValue(pkg).countedReviews)
        val none = Ranking.segments(market, emptyMap(), { false }, attested)
        assertTrue(none.pro.isEmpty() && none.promoted.isEmpty())
    }

    @Test fun `Pro counts only for reviews signed during the term`() {
        val late = listOf(pro('P', "member", 100), rev('A', "member", 100 + YEAR + 10, 5, words6))
        assertTrue(Ranking.segments(late, emptyMap(), { true }, attested).pro.isEmpty())
    }

    @Test fun `a promotion counts only when paid, by the review's own wallet, while it held Pro`() {
        val r = rev('A', "member", 200, 5, words6)
        fun promoted(extra: List<LedgerEntry>, paid: (LedgerEntry) -> Boolean = { true }) =
            Weighting.Context(listOf(pro('P', "member", 100), r) + extra, emptySet(), attested, paid).parts(r).promoted
        assertTrue(promoted(listOf(boost('X', "member", 300, r.signature))))
        assertFalse(promoted(listOf(boost('X', "someone", 300, r.signature))))
        assertFalse(promoted(listOf(boost('X', "member", 300, r.signature)), paid = { it.memo is ReviewMemo.Pro }))
        assertFalse(promoted(listOf(boost('X', "member", 100 + YEAR + 10, r.signature))))
        assertFalse(Weighting.Context(listOf(r, boost('X', "member", 300, r.signature)), emptySet(), attested, { true }).parts(r).promoted)
    }

    @Test fun `traders cannot buy into the segments`() {
        val publisher = "publisher"; val fan = "fan"
        val vp = mapOf(pkg to setOf(publisher))
        // the app's publisher pays for Pro and seats a reviewer: that seat does not make a Pro reviewer of the publisher's app
        val grant = LedgerEntry(id('G'), publisher, 150, ReviewMemo.Share(ProSharing.tag(publisher, fan)))
        val r = rev('A', fan, 200, 5, words6)
        val seeded = listOf(pro('P', publisher, 100), grant, r)
        assertFalse(Weighting.Context(seeded, emptySet(), attested, { true }, vp).parts(r).pro)
        assertTrue(Weighting.Context(seeded, emptySet(), attested, { true }).parts(r).pro)   // the same seat counts elsewhere
        // a verified publisher of ANY app cannot promote a review, even its own
        val other = mapOf("com.other.app" to setOf("member"))
        val own = rev('B', "member", 200, 1, words6)
        val all = listOf(pro('P', "member", 100), own, boost('X', "member", 300, own.signature))
        assertFalse(Weighting.Context(all, emptySet(), attested, { true }, other).parts(own).promoted)
    }

    @Test fun `a shared Pro seat counts, a grant signed after the review does not reach back`() {
        val payer = "payer"; val seat = "seat"
        val grant = LedgerEntry(id('G'), payer, 150, ReviewMemo.Share(ProSharing.tag(payer, seat)))
        val r = rev('A', seat, 200, 5, words6)
        assertTrue(Weighting.Context(listOf(pro('P', payer, 100), grant, r), emptySet(), attested, { true }).parts(r).pro)
        assertFalse(Weighting.Context(listOf(pro('P', payer, 100), grant.copy(blockTime = 250), r), emptySet(), attested, { true }).parts(r).pro)
    }

    @Test fun `payment never touches flags or helpful votes`() {
        val target = rev('A', "author", 10, 3, words6)
        val flag = LedgerEntry(id('F'), "member", 20, ReviewMemo.Flag(target.signature, ReviewMemo.FlagReason.SPAM))
        val vote = LedgerEntry(id('V'), "member", 20, ReviewMemo.Upvote(target.signature))
        val withPro = listOf(pro('P', "member", 5), target, flag, vote)
        val without = listOf(target, flag, vote)
        assertEquals(Reputation.flagWeights(without, emptyMap(), attested), Reputation.flagWeights(withPro, emptyMap(), attested))
        assertEquals(Reputation.helpfulWeights(without, attested), Reputation.helpfulWeights(withPro, attested))
    }

    @Test fun `an uncounted wallet stays out of every score whatever it pays`() {
        val r = rev('A', "nogenesis", 200, 5, words6)
        val all = listOf(pro('P', "nogenesis", 100), r, boost('X', "nogenesis", 300, r.signature))
        val none = { _: String -> SignerFacts(false, 0.0) }
        assertTrue(Ranking.score(all, facts = none).all { it.countedReviews == 0 })
        val s = Ranking.segments(all, emptyMap(), { true }, none)
        assertTrue(s.pro.isEmpty() && s.promoted.isEmpty())
    }
}
