package com.undersigned.protocol

/**
 * The second round (owner, 10 Oct 2026): review -> publisher reply -> publisher's "fixed in version X" -> the reviewer's
 * follow-up review, with first and second stars and the change, and how a publisher reacts to what reviewers raise.
 *
 * Rules, all checked on the ledger alone:
 *  - a [ReviewMemo.Fixed] counts only when signed by a verified publisher of the reviewed app, after the review;
 *  - a [ReviewMemo.FollowUp] counts only beside a review in the SAME transaction, by the SAME signer, of the SAME app as
 *    the original, signed after it; the original must not be withdrawn.
 */
object Threads {
    data class Fix(val entry: LedgerEntry, val review: LedgerEntry, val versionCode: Long, val text: String?)
    data class Round(val original: LedgerEntry, val followUp: LedgerEntry, val fix: Fix?) {
        val before: Int get() = (original.memo as ReviewMemo.Review).rating
        val after: Int get() = (followUp.memo as ReviewMemo.Review).rating
        val delta: Int get() = after - before
    }
    data class FixStats(val packageName: String, val fixes: Int, val followUpsAfterFix: Int, val meanDelta: Double?, val medianHoursToFix: Double?)

    /** Valid fix notices: review id -> the latest one. */
    fun fixes(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>>): Map<String, Fix> {
        val bySig = entries.associateBy { it.signature }
        return entries.mapNotNull { e ->
            val f = e.memo as? ReviewMemo.Fixed ?: return@mapNotNull null
            val r = bySig[f.reviewSignature]?.takeIf { it.memo is ReviewMemo.Review } ?: return@mapNotNull null
            val pkg = (r.memo as ReviewMemo.Review).packageName
            if (e.signer !in verifiedPublishers[pkg].orEmpty() || e.blockTime < r.blockTime) return@mapNotNull null
            Fix(e, r, f.versionCode, f.text)
        }.groupBy { it.review.signature }.mapValues { (_, l) -> l.maxBy { it.entry.blockTime } }
    }

    /** Valid follow-ups: follow-up review id -> its round. */
    fun rounds(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>>): Map<String, Round> {
        val bySig = entries.associateBy { it.signature }
        val revoked = Ranking.revoked(entries)
        val fixes = fixes(entries, verifiedPublishers)
        val reviewsByTx = entries.filter { it.memo is ReviewMemo.Review }.groupBy { it.signature.substringBefore(':') }
        return entries.mapNotNull { e ->
            val fu = e.memo as? ReviewMemo.FollowUp ?: return@mapNotNull null
            val original = bySig[fu.originalSignature]?.takeIf { it.memo is ReviewMemo.Review && it.signer == e.signer && it.signature !in revoked } ?: return@mapNotNull null
            val pkg = (original.memo as ReviewMemo.Review).packageName
            val next = reviewsByTx[e.signature.substringBefore(':')]?.firstOrNull { it.signer == e.signer && (it.memo as ReviewMemo.Review).packageName == pkg } ?: return@mapNotNull null
            if (next.blockTime < original.blockTime || next.signature == original.signature) return@mapNotNull null
            next.signature to Round(original, next, fixes[original.signature]?.takeIf { it.entry.blockTime <= next.blockTime })
        }.toMap()
    }

    /** How a publisher reacts to what reviewers raise, for one app: fixes announced, follow-ups after them, star change, time to fix. */
    fun stats(entries: List<LedgerEntry>, packageName: String, verifiedPublishers: Map<String, Set<String>>): FixStats {
        val fixes = fixes(entries, verifiedPublishers).values.filter { (it.review.memo as ReviewMemo.Review).packageName == packageName }
        val after = rounds(entries, verifiedPublishers).values.filter { (it.original.memo as ReviewMemo.Review).packageName == packageName && it.fix != null }
        val hours = fixes.map { (it.entry.blockTime - it.review.blockTime) / 3600.0 }.sorted()
        val median = if (hours.isEmpty()) null else if (hours.size % 2 == 1) hours[hours.size / 2] else (hours[hours.size / 2 - 1] + hours[hours.size / 2]) / 2
        return FixStats(packageName, fixes.size, after.size, after.takeIf { it.isNotEmpty() }?.map { it.delta.toDouble() }?.average(), median)
    }
}
