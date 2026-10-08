package com.undersigned.protocol

import kotlin.math.ln
import kotlin.math.min

/** One ledger entry after the chain has been read: the signer is the identity, never the payload. */
data class LedgerEntry(
    val signature: String,
    val signer: String,            // base58 wallet that signed the transaction
    val blockTime: Long,
    val memo: ReviewMemo,
)

/** Facts read live from the chain about a signer, at ranking time. Both are READ-ONLY lookups. */
data class SignerFacts(
    /** Holds an attested-device credential from the governed list (today: the Seeker Genesis Token, one per device) — the one-device-one-voice gate. Reviews from wallets without one are never counted. */
    val hasGenesisToken: Boolean,
    /** Staked SKR in whole tokens, as read from the staking program (SeekerVerify `Season1RpcClient` shape). Weighting only; nothing is staked, moved or promoted by this app. */
    val stakedSkr: Double,
    /** Which attestation matched (id from the list), e.g. "seeker-genesis". Null when none. */
    val attestation: String? = if (hasGenesisToken) "seeker-genesis" else null,
)

data class AppScore(
    val packageName: String,
    val countedReviews: Int,
    val weightedRating: Double,      // 1..5, Bayesian-shrunk toward the prior — use for ORDERING
    val weightedMean: Double,        // 1..5, weighted mean of the counted reviews, no prior — use for DISPLAY
    val tagCounts: Map<ReviewMemo.Tag, Int>,
    val latestVersionReviewed: Long,
)

object Ranking {
    /**
     * A verified publisher reviewing their own app. Their flags on that app are already ignored; their
     * reviews are excluded from the score for the same reason — a publisher cannot be a counted voice on
     * their own listing. The review still appears, labelled, and their reply carries the publisher badge.
     */
    fun isSelfPublished(entry: LedgerEntry, verifiedPublishers: Map<String, Set<String>>): Boolean {
        val pkg = (entry.memo as? ReviewMemo.Review)?.packageName ?: return false
        return entry.signer in verifiedPublishers[pkg].orEmpty()
    }

    /** ln-damped: 0 SKR → 1.0, 1k → ~1.69, 100k → ~5.6 but CAPPED at [MAX_WEIGHT]. Whales cannot buy a listing. */
    const val MAX_WEIGHT = 3.0
    /** Bayesian prior: every app starts as an average app with the strength of [PRIOR_WEIGHT] reviews. */
    const val PRIOR_MEAN = 3.0
    const val PRIOR_WEIGHT = 5.0

    fun weight(f: SignerFacts): Double = if (!f.hasGenesisToken) 0.0 else min(MAX_WEIGHT, 1.0 + ln(1.0 + f.stakedSkr / 1_000.0))

    /**
     * Rules, in order: (1) revocations remove the review they name, whoever posted the revocation
     * — but only if the same wallet signed both; (2) one counted review per wallet per package,
     * the newest wins; (3) wallets without a Genesis Token weigh zero; (4) each review weighs device × quality × paid
     * (see [Weighting]), every factor capped. Everything here is deterministic given the chain, so any reader reaches the same
     * ranking — that is the point of putting it on-chain.
     */
    /** Signatures withdrawn by a revocation from the SAME wallet that signed the review. */
    fun revoked(entries: List<LedgerEntry>): Set<String> {
        val bySig = entries.associateBy { it.signature }
        return entries.mapNotNull { e ->
            (e.memo as? ReviewMemo.Revocation)?.let { r -> bySig[r.reviewSignature]?.takeIf { it.signer == e.signer }?.signature }
        }.toSet()
    }

    /**
     * The SIGNED score: every counted review once, at weight device × quality (see [Weighting]); payment never enters it.
     * [include] narrows the reviews counted, which is how the separately labelled segment scores are made ([segments]).
     */
    fun score(
        entries: List<LedgerEntry>,
        verifiedPublishers: Map<String, Set<String>> = emptyMap(),
        /** Only needed when [include] asks about Pro or promotion. */
        paid: (LedgerEntry) -> Boolean = { false },
        include: (LedgerEntry, Weighting.Parts) -> Boolean = { _, _ -> true },
        facts: (signer: String) -> SignerFacts,
    ): List<AppScore> {
        val revoked = revoked(entries)
        val hidden = Reputation.hiddenByConsensus(entries, verifiedPublishers, facts)
        val ctx = Weighting.Context(entries, hidden, facts, paid, verifiedPublishers)
        val live = entries.filter { it.signature !in revoked && it.signature !in hidden && it.memo is ReviewMemo.Review && !isSelfPublished(it, verifiedPublishers) }
        val newestPerWalletPerApp = live
            .groupBy { it.signer to (it.memo as ReviewMemo.Review).packageName }
            // Newest wins; two records in the same second (an import batch) break the tie on the record id, so every reader picks the same one.
            .map { (_, v) -> v.maxWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature }) }
        return newestPerWalletPerApp
            .groupBy { (it.memo as ReviewMemo.Review).packageName }
            .map { (pkg, list) ->
                val weighted = list.map { it to ctx.parts(it) }.filter { (e, w) -> w.weight > 0.0 && include(e, w) }.map { (e, w) -> e to w.weight }
                val wSum = weighted.sumOf { it.second }
                val rSum = weighted.sumOf { (e, w) -> (e.memo as ReviewMemo.Review).rating * w }
                val rating = (PRIOR_MEAN * PRIOR_WEIGHT + rSum) / (PRIOR_WEIGHT + wSum)
                val mean = if (wSum > 0.0) rSum / wSum else 0.0
                val tags = weighted.flatMap { (e, _) -> (e.memo as ReviewMemo.Review).tags }.groupingBy { it }.eachCount()
                AppScore(pkg, weighted.size, rating, mean, tags, weighted.maxOfOrNull { (e, _) -> (e.memo as ReviewMemo.Review).versionCode } ?: 0L)
            }
            .sortedWith(compareByDescending<AppScore> { it.weightedRating }.thenByDescending { it.countedReviews })
    }

    /** The two labelled segment scores shown beside the signed one, per app; an app absent from a map has no review in it. */
    data class Segments(val pro: Map<String, AppScore>, val promoted: Map<String, AppScore>)

    fun segments(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>>, paid: (LedgerEntry) -> Boolean, facts: (String) -> SignerFacts): Segments = Segments(
        score(entries, verifiedPublishers, paid, { _, w -> w.pro }, facts).filter { it.countedReviews > 0 }.associateBy { it.packageName },
        score(entries, verifiedPublishers, paid, { _, w -> w.promoted }, facts).filter { it.countedReviews > 0 }.associateBy { it.packageName },
    )
}
