package com.undersigned.protocol

import kotlin.math.ceil
import kotlin.math.max

/**
 * One app's reviews over time, for the chart on its page (owner, 8 Oct 2026: "a beautiful graph ... reviews over time ...
 * with a moving bar like we used in Reckoner"). Pure, so the chart draws exactly what is tested.
 *
 * Per period: how many store reviews were written by wallets holding a Genesis Token and by other wallets, how many signed
 * reviews were written, and, as of the END of the period, the signed score, the Pro members' score and the running
 * average of the store reviews read. The store line is the average of the reviews this phone READ, never the store's own
 * rating (which counts all of them), and the chart says so.
 */
object Timeline {
    const val DAY = 86_400L
    const val MAX_BARS = 40

    data class StorePoint(val time: Long, val rating: Int, val seekerOwner: Boolean)

    data class Bucket(
        val start: Long,
        val end: Long,
        val storeSeeker: Int,
        val storeOther: Int,
        val signed: Int,
        /** Running average of the store reviews read up to [end]; null before the first one. */
        val storeMean: Double?,
        val storeRead: Int,
        /** The signed score as of [end] (device × quality, nothing paid); null while no review counts. */
        val signedMean: Double?,
        val signedCounted: Int,
        /** The Pro members' score as of [end]; null while no Pro member's review counts. */
        val proMean: Double?,
    ) {
        val storeTotal: Int get() = storeSeeker + storeOther
    }

    /** Whole days per bar so the range fits in at most [maxBars] bars. */
    fun bucketDays(from: Long, to: Long, maxBars: Int = MAX_BARS): Long {
        val days = max(1L, ceil((to - from).toDouble() / DAY).toLong())
        return max(1L, ceil(days.toDouble() / maxBars).toLong())
    }

    /** Where "All" starts: the first store review read or the first signed review of [pkg], whichever is earlier. */
    fun earliest(pkg: String, store: List<StorePoint>, entries: List<LedgerEntry>): Long? =
        (store.map { it.time } + entries.filter { (it.memo as? ReviewMemo.Review)?.packageName == pkg }.map { it.blockTime }).minOrNull()

    fun build(
        pkg: String,
        store: List<StorePoint>,
        entries: List<LedgerEntry>,
        verifiedPublishers: Map<String, Set<String>>,
        facts: (String) -> SignerFacts,
        paid: (LedgerEntry) -> Boolean,
        from: Long,
        to: Long,
        maxBars: Int = MAX_BARS,
    ): List<Bucket> {
        if (to <= from) return emptyList()
        val step = bucketDays(from, to, maxBars) * DAY
        val sortedStore = store.sortedBy { it.time }
        val reviews = entries.filter { (it.memo as? ReviewMemo.Review)?.packageName == pkg }
        val out = ArrayList<Bucket>()
        var start = from
        while (start < to) {
            val end = minOf(start + step, to)
            val inBar = sortedStore.filter { it.time in start until end }
            val upTo = sortedStore.filter { it.time < end }
            val known = entries.filter { it.blockTime < end }
            val signed = Ranking.score(known, verifiedPublishers, facts = facts).firstOrNull { it.packageName == pkg }?.takeIf { it.countedReviews > 0 }
            val pro = Ranking.score(known, verifiedPublishers, paid, { _, w -> w.pro }, facts).firstOrNull { it.packageName == pkg }?.takeIf { it.countedReviews > 0 }
            out += Bucket(
                start = start, end = end,
                storeSeeker = inBar.count { it.seekerOwner }, storeOther = inBar.count { !it.seekerOwner },
                signed = reviews.count { it.blockTime in start until end && facts(it.signer).hasGenesisToken },
                storeMean = upTo.takeIf { it.isNotEmpty() }?.map { it.rating }?.average(), storeRead = upTo.size,
                signedMean = signed?.weightedMean, signedCounted = signed?.countedReviews ?: 0,
                proMean = pro?.weightedMean,
            )
            start = end
        }
        return out
    }
}
