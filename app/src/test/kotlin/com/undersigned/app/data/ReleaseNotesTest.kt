package com.undersigned.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store's "What's new" is third-party text; these are real shapes seen on 24 Sep 2026. */
class ReleaseNotesTest {
    @Test fun `github release markdown becomes plain text`() {
        // The Solana dApp Store's own notes, as the store API returned them.
        val raw = "## What's Changed\r\n* Automatic PR from release-v1.58.0 to main by @Sol in https://github.com/x/y/pull/1\r\n* Fix [the thing](https://x.y) **now**\r\n\r\n\r\nFull changelog"
        val out = cleanReleaseNotes(raw)!!
        assertEquals("What's Changed\n• Automatic PR from release-v1.58.0 to main by @Sol in https://github.com/x/y/pull/1\n• Fix the thing now\n\nFull changelog", out)
    }

    @Test fun `plain notes and underscores in words are left alone`() {
        assertEquals("Bug fixes and improvements.", cleanReleaseNotes("Bug fixes and improvements."))
        assertEquals("Renamed release_v1.58_notes and my_var", cleanReleaseNotes("Renamed release_v1.58_notes and my_var"))
        assertTrue(cleanReleaseNotes("v1.2.152: Aardy's Fav's - our own dApps")!!.startsWith("v1.2.152: Aardy's Fav's - our own"))
    }

    @Test fun `nothing readable is null`() {
        assertNull(cleanReleaseNotes(null)); assertNull(cleanReleaseNotes("   ")); assertNull(cleanReleaseNotes("\r\n\r\n"))
    }
}
