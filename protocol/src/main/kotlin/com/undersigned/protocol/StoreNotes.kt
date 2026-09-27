package com.undersigned.protocol

/**
 * A publisher's signed notes on dApp Store reviews of their own app (`SRL1|snt`). The store has its own reply box, but
 * nothing there is signed or checkable, and a publisher has no documented way to dispute a store review; a note here is
 * a public, permanent, attributable answer that sits beside the review in Undersigned. Only notes from a VERIFIED
 * publisher of the package count; the newest one per review wins; a withdrawn note (rvk by its signer) is gone.
 */
object StoreNotes {
    /**
     * Keyed by (package, store review id): a note counts only on the app its signer publishes. Keyed by review id alone,
     * app A's verified publisher could sign a note naming a review of app B and have it shown — and win — on B's page
     * (found in review, 28 Sep 2026).
     */
    fun current(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>>): Map<Pair<String, String>, LedgerEntry> {
        val revoked = Ranking.revoked(entries)
        return entries.filter { e -> val n = e.memo as? ReviewMemo.StoreNote ?: return@filter false
                e.signature !in revoked && e.signer in verifiedPublishers[n.packageName].orEmpty() }
            .groupBy { (it.memo as ReviewMemo.StoreNote).let { n -> n.packageName to n.reviewId } }
            .mapValues { (_, l) -> l.maxWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature }) }
    }
}
