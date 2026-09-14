package com.undersigned.app.solana

import com.undersigned.app.util.Base58
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * Pins the Kotlin port of the .skr derivation to accounts read from mainnet on 15 Sep 2026 for
 * airdropper.skr (wallet HVXVfQ…yDaD). No network: if a seed order or constant drifts, this fails.
 */
class SkrDerivationTest {
    private fun hashed(s: String) = MessageDigest.getInstance("SHA-256").digest((DomainClient.HASH_PREFIX + s).toByteArray())
    private val ans = Base58.decode(DomainClient.ANS)
    private val nameAccount = "3HWy6TPfCqW2T31WEz9wH4sdCRvo2YfHbr1W9pim7cUS"

    @Test fun `tld state is the tld_house PDA for dot-skr`() {
        val state = Pda.find(listOf("tld_house".toByteArray(), ".skr".toByteArray()), Base58.decode("TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S"))
        assertEquals(DomainClient.SKR_TLD_STATE, Base58.encode(state))
    }

    @Test fun `reverse record uses state then zero seed`() {
        val reverse = Pda.find(listOf(hashed(nameAccount), Base58.decode(DomainClient.SKR_TLD_STATE), ByteArray(32)), ans)
        assertEquals("CHt1XgQRhiPv8WaCta2HewBpVYBxeMimndjQKYiUpCbz", Base58.encode(reverse))
        // the mistake this file exists to prevent: two zero seeds derive a real-looking account that does not exist
        val wrong = Pda.find(listOf(hashed(nameAccount), ByteArray(32), ByteArray(32)), ans)
        assertNotEquals(Base58.encode(reverse), Base58.encode(wrong))
    }

    @Test fun `label hashes back to its name account`() {
        val forward = Pda.find(listOf(hashed("airdropper"), ByteArray(32), Base58.decode(DomainClient.SKR_PARENT)), ans)
        assertEquals(nameAccount, Base58.encode(forward))
    }

    @Test fun `discriminator encodes`() {
        assertEquals(8, DomainClient.DISCRIMINATOR.size)
        assertEquals(DomainClient.DISCRIMINATOR.toList(), Base58.decode(Base58.encode(DomainClient.DISCRIMINATOR)).takeLast(8).toList())
    }
}
