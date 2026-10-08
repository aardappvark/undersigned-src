package com.undersigned.protocol

/**
 * What a reviewer has staked, for TRANSPARENCY only (owner, 9 Oct 2026: "show the wallet stake … that doesn't amend the
 * weighting … if the viewer allocates more trust in someone with more staked, that's up to them"). Nothing here feeds a
 * weight or a score; readers print it beside a review with the date it was read.
 */
object StakeView {
    const val STAKE_PROGRAM = "Stake11111111111111111111111111111111111111"
    /** A stake account is 200 bytes; the withdrawer authority sits at byte 44 (Meta: tag u32, rent reserve u64, staker, withdrawer). */
    const val STAKE_ACCOUNT_SIZE = 200
    const val WITHDRAWER_OFFSET = 44
    /** Today's rent floor for a 200-byte account, used when an account carries no readable reserve. */
    const val RENT_FLOOR_LAMPORTS = 2_077_224L

    /**
     * SOL staked from a wallet: per stake account, its balance minus its rent reserve, summed. Read from `lamports` and the
     * account's own `rentExemptReserve` only: `delegation.stake` is a historical record that survives a withdrawal (measured
     * 10 Sep 2026: one wallet's delegations claimed 108.47 SOL where its accounts held 9.78). [accounts] are (lamports, the
     * account's first 12 bytes or more).
     */
    fun solStaked(accounts: List<Pair<Long, ByteArray>>): Double = accounts.sumOf { (lamports, head) ->
        val tag = if (head.size >= 4) (head[0].toInt() and 0xff) or ((head[1].toInt() and 0xff) shl 8) else -1
        val reserve = if ((tag == 1 || tag == 2) && head.size >= 12)
            java.nio.ByteBuffer.wrap(head, 4, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).long.takeIf { it > 0 } ?: RENT_FLOOR_LAMPORTS
        else RENT_FLOOR_LAMPORTS
        (lamports - minOf(reserve, lamports)).coerceAtLeast(0L)
    } / 1_000_000_000.0

    /** A review's stake as read, and when: what readers print beside it. */
    data class Snapshot(val skr: Double, val sol: Double?, val readAt: Long)
}
