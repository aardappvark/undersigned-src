package com.undersigned.protocol

import kotlin.math.max
import kotlin.math.min

/**
 * What one counted review weighs, and whether it also appears in the separately labelled "Pro members" score.
 *
 * WEIGHT, never touched by payment to Undersigned:
 *  - **device** ([Ranking.weight]): the Genesis Token gate, 1 per attested device. Staked SKR is shown, never weighed.
 *  - **quality**, earned, between [QUALITY_FLOOR] and 1.0: [QUALITY_BASE] for stars and tags alone; +[QUALITY_FACT] when the
 *    review carries what the phone saw (install age or usage time); +[QUALITY_TEXT] for a written note of [TEXT_WORDS]+
 *    words; halved for a wallet that has a review hidden by consensus.
 *
 * PRO MEMBERS: reviews signed while the wallet held Pro appear, at the same weight, in their own labelled score with its
 * own count beside the signed score.
 *
 * PROMOTED (owner, 9 Oct 2026: "a promoted review which is separate to organic reviews"): the review's own author paid to
 * promote it (`bst`). It counts once, at the same weight, in the signed score, and is listed in its own section labelled
 * as paid, set apart from organic reviews and placed AFTER them, never first (UK DMCC 13(5)(i)(ii) / CMA208 5.1(b):
 * prominence must not follow payment; ACCC: promoted results shaded apart from organic ones; docs/COMPLIANCE_REVIEWS.md).
 * Honoured only when paid, signed by the review's own wallet while it held Pro, and never by a verified publisher of any
 * app, an Undersigned operator wallet, or a holder of a Pro seat gifted by the app's publisher; never on an app the
 * operator publishes (FTC 16 CFR 255.2(d): the operator would be promoting reviews of its own products).
 *
 * GIFTED (compliance review, 9 Oct 2026): a reviewer holding a Pro seat granted by the reviewed app's verified publisher
 * has received something of value from that trader; their review of that publisher's apps is labelled and counted in NO
 * score. Operator wallets are excluded separately ([Operator]).
 */
object Weighting {
    const val QUALITY_BASE = 0.5
    const val QUALITY_FACT = 0.25
    const val QUALITY_TEXT = 0.25
    const val QUALITY_FLOOR = 0.25
    const val TEXT_WORDS = 6

    data class Parts(val device: Double, val quality: Double, val pro: Boolean, val gifted: Boolean = false, val promoted: Boolean = false) {
        /** The review's weight in every score it appears in; 0 when it is gifted (counted nowhere). */
        val weight: Double get() = if (gifted) 0.0 else device * quality
    }

    fun words(text: String?): Int = text?.trim()?.split(Regex("\\s+"))?.count { it.any(Char::isLetterOrDigit) } ?: 0

    fun quality(review: ReviewMemo.Review, hasPhoneFact: Boolean, penalised: Boolean): Double {
        var q = QUALITY_BASE
        if (hasPhoneFact) q += QUALITY_FACT
        if (words(review.text) >= TEXT_WORDS) q += QUALITY_TEXT
        if (penalised) q *= 0.5
        return max(QUALITY_FLOOR, min(1.0, q))
    }

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
     * Pro at [time]: a term [wallet] paid for, or a seat a Pro payer had granted it by then (ProSharing), counted only while
     * the granter's own term runs. Seats granted by a wallet in [excludedGranters] (the reviewed app's verified publishers)
     * do not count: a trader cannot hand reviewers membership of the segment that reviews its own app.
     */
    fun proAt(entries: List<LedgerEntry>, wallet: String, time: Long, paid: (LedgerEntry) -> Boolean, excludedGranters: Set<String> = emptySet()): Boolean {
        if (proTerms(entries, wallet, paid).any { time in it }) return true
        val payers = entries.filter { it.memo is ReviewMemo.Pro && it.signer != wallet && it.signer !in excludedGranters && paid(it) }.map { it.signer }.toSet()
        return payers.any { g ->
            proTerms(entries, g, paid).any { time in it } &&
                ProSharing.holdsSeat(g, wallet, ProSharing.recordsOf(entries, g).filter { it.time <= time })
        }
    }

    /**
     * Everything the weights and segments need, read once per ledger. [paid] says whether a `pro` or `bst` record's
     * transaction credited the treasury with at least its floor; each reader supplies its own reading of the balances.
     */
    class Context(
        private val entries: List<LedgerEntry>,
        hidden: Set<String>,
        private val facts: (String) -> SignerFacts,
        private val paid: (LedgerEntry) -> Boolean,
        private val verifiedPublishers: Map<String, Set<String>> = emptyMap(),
    ) {
        private val penalised: Set<String> = entries.filter { it.signature in hidden && it.memo is ReviewMemo.Review }.map { it.signer }.toSet()
        private val phoneFacts: Set<Pair<String, String>> = entries.mapNotNull { e ->
            when (val m = e.memo) { is ReviewMemo.Use -> e.signer to m.packageName; is ReviewMemo.Time -> e.signer to m.packageName; else -> null }
        }.toSet()
        private val proCache = HashMap<Triple<String, Long, String>, Boolean>()
        private fun pro(wallet: String, time: Long, pkg: String) = proCache.getOrPut(Triple(wallet, time, pkg)) {
            proAt(entries, wallet, time, paid, verifiedPublishers[pkg].orEmpty())
        }

        /** Holds, at [time], a Pro seat granted by one of [pkg]'s verified publishers: a gift from the reviewed trader. */
        private fun gifted(wallet: String, time: Long, pkg: String): Boolean = verifiedPublishers[pkg].orEmpty().any { g ->
            g != wallet && ProSharing.holdsSeat(g, wallet, ProSharing.recordsOf(entries, g).filter { it.time <= time })
        }

        private val anyPublisher: Set<String> = verifiedPublishers.values.flatten().toSet()
        private val bySig = entries.associateBy { it.signature }

        /** Review ids with a valid promotion (see the header): every control is checked at the promotion's own time. */
        val promoted: Set<String> = entries.mapNotNull { e ->
            val b = e.memo as? ReviewMemo.Boost ?: return@mapNotNull null
            val review = bySig[b.recordId] ?: return@mapNotNull null
            val r = review.memo as? ReviewMemo.Review ?: return@mapNotNull null
            b.recordId.takeIf {
                review.signer == e.signer && e.signer !in anyPublisher && !Operator.isOperator(e.signer) && paid(e) &&
                    !Operator.publishes(r.packageName, verifiedPublishers) &&
                    pro(e.signer, e.blockTime, r.packageName) && !gifted(e.signer, e.blockTime, r.packageName)
            }
        }.toSet()

        fun parts(e: LedgerEntry): Parts {
            val r = e.memo as ReviewMemo.Review
            return Parts(Ranking.weight(facts(e.signer)), quality(r, (e.signer to r.packageName) in phoneFacts, e.signer in penalised),
                pro(e.signer, e.blockTime, r.packageName), gifted(e.signer, e.blockTime, r.packageName), e.signature in promoted)
        }
    }
}
