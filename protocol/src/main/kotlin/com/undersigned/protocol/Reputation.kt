package com.undersigned.protocol

import kotlin.math.roundToInt

/** Read-only standing of one signer, computed from the ledger. Counts first; the two scores are transparent formulas over the counts. */
data class SignerStanding(
    val signer: String,
    val countedReviews: Int,      // live reviews from a Genesis-Token wallet
    val withdrawn: Int,           // revocations they signed
    val repliesReceived: Int,     // replies by OTHER wallets under their reviews
    val repliesGiven: Int,
    val agreement: Double?,       // share of their tags another counted reviewer of the same app also used; null if no overlap possible
    val firstSeen: Long,
    val flagsGiven: Int,          // flags they signed
    val flagsUpheld: Int,         // of those, records that reached community consensus
    val flagsReceived: Int,       // distinct Genesis wallets that flagged any of their records
    val recordsHidden: Int,       // their records that reached consensus
    val repliedToReplies: Double?, // reviewer responsiveness: share of replies on their reviews they answered; null if none received
    val helpfulReceived: Double = 0.0, // Genesis-weighted upvotes on their records, self excluded
) {
    /** 0–100. Base 40; +30 tag agreement; +20 upheld flags; +10 × min(1, helpful/10); −50 × share of records hidden by consensus. Published formula, no reward attached. */
    val credibility: Int get() {
        var c = 40.0
        agreement?.let { c += 30.0 * it }
        c += 10.0 * minOf(1.0, helpfulReceived / 10.0)
        if (flagsGiven > 0) c += 20.0 * (flagsUpheld.toDouble() / flagsGiven)
        if (countedReviews + recordsHidden > 0) c -= 50.0 * (recordsHidden.toDouble() / (countedReviews + recordsHidden))
        return c.coerceIn(0.0, 100.0).roundToInt()
    }
}

/** How quickly a verified publisher answers signed reviews of one package. */
data class Responsiveness(
    val packageName: String,
    val answered: Int,
    val total: Int,
    val medianHours: Double?,     // review → first verified-publisher reply
) {
    val bucket: String get() = when {
        total == 0 -> "no reviews"
        answered == 0 -> "never"
        medianHours == null -> "unknown"
        medianHours < 24 -> "hours"
        medianHours < 24 * 7 -> "days"
        medianHours < 24 * 30 -> "weeks"
        else -> "months"
    }
}

object Reputation {
    /** Community consensus: a record is hidden by readers when distinct Genesis wallets other than its author flag it with total weight ≥ [HIDE_WEIGHT]; after the author disputes, ≥ 2 × [HIDE_WEIGHT]. */
    const val HIDE_WEIGHT = 2.0

    /** Genesis-weighted "helpful" votes per record, one per wallet, author's own vote excluded. */
    fun helpfulWeights(entries: List<LedgerEntry>, facts: (String) -> SignerFacts): Map<String, Double> {
        val bySig = entries.associateBy { it.signature }
        return entries.filter { it.memo is ReviewMemo.Upvote }.groupBy { (it.memo as ReviewMemo.Upvote).recordId }
            .mapValues { (target, ups) -> val author = bySig[target]?.signer; ups.filter { it.signer != author }.distinctBy { it.signer }.sumOf { Ranking.weight(facts(it.signer)) } }
            .filterValues { it > 0 }
    }
    /** The author's dispute of consensus hiding, if any (first one counts). */
    fun disputes(entries: List<LedgerEntry>): Map<String, LedgerEntry> {
        val bySig = entries.associateBy { it.signature }
        return entries.filter { it.memo is ReviewMemo.Dispute && bySig[(it.memo as ReviewMemo.Dispute).recordId]?.signer == it.signer }
            .sortedBy { it.blockTime }.associateBy { (it.memo as ReviewMemo.Dispute).recordId }
    }

    fun flagWeights(entries: List<LedgerEntry>, facts: (String) -> SignerFacts): Map<String, Double> {
        val bySig = entries.associateBy { it.signature }
        val out = HashMap<String, Double>()
        entries.filter { it.memo is ReviewMemo.Flag }
            .groupBy { (it.memo as ReviewMemo.Flag).recordId }
            .forEach { (target, flags) ->
                val author = bySig[target]?.signer
                val w = flags.filter { it.signer != author }.distinctBy { it.signer }.sumOf { Ranking.weight(facts(it.signer)) }
                if (w > 0) out[target] = w
            }
        return out
    }
    fun hiddenByConsensus(entries: List<LedgerEntry>, facts: (String) -> SignerFacts): Set<String> {
        val disputed = disputes(entries).keys
        return flagWeights(entries, facts).filter { (id, w) -> w >= (if (id in disputed) 2 * HIDE_WEIGHT else HIDE_WEIGHT) }.keys
    }

    fun standing(entries: List<LedgerEntry>, facts: (String) -> SignerFacts): Map<String, SignerStanding> {
        val revoked = Ranking.revoked(entries)
        val hidden = hiddenByConsensus(entries, facts)
        val live = entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && it.signature !in hidden }
        val counted = live.filter { facts(it.signer).hasGenesisToken }
        val bySig = entries.associateBy { it.signature }
        val tagsByApp = counted.groupBy { (it.memo as ReviewMemo.Review).packageName }
        val replies = entries.filter { it.memo is ReviewMemo.Reply }
        val helpful = helpfulWeights(entries, facts)
        return entries.map { it.signer }.distinct().associateWith { w ->
            val mine = counted.filter { it.signer == w }
            var overlapPossible = 0; var agreed = 0
            for (e in mine) {
                val r = e.memo as ReviewMemo.Review
                val others = tagsByApp[r.packageName].orEmpty().filter { it.signer != w }
                if (others.isEmpty()) continue
                val otherTags = others.flatMap { (it.memo as ReviewMemo.Review).tags }.toSet()
                overlapPossible += r.tags.size; agreed += r.tags.count { it in otherTags }
            }
            val myRecords = entries.filter { it.signer == w }.map { it.signature }.toSet()
            val myFlags = entries.filter { it.signer == w && it.memo is ReviewMemo.Flag && (it.memo as ReviewMemo.Flag).recordId !in myRecords }   // self-flags never count
            val repliesOnMine = replies.filter { e -> e.signer != w && bySig[(e.memo as ReviewMemo.Reply).reviewSignature]?.signer == w }
            // reviewer responsiveness: of the threads where someone replied to me, in how many did I reply afterwards?
            val threads = repliesOnMine.map { (it.memo as ReviewMemo.Reply).reviewSignature }.distinct()
            val answeredThreads = threads.count { t -> val firstOther = repliesOnMine.filter { (it.memo as ReviewMemo.Reply).reviewSignature == t }.minOf { it.blockTime }
                replies.any { it.signer == w && (it.memo as ReviewMemo.Reply).reviewSignature == t && it.blockTime >= firstOther } }
            SignerStanding(
                signer = w,
                countedReviews = mine.size,
                withdrawn = entries.count { it.signer == w && it.memo is ReviewMemo.Revocation },
                repliesReceived = repliesOnMine.size,
                repliesGiven = replies.count { it.signer == w },
                agreement = if (overlapPossible > 0) agreed.toDouble() / overlapPossible else null,
                firstSeen = entries.filter { it.signer == w }.minOfOrNull { it.blockTime } ?: 0L,
                flagsGiven = myFlags.size,
                flagsUpheld = myFlags.count { (it.memo as ReviewMemo.Flag).recordId in hidden },
                flagsReceived = entries.filter { it.memo is ReviewMemo.Flag && (it.memo as ReviewMemo.Flag).recordId in myRecords && it.signer != w }.map { it.signer }.distinct().size,
                recordsHidden = myRecords.count { it in hidden },
                repliedToReplies = if (threads.isEmpty()) null else answeredThreads.toDouble() / threads.size,
                helpfulReceived = myRecords.sumOf { helpful[it] ?: 0.0 },
            )
        }
    }

    /** Publisher responsiveness per package, given the wallets whose claims verified for it. */
    fun responsiveness(entries: List<LedgerEntry>, packageName: String, verifiedPublishers: Set<String>): Responsiveness {
        val revoked = Ranking.revoked(entries)
        val reviews = entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && (it.memo as ReviewMemo.Review).packageName == packageName }
        val replies = entries.filter { it.memo is ReviewMemo.Reply && it.signer in verifiedPublishers }
        val delays = reviews.mapNotNull { r ->
            replies.filter { (it.memo as ReviewMemo.Reply).reviewSignature == r.signature && it.blockTime >= r.blockTime }.minOfOrNull { it.blockTime }?.let { (it - r.blockTime) / 3600.0 }
        }.sorted()
        val median = if (delays.isEmpty()) null else if (delays.size % 2 == 1) delays[delays.size / 2] else (delays[delays.size / 2 - 1] + delays[delays.size / 2]) / 2
        return Responsiveness(packageName, delays.size, reviews.size, median)
    }

    /** Live counted reviews of one app grouped by the version they were written against, newest version first. */
    data class VersionScore(val versionCode: Long, val mean: Double, val count: Int)
    fun byVersion(entries: List<LedgerEntry>, packageName: String, facts: (String) -> SignerFacts): List<VersionScore> {
        val revoked = Ranking.revoked(entries)
        return entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && (it.memo as ReviewMemo.Review).packageName == packageName && facts(it.signer).hasGenesisToken }
            .groupBy { (it.memo as ReviewMemo.Review).versionCode }
            .map { (v, list) -> VersionScore(v, list.map { (it.memo as ReviewMemo.Review).rating }.average(), list.size) }
            .sortedByDescending { it.versionCode }
    }
}
