package com.undersigned.app.solana

import com.undersigned.app.AppConfig
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A licence is the payment. Until 20 Sep 2026 it also wrote `SRL1|pro|12` into the same transaction, which put
 * a pipe-delimited payload where the fleet convention wants a three-letter code — and since the RPC summarises
 * a transaction with its FIRST memo, that hid the code from every treasury scan. The payload is gone; the
 * wallets that bought one under the old shape must keep it, which is what the first test here holds.
 */
class ProLicenceTest {
    private val wallet = "8oLtrvMnk7bLzjYzWQdo66kJsvkG4CYL1QKoeKbBHT8F"
    private val paidAt = 1_789_000_000L
    private fun entry(sig: String) = LedgerEntry(sig, wallet, paidAt, ReviewMemo.Pro(AppConfig.Payment.TERM_MONTHS))
    private fun meta(sol: Long = 0, skr: Long = 0) = mapOf("sig" to TxMeta(0L, true, sol, skr))

    @Test fun `a licence bought under the old shape still reads`() {
        val s = ProLicence.of(listOf(entry("sig")), meta(skr = AppConfig.Payment.skrRaw(276.0)), wallet, now = paidAt + 86_400)
        assertTrue("the owner's 276 SKR licence must survive the change", s!!.active)
        assertEquals("SKR", s.rail)
        assertTrue(s.owned)
    }

    @Test fun `a licence bought under the new shape reads the same`() {
        // Same entry, but synthesised by the reader from the payment rather than decoded from a memo.
        val s = ProLicence.of(listOf(entry("sig")), meta(sol = AppConfig.Payment.lamports(AppConfig.Payment.PRO_SOL)), wallet, now = paidAt + 86_400)
        assertTrue(s!!.active); assertEquals("SOL", s.rail)
    }

    @Test fun `a term that was never paid for is not a licence`() {
        // The floor is the lowest price ever charged (276 SKR): one unit under it is not a licence.
        assertNull(ProLicence.of(listOf(entry("sig")), meta(skr = AppConfig.Payment.skrRaw(276.0) - 1), wallet, now = paidAt))
        assertNull(ProLicence.of(listOf(entry("sig")), meta(sol = AppConfig.Payment.lamports(AppConfig.Payment.PRO_SOL) - 1), wallet, now = paidAt))
        assertNull(ProLicence.of(listOf(entry("sig")), emptyMap(), wallet, now = paidAt))
    }

    @Test fun `the paid test is what the floor says, on either rail`() {
        val p = AppConfig.Payment
        assertTrue(p.isPaidLicence(p.floorLamports, 0))
        assertTrue(p.isPaidLicence(0, p.floorSkrRaw))
        assertTrue(!p.isPaidLicence(p.floorLamports - 1, p.floorSkrRaw - 1))
        assertTrue(!p.isPaidLicence(0, 0))
    }

    @Test fun `a term runs a year and then stops`() {
        val s = ProLicence.of(listOf(entry("sig")), meta(skr = AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR)), wallet, now = paidAt)
        assertEquals(365L, s!!.daysLeft(paidAt))
        assertTrue(!ProLicence.of(listOf(entry("sig")), meta(skr = AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR)), wallet, now = paidAt + 366L * 86_400)!!.active)
    }
}
