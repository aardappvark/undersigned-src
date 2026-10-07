package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A treasury listed by the app's proven publisher (the treasury never signs) counts; from anyone else it does not. */
class TreasuryForTest {
    private val treasury = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
    private val code = "AZFbwj8hARyRpAVU2umxAQ2fqscS3CfwPYpYzYigTgQr"
    private fun id(c: Char) = "1" + c.toString().repeat(86)
    private fun trd(sig: Char, by: String, t: Long = 1) = LedgerEntry(id(sig), by, t, ReviewMemo.TreasuryFor("com.x", code, treasury))

    @Test fun `wire round trip, and a bad treasury address is rejected`() {
        val m = ReviewMemo.TreasuryFor("com.x", code, treasury)
        assertEquals(m, ReviewMemo.decode(ReviewMemo.encode(m)))
        assertNull(ReviewMemo.decode("SRL1|trd|com.x|$code|not-an-address"))
    }

    @Test fun `only the proven publisher's listing counts, and it lists the named treasury`() {
        val isPub = { w: String, pkg: String -> w == "pub" && pkg == "com.x" }
        val c = Payments.claims(listOf(trd('A', "pub")), isPublisher = isPub).single()
        assertEquals(treasury, c.treasury); assertEquals("pub", c.signedBy); assertEquals(code, c.code)
        assertTrue(Payments.claims(listOf(trd('B', "stranger")), isPublisher = isPub).isEmpty())
    }

    @Test fun `the publisher withdraws it, and an unpaid listing lists nothing`() {
        val isPub = { w: String, _: String -> w == "pub" }
        val withdrawn = listOf(trd('A', "pub", 1), LedgerEntry(id('C'), "pub", 2, ReviewMemo.Untreasury("com.x")))
        assertTrue(Payments.claims(withdrawn, isPublisher = isPub).isEmpty())
        assertTrue(Payments.claims(listOf(trd('A', "pub")), paid = { false }, isPublisher = isPub).isEmpty())
    }
}
