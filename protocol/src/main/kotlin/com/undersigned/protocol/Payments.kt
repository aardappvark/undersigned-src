package com.undersigned.protocol

/**
 * Publisher-confirmed payment boards. A dApp's payments are counted only when BOTH sides say so:
 *  - the treasury wallet signs `SRL1|trs|<package>|<code>` ("payments to me marked <code> are for <package>"), which a
 *    wallet cannot do for an address it does not control, and
 *  - the website the store lists for the package names that wallet on a `treasury: <address>` line of
 *    `/.well-known/undersigned.txt`, which nobody but the publisher can write.
 * Either side withdraws it: `SRL1|trx|<package>` from the treasury, or deleting the line.
 *
 * Only transfers that carry the declared mark count, so a treasury shared by many apps (the fleet's is) splits by app,
 * and grants and top-ups stay out. The mark is either a memo code (2–31 of A-Za-z0-9_-, the transfer's memo starts with
 * it) or a REFERENCE account (a base58 address the payment transaction includes, the Solana Pay pattern). The fleet's
 * own ledger attributed 97 of its 100 payments by reference and only 33 carried a memo at all (reconciled 28 Sep 2026),
 * so memo-only attribution missed most of an app's history. Payments from the publisher's own listed wallets (the
 * owner testing their own checkout — 63% of the fleet's inbound value) are counted apart, never in the total. Readers label the result "payments
 * received on-chain": off-chain income is invisible to this, and nothing here is a statement about profit.
 */
object Payments {
    data class Claim(val packageName: String, val treasury: String, val code: String, val since: Long, val signature: String) {
        val byReference: Boolean get() = isReference(code)
    }
    /** One transfer in: [tokens] maps mint → raw units credited to the treasury's token accounts. */
    data class Payment(val signature: String, val payer: String, val time: Long, val lamports: Long, val tokens: Map<String, Long>)
    data class Month(val key: String, val payments: Int, val lamports: Long, val tokens: Map<String, Long>)
    /** [ownPayments] = payments from the publisher's own wallets, left out of every other number. */
    data class Tally(val payments: Int, val payers: Int, val attestedPayers: Int, val lamports: Long, val tokens: Map<String, Long>, val months: List<Month>, val first: Long?, val last: Long?, val ownPayments: Int = 0)

    private val CODE = Regex("^[A-Za-z0-9_-]{2,31}$")
    private val REFERENCE = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    fun isReference(code: String) = REFERENCE.matches(code)
    fun validCode(code: String) = CODE.matches(code) || isReference(code)

    /** Active claims: the newest `trs`/`trx` per (treasury, package) decides, as with flags and votes. */
    fun claims(entries: List<LedgerEntry>): List<Claim> =
        entries.filter { it.memo is ReviewMemo.Treasury || it.memo is ReviewMemo.Untreasury }
            .groupBy { it.signer to ((it.memo as? ReviewMemo.Treasury)?.packageName ?: (it.memo as ReviewMemo.Untreasury).packageName) }
            .mapNotNull { (k, v) -> val latest = v.maxWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature })
                (latest.memo as? ReviewMemo.Treasury)?.let { Claim(k.second, k.first, it.code, latest.blockTime, latest.signature) } }

    /** Every wallet address a well-known file names, on any line: the publisher's own wallets. */
    fun listedWallets(body: String): Set<String> =
        body.lines().mapNotNull { Regex("([1-9A-HJ-NP-Za-km-z]{32,44})\\s*$").find(it.substringBefore('#'))?.groupValues?.get(1) }.toSet()

    /** Wallets a well-known file names as treasuries: `treasury: <address>` (or `=`), one per line, case-insensitive key. */
    fun listedTreasuries(body: String): Set<String> =
        body.lines().mapNotNull { Regex("^\\s*treasury\\s*[:=]\\s*([1-9A-HJ-NP-Za-km-z]{32,44})\\s*$", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }.toSet()

    /**
     * Whether a transaction's memo summary carries [code] as its first word. The RPC summarises a transaction's memos as
     * "[len] text" joined by "; " (and only the FIRST memo for some nodes), so each part is checked. "UNS" matches
     * "[3] UNS" and "UNS:order-7"; it does not match "UNSX" or "[12] SRL1|rev|…".
     */
    fun memoMatches(summary: String?, code: String): Boolean {
        summary ?: return false
        return summary.split("; ").any { part ->
            val body = part.trim().replace(Regex("^\\[\\d+]\\s*"), "")
            body.split(Regex("[^A-Za-z0-9_-]"), limit = 2).firstOrNull() == code
        }
    }

    /** Totals, distinct payers, and payers holding an attested-device credential, by calendar month (UTC). The treasury paying itself never counts. */
    fun tally(payments: List<Payment>, treasury: String, own: Set<String> = emptySet(), attested: (String) -> Boolean): Tally {
        val paid = payments.filter { it.payer != treasury && (it.lamports > 0 || it.tokens.values.any { v -> v > 0 }) }.distinctBy { it.signature }
        val real = paid.filter { it.payer !in own }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").withZone(java.time.ZoneOffset.UTC)
        fun sumTokens(l: List<Payment>) = l.flatMap { it.tokens.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.filterValues { it > 0 }
        val months = real.groupBy { fmt.format(java.time.Instant.ofEpochSecond(it.time)) }.toSortedMap()
            .map { (k, l) -> Month(k, l.size, l.sumOf { it.lamports }, sumTokens(l)) }
        val payers = real.map { it.payer }.toSet()
        return Tally(real.size, payers.size, payers.count(attested), real.sumOf { it.lamports }, sumTokens(real), months, real.minOfOrNull { it.time }, real.maxOfOrNull { it.time }, paid.size - real.size)
    }
}
