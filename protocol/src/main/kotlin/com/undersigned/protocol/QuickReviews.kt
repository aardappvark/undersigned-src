package com.undersigned.protocol

/**
 * Quick reviews (owner, 10 Oct 2026): stars, tags and text signed by an app key on the phone, no wallet, no Seed Vault,
 * fee paid by [Registry.QUICK_RELAYER]. Anonymous, so they never count in the signed score; they have their own section,
 * their own average and their own sort. A review is quick when a [ReviewMemo.Quick] by its signer rides in its transaction.
 */
object QuickReviews {
    data class Score(val packageName: String, val mean: Double, val count: Int)

    /** Review ids marked quick. */
    fun ids(entries: List<LedgerEntry>): Set<String> {
        val marks = entries.filter { it.memo == ReviewMemo.Quick }.map { it.signature.substringBefore(':') to it.signer }.toSet()
        if (marks.isEmpty()) return emptySet()
        return entries.filter { it.memo is ReviewMemo.Review && (it.signature.substringBefore(':') to it.signer) in marks }.map { it.signature }.toSet()
    }

    /** Per app: the mean of each quick key's latest live review, and how many keys. */
    fun scores(entries: List<LedgerEntry>): Map<String, Score> {
        val quick = ids(entries); if (quick.isEmpty()) return emptyMap()
        val revoked = Ranking.revoked(entries)
        return entries.filter { it.signature in quick && it.signature !in revoked }
            .groupBy { (it.memo as ReviewMemo.Review).packageName }
            .mapValues { (pkg, l) -> val latest = l.groupBy { it.signer }.values.map { s -> s.maxBy { it.blockTime } }
                Score(pkg, latest.map { (it.memo as ReviewMemo.Review).rating }.average(), latest.size) }
    }
}
