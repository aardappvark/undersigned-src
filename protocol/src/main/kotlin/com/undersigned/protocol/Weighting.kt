package com.undersigned.protocol

import kotlin.math.max
import kotlin.math.min

/**
 * What one counted review weighs in an app's signed score (owner, 8 Oct 2026: low-value reviews should not weigh the same,
 * and a Pro member, and a boost on top of Pro, should count a little more, as long as it is shown). Three factors, every
 * one read from the chain and shown on the review, so any reader reaches the same number:
 *
 *  - **device** ([Ranking.weight]): the Genesis Token gate and staked SKR, capped at 3×. Unchanged.
 *  - **quality**, earned, between [QUALITY_FLOOR] and 1.0: [QUALITY_BASE] for stars and tags alone; +[QUALITY_FACT] when
 *    the review carries what the phone saw (install age or usage time); +[QUALITY_TEXT] for a written note of
 *    [TEXT_WORDS]+ words; halved for a wallet that has a review hidden by consensus. Low-value reviews sink by themselves.
 *  - **paid**, capped at [MAX_PAID]: ×[PRO_BONUS] when the wallet held Pro at the time it signed the review, and
 *    ×[BOOST_BONUS] more for a boost the same wallet paid for on that review while holding Pro.
 *
 * Two scores come out of this (owner, 8 Oct 2026: "run two simultaneous scores ... for users to decide themselves"):
 * the SIGNED score uses device × quality and never includes payment; the MEMBER-WEIGHTED score, shown beside it and
 * labelled, adds the paid factor. Flags, "helpful" votes and consensus hiding use the device weight alone: money can lift
 * a member's own voice a little in the member-weighted view; it can never quiet anyone else's.
 */
object Weighting {
    const val QUALITY_BASE = 0.5
    const val QUALITY_FACT = 0.25
    const val QUALITY_TEXT = 0.25
    const val QUALITY_FLOOR = 0.25
    const val TEXT_WORDS = 6
    const val PRO_BONUS = 1.25
    const val BOOST_BONUS = 1.2
    const val MAX_PAID = 1.5

    data class Parts(val device: Double, val quality: Double, val paid: Double, val pro: Boolean, val boosted: Boolean) {
        /** The weight in the SIGNED score: device × quality. Payment never enters it. */
        val signed: Double get() = device * quality
        /** The weight in the MEMBER-WEIGHTED score, shown beside the signed one: signed × paid. */
        val total: Double get() = device * quality * paid
    }

    fun words(text: String?): Int = text?.trim()?.split(Regex("\\s+"))?.count { it.any(Char::isLetterOrDigit) } ?: 0

    fun quality(review: ReviewMemo.Review, hasPhoneFact: Boolean, penalised: Boolean): Double {
        var q = QUALITY_BASE
        if (hasPhoneFact) q += QUALITY_FACT
        if (words(review.text) >= TEXT_WORDS) q += QUALITY_TEXT
        if (penalised) q *= 0.5
        return max(QUALITY_FLOOR, min(1.0, q))
    }

    /** A boost counts only on top of Pro, and the two together never pass [MAX_PAID]. */
    fun paid(pro: Boolean, boosted: Boolean): Double =
        min(MAX_PAID, (if (pro) PRO_BONUS else 1.0) * (if (pro && boosted) BOOST_BONUS else 1.0))

    private const val DAY = 86_400L

    /** The wallet's own paid Pro terms as [start, end) intervals: a renewal signed inside a term starts when that term ends. */
    fun proTerms(entries: List<LedgerEntry>, wallet: String, paid: (LedgerEntry) -> Boolean): List<LongRange> {
        var expiry = 0L
        return entries.filter { it.signer == wallet && it.memo is ReviewMemo.Pro && paid(it) }.sortedBy { it.blockTime }.map { e ->
            val start = max(expiry, e.blockTime)
            expiry = start + (e.memo as ReviewMemo.Pro).months * 365L * DAY / 12L
            start until expiry
        }
    }

    /**
     * Pro at [time]: a term [wallet] paid for, or a seat a Pro payer had granted it by then (ProSharing). A grant counts
     * only while the granter's own term is running, exactly as the app's licence reader decides it.
     */
    fun proAt(entries: List<LedgerEntry>, wallet: String, time: Long, paid: (LedgerEntry) -> Boolean): Boolean {
        if (proTerms(entries, wallet, paid).any { time in it }) return true
        val payers = entries.filter { it.memo is ReviewMemo.Pro && it.signer != wallet && paid(it) }.map { it.signer }.toSet()
        return payers.any { g ->
            proTerms(entries, g, paid).any { time in it } &&
                ProSharing.holdsSeat(g, wallet, ProSharing.recordsOf(entries, g).filter { it.time <= time })
        }
    }

    /**
     * Everything a review's weight needs, read once per ledger. [paid] says whether a `pro` or `bst` record's transaction
     * credited the treasury with at least its floor; each reader supplies its own reading of the balances.
     */
    class Context(
        private val entries: List<LedgerEntry>,
        hidden: Set<String>,
        private val facts: (String) -> SignerFacts,
        private val paid: (LedgerEntry) -> Boolean,
    ) {
        private val penalised: Set<String> = entries.filter { it.signature in hidden && it.memo is ReviewMemo.Review }.map { it.signer }.toSet()
        private val phoneFacts: Set<Pair<String, String>> = entries.mapNotNull { e ->
            when (val m = e.memo) { is ReviewMemo.Use -> e.signer to m.packageName; is ReviewMemo.Time -> e.signer to m.packageName; else -> null }
        }.toSet()
        private val bySig = entries.associateBy { it.signature }
        private val proCache = HashMap<Pair<String, Long>, Boolean>()
        private fun pro(wallet: String, time: Long) = proCache.getOrPut(wallet to time) { proAt(entries, wallet, time, paid) }

        /** Review ids with a valid boost: paid, signed by the review's own wallet, while that wallet held Pro. */
        val boosted: Set<String> = entries.mapNotNull { e ->
            val b = e.memo as? ReviewMemo.Boost ?: return@mapNotNull null
            val review = bySig[b.recordId]?.takeIf { it.memo is ReviewMemo.Review } ?: return@mapNotNull null
            b.recordId.takeIf { review.signer == e.signer && paid(e) && pro(e.signer, e.blockTime) }
        }.toSet()

        fun parts(e: LedgerEntry): Parts {
            val r = e.memo as ReviewMemo.Review
            val device = Ranking.weight(facts(e.signer))
            val isPro = pro(e.signer, e.blockTime)
            val isBoosted = e.signature in boosted
            return Parts(device, quality(r, (e.signer to r.packageName) in phoneFacts, e.signer in penalised), paid(isPro, isBoosted), isPro, isBoosted)
        }
    }
}
