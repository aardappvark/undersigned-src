package com.undersigned.protocol

import java.security.MessageDigest

/**
 * Sharing one Pro term across a few wallets of one person, recorded on the ledger.
 *
 * The wallet that paid signs `shr|<tag>`; only that wallet can produce the signature, so the record IS the
 * grant: public, timestamped, revocable, no contract. The other wallet is written as a HASH of the pair,
 * because a memo naming both addresses would publish, permanently and for anyone, that they belong to one
 * person. The granted wallet already knows the payer (it was told), so it can compute the tag and find its
 * grant; a stranger cannot enumerate the pairs.
 *
 * Seats: the payer plus [SHAREABLE] others. Replayed in signing order: a grant for a wallet already seated
 * changes nothing; a new grant takes a seat and, when all are taken, displaces the longest-seated; a revoke
 * frees a seat at once. Auditable, not enforceable — the app runs on the device and the record proves who
 * granted what.
 */
object ProSharing {
    const val SEATS = 3
    const val SHAREABLE = SEATS - 1

    /** First 16 bytes of SHA-256 over "granter:grantee", hex. Order matters: a grant cannot be read backwards. */
    fun tag(granter: String, grantee: String): String =
        MessageDigest.getInstance("SHA-256").digest("$granter:$grantee".toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }

    data class Record(val tag: String, val time: Long, val revoked: Boolean)

    /** The granter's share statements, in signing order, from the ledger. */
    fun recordsOf(entries: List<LedgerEntry>, granter: String): List<Record> = entries.mapNotNull { e ->
        if (e.signer != granter) null else when (val m = e.memo) {
            is ReviewMemo.Share -> Record(m.tag, e.blockTime, false)
            is ReviewMemo.Unshare -> Record(m.tag, e.blockTime, true)
            else -> null
        }
    }

    /** Seated tags, longest-seated first. */
    fun seatOrder(records: List<Record>, seats: Int = SHAREABLE): List<String> {
        if (seats <= 0 || records.isEmpty()) return emptyList()
        val seated = ArrayList<String>()
        // A revoke sorts after a grant at the same instant: signed in one moment, the safer reading is "not held".
        for (r in records.sortedWith(compareBy<Record> { it.time }.thenBy { it.revoked })) {
            if (r.revoked) { seated.remove(r.tag); continue }
            if (r.tag in seated) continue
            seated.add(r.tag)
            if (seated.size > seats) seated.removeAt(0)
        }
        return seated
    }

    fun activeSeats(records: List<Record>, seats: Int = SHAREABLE): Set<String> = seatOrder(records, seats).toSet()
    /** The tag one more grant would displace, or null when a seat is free. Asked BEFORE a grant is composed. */
    fun wouldDisplace(records: List<Record>, seats: Int = SHAREABLE): String? = seatOrder(records, seats).let { if (it.size < seats) null else it.first() }
    fun holdsSeat(granter: String, grantee: String, records: List<Record>, seats: Int = SHAREABLE): Boolean = tag(granter, grantee) in activeSeats(records, seats)
}
