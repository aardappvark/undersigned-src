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
)

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
    /**
     * Community consensus: a record is hidden by readers when the NET flag weight — flags minus "looks genuine"
     * clears, each one per attested wallet, author excluded, latest flag/withdrawal per wallet wins, and flags from
     * a verified publisher of the reviewed app ignored — reaches [HIDE_WEIGHT]; after the author disputes, 2 × that.
     * Every step is reversible on-chain: withdraw a flag (unf), add a clear (clr), dispute (dsp).
     */
    const val HIDE_WEIGHT = 2.0
    /**
     * Weight alone is not consensus: one wallet at the 3x staking cap weighs 3.0, above [HIDE_WEIGHT], so it
     * could hide any record by itself (found 15 Sep 2026 with a 108k-SKR wallet). Hiding also needs this many
     * distinct attested flaggers, net of clears; [DISPUTED_MIN_FLAGGERS] once the author disputes.
     */
    const val MIN_FLAGGERS = 2
    const val DISPUTED_MIN_FLAGGERS = 3

    /** Distinct attested wallets currently flagging a record, net of distinct attested clearers (author and the app's verified publishers excluded). */
    fun netFlaggers(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>> = emptyMap(), facts: (String) -> SignerFacts): Map<String, Int> {
        val bySig = entries.associateBy { it.signature }
        val clears = currentClearers(entries)
        val out = HashMap<String, Int>()
        for ((target, flaggers) in currentFlaggers(entries)) {
            val rec = bySig[target]; val author = rec?.signer
            val pkg = (rec?.memo as? ReviewMemo.Review)?.packageName ?: ((rec?.memo as? ReviewMemo.Reply)?.let { (bySig[it.reviewSignature]?.memo as? ReviewMemo.Review)?.packageName })
            val excluded = pkg?.let { verifiedPublishers[it] }.orEmpty()
            val f = flaggers.count { it != author && it !in excluded && facts(it).hasGenesisToken }
            val c = clears[target].orEmpty().count { it != author && facts(it).hasGenesisToken }
            if (f - c > 0) out[target] = f - c
        }
        return out
    }

    /** Per (record, wallet): the latest "on"/"off" state of a toggle pair, by block time. */
    private fun latestState(entries: List<LedgerEntry>, on: (ReviewMemo) -> String?, off: (ReviewMemo) -> String?): Map<String, Map<String, Boolean>> {
        val out = HashMap<String, HashMap<String, Pair<Long, Boolean>>>()
        for (e in entries.sortedBy { it.blockTime }) {
            val target = on(e.memo)?.let { it to true } ?: off(e.memo)?.let { it to false } ?: continue
            out.getOrPut(target.first) { HashMap() }[e.signer] = e.blockTime to target.second
        }
        return out.mapValues { (_, m) -> m.mapValues { it.value.second } }
    }

    /** Attestation-weighted "helpful" votes per record, one per wallet, withdrawable, author's own vote excluded. */
    fun helpfulWeights(entries: List<LedgerEntry>, facts: (String) -> SignerFacts): Map<String, Double> {
        val bySig = entries.associateBy { it.signature }
        return latestState(entries, { (it as? ReviewMemo.Upvote)?.recordId }, { (it as? ReviewMemo.Unvote)?.recordId })
            .mapValues { (target, m) -> val author = bySig[target]?.signer; m.filter { it.value && it.key != author }.keys.sumOf { Ranking.weight(facts(it)) } }
            .filterValues { it > 0 }
    }
    /** Which wallets currently flag / clear a record (after withdrawals). */
    fun currentFlaggers(entries: List<LedgerEntry>): Map<String, Set<String>> =
        latestState(entries, { (it as? ReviewMemo.Flag)?.recordId }, { (it as? ReviewMemo.Unflag)?.recordId }).mapValues { (_, m) -> m.filter { it.value }.keys }
    fun currentClearers(entries: List<LedgerEntry>): Map<String, Set<String>> =
        entries.filter { it.memo is ReviewMemo.Clear }.groupBy { (it.memo as ReviewMemo.Clear).recordId }.mapValues { (_, l) -> l.map { it.signer }.toSet() }
    /** The author's dispute of consensus hiding, if any (first one counts). */
    fun disputes(entries: List<LedgerEntry>): Map<String, LedgerEntry> {
        val bySig = entries.associateBy { it.signature }
        return entries.filter { it.memo is ReviewMemo.Dispute && bySig[(it.memo as ReviewMemo.Dispute).recordId]?.signer == it.signer }
            .sortedBy { it.blockTime }.associateBy { (it.memo as ReviewMemo.Dispute).recordId }
    }

    /** Net flag weight per record (flags − clears), never below zero. [verifiedPublishers]: package → wallets whose flags on that app's reviews are ignored. */
    fun flagWeights(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>> = emptyMap(), facts: (String) -> SignerFacts): Map<String, Double> {
        val bySig = entries.associateBy { it.signature }
        val clears = currentClearers(entries)
        val out = HashMap<String, Double>()
        for ((target, flaggers) in currentFlaggers(entries)) {
            val rec = bySig[target]; val author = rec?.signer
            val pkg = (rec?.memo as? ReviewMemo.Review)?.packageName ?: ((rec?.memo as? ReviewMemo.Reply)?.let { (bySig[it.reviewSignature]?.memo as? ReviewMemo.Review)?.packageName })
            val excluded = pkg?.let { verifiedPublishers[it] }.orEmpty()
            val fw = flaggers.filter { it != author && it !in excluded }.sumOf { Ranking.weight(facts(it)) }
            val cw = clears[target].orEmpty().filter { it != author }.sumOf { Ranking.weight(facts(it)) }
            val net = fw - cw
            if (net > 0) out[target] = net
        }
        return out
    }
    fun hiddenByConsensus(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>> = emptyMap(), facts: (String) -> SignerFacts): Set<String> {
        val disputed = disputes(entries).keys
        val people = netFlaggers(entries, verifiedPublishers, facts)
        return flagWeights(entries, verifiedPublishers, facts).filter { (id, w) ->
            val isDisputed = id in disputed
            w >= (if (isDisputed) 2 * HIDE_WEIGHT else HIDE_WEIGHT) &&
                (people[id] ?: 0) >= (if (isDisputed) DISPUTED_MIN_FLAGGERS else MIN_FLAGGERS)
        }.keys
    }

    fun standing(allEntries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>> = emptyMap(), facts: (String) -> SignerFacts): Map<String, SignerStanding> {
        // A licence is not a judgement: a wallet whose only record is `pro` has no standing, and "signing since" never dates from a payment.
        val entries = allEntries.filter { it.memo !is ReviewMemo.Pro }
        val revoked = Ranking.revoked(entries)
        val hidden = hiddenByConsensus(entries, verifiedPublishers, facts)
        val flaggersNow = currentFlaggers(entries)
        val live = entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && it.signature !in hidden && !Ranking.isSelfPublished(it, verifiedPublishers) }
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
            // flags this wallet currently holds (withdrawn ones do not count), self-flags never count
            val myFlags = flaggersNow.filter { (id, who) -> w in who && id !in myRecords }.keys.map { id -> entries.first { it.signer == w && (it.memo as? ReviewMemo.Flag)?.recordId == id } }
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
                flagsReceived = flaggersNow.filter { (id, _) -> id in myRecords }.values.flatten().filter { it != w }.distinct().size,
                recordsHidden = myRecords.count { it in hidden },
                repliedToReplies = if (threads.isEmpty()) null else answeredThreads.toDouble() / threads.size,
                helpfulReceived = myRecords.sumOf { helpful[it] ?: 0.0 },
            )
        }
    }

    /** Publisher responsiveness per package, given the wallets whose claims verified for it. */
    fun responsiveness(entries: List<LedgerEntry>, packageName: String, verifiedPublishers: Set<String>): Responsiveness {
        val revoked = Ranking.revoked(entries)
        // One thread per reviewer: a reviewer who edits their review (signs a newer one) is still one person waiting for
        // one answer, and the publisher's own reviews are nothing to answer. Counting every record read "0 of 2" for a
        // single reviewer who had edited once (seen on mainnet, 28 Sep 2026).
        val threads = entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && (it.memo as ReviewMemo.Review).packageName == packageName && it.signer !in verifiedPublishers }
            .groupBy { it.signer }
        val replies = entries.filter { it.memo is ReviewMemo.Reply && it.signer in verifiedPublishers }
        // Answered when the publisher replied to any of that reviewer's live reviews of this app; the gap runs from the review answered.
        val delays = threads.values.mapNotNull { mine ->
            mine.mapNotNull { r -> replies.filter { (it.memo as ReviewMemo.Reply).reviewSignature == r.signature && it.blockTime >= r.blockTime }.minOfOrNull { it.blockTime }?.let { (it - r.blockTime) / 3600.0 } }.minOrNull()
        }.sorted()
        val median = if (delays.isEmpty()) null else if (delays.size % 2 == 1) delays[delays.size / 2] else (delays[delays.size / 2 - 1] + delays[delays.size / 2]) / 2
        return Responsiveness(packageName, delays.size, threads.size, median)
    }

    /** Live counted reviews of one app grouped by the version they were written against, newest version first. */
    data class VersionScore(val versionCode: Long, val mean: Double, val count: Int)
    fun byVersion(entries: List<LedgerEntry>, packageName: String, verifiedPublishers: Map<String, Set<String>> = emptyMap(), facts: (String) -> SignerFacts): List<VersionScore> {
        val revoked = Ranking.revoked(entries)
        return entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && (it.memo as ReviewMemo.Review).packageName == packageName && !Ranking.isSelfPublished(it, verifiedPublishers) && facts(it.signer).hasGenesisToken }
            .groupBy { (it.memo as ReviewMemo.Review).versionCode }
            .map { (v, list) -> VersionScore(v, list.map { (it.memo as ReviewMemo.Review).rating }.average(), list.size) }
            .sortedByDescending { it.versionCode }
    }
}
