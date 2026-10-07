package com.undersigned.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Share to Undersigned": what a shared text must resolve to. The property is the package the owner meant, not a URL shape. */
class NavIntentsTest {
    @Test fun `a store link's id is found first`() {
        val got = NavIntents.packagesIn("Check out Magic Eden on the dApp Store https://solanadappstore.com/details?id=io.magiceden.twa")
        assertEquals("io.magiceden.twa", got.first { it != "solanadappstore.com" })
    }

    @Test fun `a bare package and a deep link both resolve`() {
        assertEquals(listOf("com.adappvark.toolkit"), NavIntents.packagesIn("com.adappvark.toolkit"))
        assertTrue("io.magiceden.twa" in NavIntents.packagesIn("solanadappstore://details?id=io.magiceden.twa"))
    }

    @Test fun `www hosts and plain words are not packages`() {
        assertTrue(NavIntents.packagesIn("Magic Eden is great. Try it").isEmpty())
        assertTrue(NavIntents.packagesIn("https://www.example.com/").none { it.startsWith("www.") })
    }
}
