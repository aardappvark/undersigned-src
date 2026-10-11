package com.undersigned.protocol

/**
 * Moderation hides TEXT, never a voice (owner decision, 7 Oct 2026; policy in docs/MODERATION.md).
 *
 * A wallet on [MODERATORS] signs `mod|<record id>|<reason>`; readers then show the reason instead of that review's or
 * reply's text. The rating, tags and weight still count, the record stays on-chain, and `umd|<record id>` from the same
 * moderator undoes it (the later of the two wins). A moderator's record on a review of an app that moderator is the
 * proven publisher of is ignored, as a publisher's flags on their own app are, and an operator wallet's record is ignored on
 * every app the operator publishes. Community flags (Reputation) are separate.
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
                   packageOf: ((String) -> String?)? = null, autoHide: Boolean = true): Map<String, ReviewMemo.ModReason> {
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
            // Recusal (compliance review, 9 Oct 2026): an operator wallet never moderates an app the operator publishes, whichever
            // operator wallet proved it (FTC 16 CFR 465.7; the operator sells apps reviewed here).
            if (pkg != null && Operator.isOperator(key.first) && Operator.publishes(pkg, verifiedPublishers)) return@forEach
            out.putIfAbsent(key.second, m.reason)
        }
        // Automatic (owner, 11 Oct 2026): text on the shared abusive-language list is hidden with the reason "abuse" by every
        // reader, no moderator needed; the rating, tags and weight still count. A moderator's own record on the same text wins.
        if (autoHide) for (e in entries) {
            val text = when (val m = e.memo) { is ReviewMemo.Review -> m.text; is ReviewMemo.Reply -> m.text; is ReviewMemo.Dispute -> m.text; is ReviewMemo.StoreNote -> m.text; is ReviewMemo.Fixed -> m.text; else -> null }
            if (text != null && e.signature !in out && Abuse.blocked(text)) out[e.signature] = ReviewMemo.ModReason.ABUSE
        }
        return out
    }

    /**
     * Wallets a moderator has excluded ([ReviewMemo.Ban]; [ReviewMemo.Unban] lifts it, the later record wins per moderator;
     * any moderator's standing ban counts). Moderators and the operator's wallets are never banned, so a key cannot lock the
     * ledger's own keepers out.
     */
    fun banned(entries: List<LedgerEntry>, moderators: Set<String> = MODERATORS): Set<String> {
        val latest = HashMap<Pair<String, String>, LedgerEntry>()
        entries.filter { it.signer in moderators && (it.memo is ReviewMemo.Ban || it.memo is ReviewMemo.Unban) }
            .sortedWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature })
            .forEach { e -> val w = (e.memo as? ReviewMemo.Ban)?.wallet ?: (e.memo as ReviewMemo.Unban).wallet; latest[e.signer to w] = e }
        return latest.values.filter { it.memo is ReviewMemo.Ban }.map { (it.memo as ReviewMemo.Ban).wallet }
            .filter { it !in moderators && !Operator.isOperator(it) }.toSet()
    }

    /** [entries] without any record signed by a banned wallet; the moderators' own records stay, so the ban can be read back. */
    /** Who banned whom (latest standing ban per moderator). */
    fun bans(entries: List<LedgerEntry>, moderators: Set<String> = MODERATORS): Map<String, Set<String>> {
        val latest = HashMap<Pair<String, String>, LedgerEntry>()
        entries.filter { it.signer in moderators && (it.memo is ReviewMemo.Ban || it.memo is ReviewMemo.Unban) }
            .sortedWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature })
            .forEach { e -> val w = (e.memo as? ReviewMemo.Ban)?.wallet ?: (e.memo as ReviewMemo.Unban).wallet; latest[e.signer to w] = e }
        val out = HashMap<String, MutableSet<String>>()
        latest.values.filter { it.memo is ReviewMemo.Ban }.forEach { e -> val w = (e.memo as ReviewMemo.Ban).wallet; if (w !in moderators && !Operator.isOperator(w)) out.getOrPut(w) { HashSet() } += e.signer }
        return out
    }
    /**
     * The entries a reader keeps. Recusal (audit, 12 Oct 2026): a moderator's ban never removes the wallet's records about an app
     * that moderator publishes (the operator sells apps reviewed here); only another moderator's ban reaches those. The ban
     * record itself stays public on-chain, so the recusal is checkable.
     */
    fun excludeBanned(entries: List<LedgerEntry>, moderators: Set<String> = MODERATORS, verifiedPublishers: Map<String, Set<String>> = emptyMap()): List<LedgerEntry> {
        val b = bans(entries, moderators); if (b.isEmpty()) return entries
        val pkgOf = entries.associate { it.signature to pkgOfMemo(it.memo) }
        return entries.filter { e ->
            val by = b[e.signer] ?: return@filter true
            val pkg = pkgOfMemo(e.memo) ?: (e.memo as? ReviewMemo.Reply)?.let { pkgOf[it.reviewSignature] } ?: return@filter false
            // Kept only when EVERY banning moderator is recused for this app.
            by.all { mod -> Operator.isOperator(mod) && Operator.publishes(pkg, verifiedPublishers) || mod in verifiedPublishers[pkg].orEmpty() }
        }
    }
    private fun pkgOfMemo(m: ReviewMemo): String? = when (m) { is ReviewMemo.Review -> m.packageName; is ReviewMemo.Claim -> m.packageName; is ReviewMemo.Use -> m.packageName; is ReviewMemo.Time -> m.packageName; is ReviewMemo.Activity -> m.packageName; is ReviewMemo.CustomTags -> m.packageName; is ReviewMemo.Request -> m.packageName; else -> null }
}
