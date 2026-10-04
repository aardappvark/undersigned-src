package com.undersigned.api

import com.undersigned.protocol.Apk
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real APKs, when present on this machine (UNDERSIGNED_TEST_APKS=dir): a fleet build carrying the publisher line and the
 * Toolkit's store download. Expected values were read independently with apksigner and aapt2 (28 Sep 2026).
 */
class ApkTest {
    private val dir = System.getenv("UNDERSIGNED_TEST_APKS")?.let(::File)

    @Test fun `a fleet build names both publisher wallets`() {
        val f = dir?.resolve("solclock-debug.apk"); assumeTrue(f?.exists() == true)
        assertEquals(setOf("DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q", "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD"), Apk.read(f!!).declared)
    }

    @Test fun `the store's Toolkit build is signed by the certificate the store lists, and names nobody yet`() {
        val f = dir?.resolve("toolkit-store.apk"); assumeTrue(f?.exists() == true)
        val facts = Apk.read(f!!)
        assertTrue("5bca6fe9b3d0" in facts.certSha256.joinToString(), "certs: ${facts.certSha256}")
        assertEquals(emptySet(), facts.declared)
    }
}
