package com.undersigned.protocol

/**
 * Tips to a dApp's developer (owner, 9 Oct 2026: "a tip to dev rather than a promotion … paid directly to the signed off
 * wallet, which is also an incentive for the dev to sign up").
 *
 * A tip is one transaction: a SOL or SKR transfer straight from the tipper's wallet to the app's PROVEN publisher wallet,
 * the registry reference, and an `SRL1|tip|<package>|<recipient>` record, optionally with the tipper's review in the same
 * transaction. Undersigned never holds it and takes no share; the only other cost is the network fee.
 *
 * It counts, for every reader, only when:
 *  - [recipient] is a proven publisher of the package when the ledger is read (a claim alone is not enough), and
 *  - the transaction credited that wallet at least [Payments.meaningful]'s floor (0.001 SOL or 1 SKR), read from balances,
 *    never from the memo, and
 *  - the tipper is not one of the app's own publishers and not an Undersigned operator wallet.
 *
 * A tipper's review is NOT weighted up. It counts at its ordinary weight in the signed score and also appears, labelled, in
 * a separate "tipped the developer" score with its own count, the same treatment as Pro members.
 */
object Tips {
    /** What one transaction credited the tip's recipient: lamports and raw SKR units. */
    data class Credit(val lamports: Long, val skrRaw: Long)
    data class Tip(val signature: String, val tipper: String, val packageName: String, val recipient: String, val time: Long, val lamports: Long, val skrRaw: Long)
    data class Totals(val tips: Int, val tippers: Int, val lamports: Long, val skrRaw: Long)

    const val SKR_DECIMALS = 6

    /** Where a tip for [pkg] goes: its proven publisher wallet whose claim is newest. Null when nobody has proven the app. */
    fun recipient(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>>, pkg: String): String? {
        val proven = verifiedPublishers[pkg].orEmpty()
        if (proven.isEmpty()) return null
        return entries.filter { it.signer in proven && (it.memo as? ReviewMemo.Claim)?.packageName == pkg }.maxByOrNull { it.blockTime }?.signer ?: proven.min()
    }

    fun counted(credit: Credit): Boolean = credit.lamports >= Payments.MIN_PAYER_LAMPORTS || credit.skrRaw >= Payments.MIN_PAYER_TOKEN_RAW

    /** Every tip that counts, newest first. [credit] reads the transaction's credit to the record's recipient; null = unknown. */
    fun tips(entries: List<LedgerEntry>, verifiedPublishers: Map<String, Set<String>>, credit: (LedgerEntry) -> Credit?): List<Tip> =
        entries.mapNotNull { e ->
            val m = e.memo as? ReviewMemo.Tip ?: return@mapNotNull null
            val pubs = verifiedPublishers[m.packageName].orEmpty()
            if (m.recipient !in pubs || e.signer in pubs || Operator.isOperator(e.signer)) return@mapNotNull null
            val c = credit(e)?.takeIf(::counted) ?: return@mapNotNull null
            Tip(e.signature, e.signer, m.packageName, m.recipient, e.blockTime, c.lamports, c.skrRaw)
        }.distinctBy { it.signature.substringBefore(':') }.sortedByDescending { it.time }

    /** Package → wallets that have tipped its developer. */
    fun tippers(tips: List<Tip>): Map<String, Set<String>> = tips.groupBy { it.packageName }.mapValues { (_, l) -> l.map { it.tipper }.toSet() }

    fun totals(tips: List<Tip>): Map<String, Totals> = tips.groupBy { it.packageName }.mapValues { (_, l) ->
        Totals(l.size, l.map { it.tipper }.toSet().size, l.sumOf { it.lamports }, l.sumOf { it.skrRaw })
    }
}
