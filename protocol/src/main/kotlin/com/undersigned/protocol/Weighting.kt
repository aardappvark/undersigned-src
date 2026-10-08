package com.undersigned.protocol

import kotlin.math.max
import kotlin.math.min

/**
 * What one counted review weighs, and which separately labelled score it also appears in (owner, 9 Oct 2026: "count it as
 * paid or promoted ratings and keep it separate from organic ratings and pro ratings").
 *
 * WEIGHT, the same in every score, and never touched by payment:
 *  - **device** ([Ranking.weight]): the Genesis Token gate and staked SKR, capped at 3×.
 *  - **quality**, earned, between [QUALITY_FLOOR] and 1.0: [QUALITY_BASE] for stars and tags alone; +[QUALITY_FACT] when the
 *    review carries what the phone saw (install age or usage time); +[QUALITY_TEXT] for a written note of [TEXT_WORDS]+
 *    words; halved for a wallet that has a review hidden by consensus. Low-value reviews sink by themselves.
 *
 * SEGMENTS, each its own score shown beside the signed score with its own count (the signed score includes every counted
 * review once, at the same weight, whatever the reviewer paid):
 *  - **Pro members**: reviews signed while the wallet held Pro.
 *  - **Promoted**: reviews their own author paid to promote (`bst`), Pro only; shown first in the list, labelled paid.
 *
 * Why segments and not a multiplier (compliance check, 9 Oct 2026): UK CMA guidance on the DMCC Act (CMA208, 4.7 and 5.1)
 * says incentivised reviews must be distinguished from others, and that review information must not be influenced by
 * payment for prominence; the ACCC's guide says promoted results must be distinguished from organic ones; EU law (UCPD
 * Art. 7(6), Annex I 23c) bars misrepresenting reviews. A paid multiplier blends payment into a number; a separate,
 * counted, labelled score does not.
 *
 * Traders cannot buy their way into either segment: a Pro seat granted by an app's verified publisher does not make the
 * holder a Pro reviewer of that publisher's apps, and a promotion signed by any verified publisher is not honoured. Flags,
 * votes and hiding use the device weight alone.
 */
object Weighting {
    const val QUALITY_BASE = 0.5
    const val QUALITY_FACT = 0.25
    const val QUALITY_TEXT = 0.25
    const val QUALITY_FLOOR = 0.25
    const val TEXT_WORDS = 6

    data class Parts(val device: Double, val quality: Double, val pro: Boolean, val promoted: Boolean) {
        /** The review's weight in every score it appears in. Payment never enters it. */
        val weight: Double get() = device * quality
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
        private val anyPublisher: Set<String> = verifiedPublishers.values.flatten().toSet()
        private val bySig = entries.associateBy { it.signature }
        private val proCache = HashMap<Triple<String, Long, String>, Boolean>()
        private fun pro(wallet: String, time: Long, pkg: String) = proCache.getOrPut(Triple(wallet, time, pkg)) {
            proAt(entries, wallet, time, paid, verifiedPublishers[pkg].orEmpty())
        }

        /** Review ids with a valid promotion: paid, signed by the review's own wallet while it held Pro, never by a publisher. */
        val promoted: Set<String> = entries.mapNotNull { e ->
            val b = e.memo as? ReviewMemo.Boost ?: return@mapNotNull null
            val review = bySig[b.recordId] ?: return@mapNotNull null
            val r = review.memo as? ReviewMemo.Review ?: return@mapNotNull null
            b.recordId.takeIf { review.signer == e.signer && e.signer !in anyPublisher && paid(e) && pro(e.signer, e.blockTime, r.packageName) }
        }.toSet()

        fun parts(e: LedgerEntry): Parts {
            val r = e.memo as ReviewMemo.Review
            return Parts(Ranking.weight(facts(e.signer)), quality(r, (e.signer to r.packageName) in phoneFacts, e.signer in penalised),
                pro(e.signer, e.blockTime, r.packageName), e.signature in promoted)
        }
    }
}
