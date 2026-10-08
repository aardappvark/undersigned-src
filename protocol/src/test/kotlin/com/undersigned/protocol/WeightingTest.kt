package com.undersigned.protocol

import com.undersigned.protocol.ReviewMemo.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Weight = device × quality, the same in every score and never touched by payment. Pro members' reviews appear, at that same
 * weight, in their own labelled score beside the signed score (compliance check, 9 Oct 2026). Properties: paying never
 * changes the signed score; the Pro segment only holds what was really paid; a seat gifted by the reviewed app's publisher
 * counts in no score; Undersigned's own wallets count in no score; a promotion is honoured only for the author's own
 * review, paid, while holding Pro, never by a publisher, operator or gifted seat, and never on the operator's own apps.
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
        pro('P', "member", 100), rev('A', "member", 200, 5, words6), use('U', "member", 200),
        rev('B', "other", 200, 1, words6), use('V', "other", 200),
    )

    @Test fun `paying never changes the signed score`() {
        val unpaid = Ranking.score(market, paid = { false }, facts = attested).single()
        val paid = Ranking.score(market, paid = { true }, facts = attested).single()
        close(3.0, unpaid.weightedMean); close(unpaid.weightedMean, paid.weightedMean); assertEquals(2, paid.countedReviews)
    }

    @Test fun `the Pro segment holds exactly the Pro members' reviews, at their ordinary weight`() {
        val s = Ranking.segments(market, emptyMap(), { true }, attested)
        assertEquals(1, s.pro.getValue(pkg).countedReviews); close(5.0, s.pro.getValue(pkg).weightedMean)
        assertTrue(Ranking.segments(market, emptyMap(), { false }, attested).pro.isEmpty())
    }

    @Test fun `Pro counts only for reviews signed during the term`() {
        val late = listOf(pro('P', "member", 100), rev('A', "member", 100 + YEAR + 10, 5, words6))
        assertTrue(Ranking.segments(late, emptyMap(), { true }, attested).pro.isEmpty())
    }

    @Test fun `a Pro seat gifted by the app's publisher is labelled and counted in no score`() {
        val publisher = "publisher"; val fan = "fan"
        val vp = mapOf(pkg to setOf(publisher))
        val grant = LedgerEntry(id('G'), publisher, 150, ReviewMemo.Share(ProSharing.tag(publisher, fan)))
        val r = rev('A', fan, 200, 5, words6)
        val seeded = listOf(pro('P', publisher, 100), grant, r, rev('B', "other", 200, 1, words6))
        val parts = Weighting.Context(seeded, emptySet(), attested, { true }, vp).parts(r)
        assertTrue(parts.gifted); assertFalse(parts.pro); close(0.0, parts.weight)
        val score = Ranking.score(seeded, vp, { true }, facts = attested).single()
        assertEquals(1, score.countedReviews); close(1.0, score.weightedMean)
        assertTrue(Ranking.segments(seeded, vp, { true }, attested).pro.isEmpty())
        // the same seat is an ordinary Pro seat for an app that publisher does not publish
        val elsewhere = Weighting.Context(seeded, emptySet(), attested, { true }).parts(r)
        assertTrue(elsewhere.pro); assertFalse(elsewhere.gifted)
    }

    @Test fun `Undersigned's own wallets are counted in no score, flag or vote`() {
        val op = Operator.WALLETS.first()
        val entries = listOf(rev('A', op, 200, 1, words6), use('U', op, 200), rev('B', "owner", 200, 5, words6))
        val s = Ranking.score(entries, facts = attested).single()
        assertEquals(1, s.countedReviews); close(5.0, s.weightedMean)
        val target = rev('C', "owner", 10, 3, words6)
        val flag = LedgerEntry(id('F'), op, 20, ReviewMemo.Flag(target.signature, ReviewMemo.FlagReason.SPAM))
        assertTrue(Reputation.flagWeights(listOf(target, flag), emptyMap(), Operator.gate(attested)).values.all { it == 0.0 })
        assertTrue(Operator.publishes(pkg, mapOf(pkg to setOf(op)))); assertFalse(Operator.publishes(pkg, mapOf(pkg to setOf("x"))))
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
        val all = listOf(pro('P', "nogenesis", 100), r)
        val none = { _: String -> SignerFacts(false, 0.0) }
        assertTrue(Ranking.score(all, facts = none).all { it.countedReviews == 0 })
        val s = Ranking.segments(all, emptyMap(), { true }, none)
        assertTrue(s.pro.isEmpty())
    }

    @Test fun `promotion record round-trips and rejects a bad id`() {
        val b = ReviewMemo.Boost("${id('A')}:1")
        assertEquals(b, ReviewMemo.decode(ReviewMemo.encode(b)))
        assertNull(ReviewMemo.decode("SRL1|bst|not-a-signature"))
    }

    private val promotedMarket = listOf(pro('P', "member", 100), rev('A', "member", 200, 5, words6), boost('X', "member", 300, id('A')), rev('B', "other", 200, 1, words6))

    @Test fun `a promotion never changes the signed score and sits in its own score`() {
        val with = Ranking.score(promotedMarket, paid = { true }, facts = attested).single()
        val without = Ranking.score(promotedMarket.filter { it.memo !is ReviewMemo.Boost }, paid = { true }, facts = attested).single()
        close(without.weightedMean, with.weightedMean); assertEquals(2, with.countedReviews)
        val seg = Ranking.segments(promotedMarket, emptyMap(), { true }, attested).promoted.getValue(pkg)
        assertEquals(1, seg.countedReviews); close(5.0, seg.weightedMean)
    }

    @Test fun `a promotion counts only when paid, by the review's own wallet, while it held Pro`() {
        val r = rev('A', "member", 200, 5, words6)
        fun promoted(extra: List<LedgerEntry>, paid: (LedgerEntry) -> Boolean = { true }, base: List<LedgerEntry> = listOf(pro('P', "member", 100), r)) =
            Weighting.Context(base + extra, emptySet(), attested, paid).parts(r).promoted
        assertTrue(promoted(listOf(boost('X', "member", 300, r.signature))))
        assertFalse(promoted(listOf(boost('X', "someone", 300, r.signature))))                                      // not the author
        assertFalse(promoted(listOf(boost('X', "member", 300, r.signature)), paid = { it.memo is ReviewMemo.Pro }))  // unpaid
        assertFalse(promoted(listOf(boost('X', "member", 100 + YEAR + 10, r.signature))))                            // after the term
        assertFalse(promoted(listOf(boost('X', "member", 300, r.signature)), base = listOf(r)))                      // never held Pro
    }

    @Test fun `publishers, operators, gifted seats and the operator's own apps cannot promote`() {
        val own = rev('B', "member", 200, 1, words6)
        val all = listOf(pro('P', "member", 100), own, boost('X', "member", 300, own.signature))
        // a verified publisher of ANY app
        assertFalse(Weighting.Context(all, emptySet(), attested, { true }, mapOf("com.other.app" to setOf("member"))).parts(own).promoted)
        // the operator publishes the reviewed app
        assertFalse(Weighting.Context(all, emptySet(), attested, { true }, mapOf(pkg to setOf(Operator.WALLETS.first()))).parts(own).promoted)
        // an operator wallet promoting its own review
        val op = Operator.WALLETS.first(); val opRev = rev('C', op, 200, 5, words6)
        assertFalse(Weighting.Context(listOf(pro('Q', op, 100), opRev, boost('Y', op, 300, opRev.signature)), emptySet(), attested, { true }).parts(opRev).promoted)
        // a Pro seat gifted by the app's publisher
        val grant = LedgerEntry(id('G'), "publisher", 150, ReviewMemo.Share(ProSharing.tag("publisher", "fan")))
        val fanRev = rev('D', "fan", 200, 5, words6)
        val gifted = listOf(pro('P', "publisher", 100), grant, fanRev, boost('Z', "fan", 300, fanRev.signature))
        assertFalse(Weighting.Context(gifted, emptySet(), attested, { true }, mapOf(pkg to setOf("publisher"))).parts(fanRev).promoted)
    }
}
