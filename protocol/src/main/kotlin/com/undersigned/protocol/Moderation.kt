package com.undersigned.protocol

/**
 * Moderation hides TEXT, never a voice (owner decision, 7 Oct 2026; policy in docs/MODERATION.md).
 *
 * A wallet on [MODERATORS] signs `mod|<record id>|<reason>`; readers then show the reason instead of that review's or
 * reply's text. The rating, tags and weight still count, the record stays on-chain, and `umd|<record id>` from the same
 * moderator undoes it (the later of the two wins). A moderator's record on a review of an app that moderator is the
 * proven publisher of is ignored, as a publisher's flags on their own app are. Community flags (Reputation) are separate.
 */
object Moderation {
    /** Who may hide text. Ships with the app and the server; changing it is a release, visible in the source. */
    val MODERATORS: Set<String> = setOf(
        "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD",   // the operator (airdropper.skr), until a dedicated moderation wallet exists
    )

    /**
     * Record id -> reason, for every record whose text a moderator currently hides. [packageOf] gives the app a record
     * belongs to (a review's own package; a reply's review's package), [verifiedPublishers] package -> proven wallets.
     */
    fun textHidden(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>> = emptyMap(), moderators: Set<String> = MODERATORS,
                   packageOf: ((String) -> String?)? = null): Map<String, ReviewMemo.ModReason> {
        val byId by lazy { entries.associateBy { it.signature } }
        fun pkgOf(id: String): String? = packageOf?.invoke(id) ?: when (val m = byId[id]?.memo) {
            is ReviewMemo.Review -> m.packageName
            is ReviewMemo.Reply -> (byId[m.reviewSignature]?.memo as? ReviewMemo.Review)?.packageName
            else -> null
        }
        val latest = HashMap<Pair<String, String>, LedgerEntry>()   // (moderator, target) -> their latest mod/umd
        entries.filter { it.signer in moderators && (it.memo is ReviewMemo.Moderate || it.memo is ReviewMemo.Unmoderate) }
            .sortedWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature })
            .forEach { e -> val target = (e.memo as? ReviewMemo.Moderate)?.recordId ?: (e.memo as ReviewMemo.Unmoderate).recordId; latest[e.signer to target] = e }
        val out = HashMap<String, ReviewMemo.ModReason>()
        latest.forEach { (key, e) ->
            val m = e.memo as? ReviewMemo.Moderate ?: return@forEach
            val pkg = pkgOf(key.second)
            if (pkg != null && key.first in verifiedPublishers[pkg].orEmpty()) return@forEach
            out.putIfAbsent(key.second, m.reason)
        }
        return out
    }
}
