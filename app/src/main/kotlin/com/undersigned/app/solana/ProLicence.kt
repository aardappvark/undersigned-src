package com.undersigned.app.solana

import com.undersigned.app.AppConfig
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo

/** A wallet's Pro term as read from the ledger: no server, no receipt file, nothing stored about the buyer. */
data class ProStatus(val active: Boolean, val expiresAt: Long, val paidAt: Long, val signature: String, val rail: String, val amountLabel: String, val sharedFrom: String? = null) {
    /** Days until the term ends, rounded up: the day it was paid reads as the full term, not one short. */
    fun daysLeft(now: Long = System.currentTimeMillis() / 1000): Long = ((expiresAt - now + 86399L) / 86400L).coerceAtLeast(0L)
    /** Only a paid term can be shared or renewed from this wallet; a shared seat cannot be re-shared. */
    val owned: Boolean get() = sharedFrom == null
}

/**
 * Pro is a fact on the chain, so it is read from there rather than trusted from anything stored locally —
 * any device holding the wallet has it. A `pro` record counts only when the transaction it rode in on
 * actually credited the treasury with at least the floor price on either rail (read from balances). Terms
 * chain: a renewal signed before the current term ends starts when that term ends, never earlier.
 */
object ProLicence {
    private const val DAY = 86_400L

    /**
     * @param granters wallets this device was told may have shared Pro with [wallet]. A grant proves only that a
     * wallet SAID it was sharing; the granter's own paid term is checked too, so nobody can mint Pro for anyone.
     */
    fun of(entries: List<LedgerEntry>, meta: Map<String, TxMeta>, wallet: String?, granters: Collection<String> = emptyList(), now: Long = System.currentTimeMillis() / 1000): ProStatus? {
        wallet ?: return null
        paid(entries, meta, wallet, now)?.let { return it }
        for (g in granters.filter { it != wallet }) {
            val theirs = paid(entries, meta, g, now) ?: continue
            if (!com.undersigned.protocol.ProSharing.holdsSeat(g, wallet, com.undersigned.protocol.ProSharing.recordsOf(entries, g))) continue
            return theirs.copy(sharedFrom = g)
        }
        return null
    }

    private fun paid(entries: List<LedgerEntry>, meta: Map<String, TxMeta>, wallet: String, now: Long): ProStatus? {
        data class Paid(val e: LedgerEntry, val months: Int, val rail: String, val label: String)
        val paid = entries.filter { it.signer == wallet && it.memo is ReviewMemo.Pro }.mapNotNull { e ->
            val m = meta[e.signature.substringBefore(':')] ?: return@mapNotNull null
            val months = (e.memo as ReviewMemo.Pro).months
            when {
                m.paidLamports >= AppConfig.Payment.floorLamports -> Paid(e, months, "SOL", String.format(java.util.Locale.US, "%s SOL", trim(m.paidLamports / 1e9)))
                m.paidSkr >= AppConfig.Payment.floorSkrRaw -> Paid(e, months, "SKR", String.format(java.util.Locale.US, "%,.0f SKR", m.paidSkr / 1e6))
                else -> null   // a `pro` record whose transaction paid nothing buys nothing
            }
        }.sortedBy { it.e.blockTime }
        if (paid.isEmpty()) return null
        var expiry = 0L
        for (p in paid) expiry = maxOf(expiry, p.e.blockTime) + p.months * 365L * DAY / 12L
        val last = paid.last()
        return ProStatus(expiry > now, expiry, last.e.blockTime, last.e.signature, last.rail, last.label)
    }

    private fun trim(v: Double): String = java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
