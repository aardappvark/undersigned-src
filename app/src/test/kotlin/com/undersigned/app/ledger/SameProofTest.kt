package com.undersigned.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real shapes from 26 Sep 2026: the Toolkit and DivineQuotes both list the same repo under publisher "Befran". */
class SameProofTest {
    private val pub = "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD"
    private val sites = mapOf(
        "com.adappvark.toolkit" to "https://github.com/AardAppvark/ADappvarkToolkit",
        "com.divinequotes.sol" to "https://github.com/AardAppvark/ADappvarkToolkit",
        "com.same.repo.other.spelling" to "http://github.com/aardappvark/adappvarktoolkit/",
        "com.same.owner.other.repo" to "https://github.com/AardAppvark/SomethingElse",
        "com.unrelated" to "https://example.org",
    )

    @Test fun `a verified publisher covers every app listing the same proof file`() {
        val out = PublisherVerifier.expandToSameProof(mapOf("com.adappvark.toolkit" to setOf(pub)), sites.keys + "com.no.website") { sites[it] }
        assertEquals(setOf(pub), out["com.adappvark.toolkit"])
        assertEquals("same repo, same proof", setOf(pub), out["com.divinequotes.sol"])
        assertEquals("scheme, case and trailing slash do not matter", setOf(pub), out["com.same.repo.other.spelling"])
    }

    @Test fun `a different repo, an unrelated site, or no site is never covered`() {
        val out = PublisherVerifier.expandToSameProof(mapOf("com.adappvark.toolkit" to setOf(pub)), sites.keys + "com.no.website") { sites[it] }
        assertNull("same GitHub owner but a different repo is a different proof", out["com.same.owner.other.repo"])
        assertNull(out["com.unrelated"]); assertNull(out["com.no.website"])
    }

    @Test fun `nothing verified means nothing expands`() {
        assertTrue(PublisherVerifier.expandToSameProof(emptyMap(), sites.keys) { sites[it] }.isEmpty())
    }
}
