package com.undersigned.protocol

/** Read-only standing of one signer, computed from the ledger. No score, no reward, no rank — just counts a reader can check. */
data class SignerStanding(
    val signer: String,
    val countedReviews: Int,      // live reviews from a Genesis-Token wallet
    val withdrawn: Int,           // revocations they signed
    val repliesReceived: Int,     // replies by OTHER wallets under their reviews
    val repliesGiven: Int,
    val agreement: Double?,       // share of their tags that another counted reviewer of the same app also used; null if no overlap possible
    val firstSeen: Long,
)

object Reputation {
    fun standing(entries: List<LedgerEntry>, facts: (String) -> SignerFacts): Map<String, SignerStanding> {
        val revoked = Ranking.revoked(entries)
        val live = entries.filter { it.memo is ReviewMemo.Review && it.signature !in revoked }
        val counted = live.filter { facts(it.signer).hasGenesisToken }
        val bySig = entries.associateBy { it.signature }
        val tagsByApp = counted.groupBy { (it.memo as ReviewMemo.Review).packageName }
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
            SignerStanding(
                signer = w,
                countedReviews = mine.size,
                withdrawn = entries.count { it.signer == w && it.memo is ReviewMemo.Revocation },
                repliesReceived = entries.count { e -> e.signer != w && (e.memo as? ReviewMemo.Reply)?.let { bySig[it.reviewSignature]?.signer == w } == true },
                repliesGiven = entries.count { it.signer == w && it.memo is ReviewMemo.Reply },
                agreement = if (overlapPossible > 0) agreed.toDouble() / overlapPossible else null,
                firstSeen = entries.filter { it.signer == w }.minOfOrNull { it.blockTime } ?: 0L,
            )
        }
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
