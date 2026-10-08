package com.undersigned.protocol

import com.undersigned.protocol.ReviewMemo.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Review weight = device × quality × paid (owner, 8 Oct 2026). The properties that must hold: a bare review weighs less than
 * one that carries what the phone saw and a written note; paid weight only exists when the payment really happened, only
 * on top of Pro, never past ×1.5; and payment never changes the weight of anyone else's review, flag or vote.
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
        close(0.25, Weighting.quality(r, hasPhoneFact = false, penalised = true))   // floor
        close(0.5, Weighting.quality(r.copy(text = words6), hasPhoneFact = true, penalised = true))
    }

    @Test fun `paid weight - Pro 1_25, boost only on top of Pro, never past 1_5`() {
        close(1.0, Weighting.paid(pro = false, boosted = false))
        close(1.0, Weighting.paid(pro = false, boosted = true))
        close(1.25, Weighting.paid(pro = true, boosted = false))
        close(1.5, Weighting.paid(pro = true, boosted = true))
        assertTrue(Weighting.PRO_BONUS * Weighting.BOOST_BONUS <= Weighting.MAX_PAID + 1e-9)
    }

    @Test fun `a full review outweighs a bare one in the score`() {
        // bare 5-star (0.5) and full 1-star with phone fact and note (1.0): mean = (5*0.5 + 1*1.0) / 1.5
        val entries = listOf(rev('A', "spam", 10, 5), rev('B', "owner", 10, 1, words6), use('C', "owner", 10))
        close((5 * 0.5 + 1 * 1.0) / 1.5, Ranking.score(entries, facts = attested).single().weightedMean)
    }

    @Test fun `Pro adds weight only when the payment is real and only for reviews signed during the term`() {
        val entries = listOf(pro('P', "member", 100), rev('A', "member", 200, 5, words6), use('U', "member", 200), rev('B', "other", 200, 1, words6), use('V', "other", 200))
        // unpaid: nothing changes, the two full reviews weigh the same
        close(3.0, Ranking.score(entries, paid = { false }, facts = attested).single().weightedMean)
        // paid: member counts 1.25
        close((5 * 1.25 + 1 * 1.0) / 2.25, Ranking.score(entries, paid = { true }, facts = attested).single().weightedMean)
        // a review signed after the term ended gets no bonus
        val late = listOf(pro('P', "member", 100), rev('A', "member", 100 + YEAR + 10, 5, words6), use('U', "member", 200), rev('B', "other", 200, 1, words6), use('V', "other", 200))
        close(3.0, Ranking.score(late, paid = { true }, facts = attested).single().weightedMean)
    }

    @Test fun `a boost counts only when paid, by the review's own wallet, while it held Pro`() {
        val r = rev('A', "member", 200, 5, words6)
        fun parts(extra: List<LedgerEntry>, paid: (LedgerEntry) -> Boolean = { true }): Weighting.Parts {
            val all = listOf(pro('P', "member", 100), r, use('U', "member", 200)) + extra
            return Weighting.Context(all, emptySet(), attested, paid).parts(r)
        }
        close(1.25, parts(emptyList()).paid)
        close(1.5, parts(listOf(boost('X', "member", 300, r.signature))).paid)
        assertTrue(parts(listOf(boost('X', "member", 300, r.signature))).boosted)
        close(1.25, parts(listOf(boost('X', "publisher", 300, r.signature))).paid)                     // someone else's boost
        close(1.25, parts(listOf(boost('X', "member", 300, r.signature)), paid = { it.memo is ReviewMemo.Pro }).paid)   // unpaid boost
        close(1.25, parts(listOf(boost('X', "member", 100 + YEAR + 10, r.signature))).paid)           // boosted after Pro lapsed
        // without Pro at all, a paid boost adds nothing
        val bare = listOf(r, use('U', "member", 200), boost('X', "member", 300, r.signature))
        close(1.0, Weighting.Context(bare, emptySet(), attested, { true }).parts(r).paid)
    }

    @Test fun `a shared Pro seat carries the bonus, a seat without the payer's term does not`() {
        val payer = "payer"; val seat = "seat"
        val grant = LedgerEntry(id('G'), payer, 150, ReviewMemo.Share(ProSharing.tag(payer, seat)))
        val r = rev('A', seat, 200, 5, words6)
        close(1.25, Weighting.Context(listOf(pro('P', payer, 100), grant, r), emptySet(), attested, { true }).parts(r).paid)
        close(1.0, Weighting.Context(listOf(pro('P', payer, 100), grant, r), emptySet(), attested, { false }).parts(r).paid)
        // a grant signed after the review does not reach back
        val lateGrant = grant.copy(blockTime = 250)
        close(1.0, Weighting.Context(listOf(pro('P', payer, 100), lateGrant, r), emptySet(), attested, { true }).parts(r).paid)
    }

    @Test fun `paid weight never touches flags or helpful votes`() {
        val target = rev('A', "author", 10, 3, words6)
        val flag = LedgerEntry(id('F'), "member", 20, ReviewMemo.Flag(target.signature, ReviewMemo.FlagReason.values().first()))
        val vote = LedgerEntry(id('V'), "member", 20, ReviewMemo.Upvote(target.signature))
        val withPro = listOf(pro('P', "member", 5), target, flag, vote)
        val without = listOf(target, flag, vote)
        assertEquals(Reputation.flagWeights(without, emptyMap(), attested), Reputation.flagWeights(withPro, emptyMap(), attested))
        assertEquals(Reputation.helpfulWeights(without, attested), Reputation.helpfulWeights(withPro, attested))
    }

    @Test fun `the signed score never includes payment, the member-weighted score does`() {
        val entries = listOf(pro('P', "member", 100), rev('A', "member", 200, 5, words6), use('U', "member", 200), rev('B', "other", 200, 1, words6), use('V', "other", 200))
        val signed = Ranking.score(entries, facts = attested).single()                      // default: no paid weight
        val member = Ranking.score(entries, paid = { true }, facts = attested).single()
        close(3.0, signed.weightedMean)
        assertTrue(member.weightedMean > signed.weightedMean)
        val r = entries[1]; val w = Weighting.Context(entries, emptySet(), attested, { true }).parts(r)
        close(w.device * w.quality, w.signed); close(w.signed * w.paid, w.total)
    }

    @Test fun `an uncounted wallet stays at zero whatever it pays`() {
        val r = rev('A', "nogenesis", 200, 5, words6)
        val all = listOf(pro('P', "nogenesis", 100), r, boost('X', "nogenesis", 300, r.signature))
        close(0.0, Weighting.Context(all, emptySet(), { SignerFacts(false, 0.0) }, { true }).parts(r).total)
        assertTrue(Ranking.score(all, paid = { true }, facts = { SignerFacts(false, 0.0) }).all { it.countedReviews == 0 })
    }
}
