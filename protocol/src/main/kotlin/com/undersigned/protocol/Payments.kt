package com.undersigned.protocol

/**
 * Publisher-confirmed payment boards. A dApp's payments are counted only when BOTH sides say so:
 *  - the treasury wallet signs `SRL1|trs|<package>|<code>` ("payments to me marked <code> are for <package>"), which a
 *    wallet cannot do for an address it does not control, and
 *  - the website the store lists for the package names that wallet on a `treasury: <address>` line of
 *    `/.well-known/undersigned.txt`, which nobody but the publisher can write.
 * Either side withdraws it: `SRL1|trx|<package>` from the treasury, or deleting the line.
 *
 * Only transfers whose memo starts with the declared code count, so a treasury shared by many apps (the fleet's is)
 * splits by app, and grants, top-ups and the owner's own transfers stay out. Readers label the result "payments
 * received on-chain": off-chain income is invisible to this, and nothing here is a statement about profit.
 */
object Payments {
    data class Claim(val packageName: String, val treasury: String, val code: String, val since: Long, val signature: String)
    /** One transfer in: [tokens] maps mint → raw units credited to the treasury's token accounts. */
    data class Payment(val signature: String, val payer: String, val time: Long, val lamports: Long, val tokens: Map<String, Long>)
    data class Month(val key: String, val payments: Int, val lamports: Long, val tokens: Map<String, Long>)
    data class Tally(val payments: Int, val payers: Int, val attestedPayers: Int, val lamports: Long, val tokens: Map<String, Long>, val months: List<Month>, val first: Long?, val last: Long?)

    private val CODE = Regex("^[A-Za-z0-9_-]{2,32}$")
    fun validCode(code: String) = CODE.matches(code)

    /** Active claims: the newest `trs`/`trx` per (treasury, package) decides, as with flags and votes. */
    fun claims(entries: List<LedgerEntry>): List<Claim> =
        entries.filter { it.memo is ReviewMemo.Treasury || it.memo is ReviewMemo.Untreasury }
            .groupBy { it.signer to ((it.memo as? ReviewMemo.Treasury)?.packageName ?: (it.memo as ReviewMemo.Untreasury).packageName) }
            .mapNotNull { (k, v) -> val latest = v.maxWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature })
                (latest.memo as? ReviewMemo.Treasury)?.let { Claim(k.second, k.first, it.code, latest.blockTime, latest.signature) } }

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
    fun tally(payments: List<Payment>, treasury: String, attested: (String) -> Boolean): Tally {
        val real = payments.filter { it.payer != treasury && (it.lamports > 0 || it.tokens.values.any { v -> v > 0 }) }.distinctBy { it.signature }
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").withZone(java.time.ZoneOffset.UTC)
        fun sumTokens(l: List<Payment>) = l.flatMap { it.tokens.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.filterValues { it > 0 }
        val months = real.groupBy { fmt.format(java.time.Instant.ofEpochSecond(it.time)) }.toSortedMap()
            .map { (k, l) -> Month(k, l.size, l.sumOf { it.lamports }, sumTokens(l)) }
        val payers = real.map { it.payer }.toSet()
        return Tally(real.size, payers.size, payers.count(attested), real.sumOf { it.lamports }, sumTokens(real), months, real.minOfOrNull { it.time }, real.maxOfOrNull { it.time })
    }
}
