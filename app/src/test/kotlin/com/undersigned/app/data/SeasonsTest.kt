package com.undersigned.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SeasonsTest {
    private val seasons = Seasons.parse(File("src/main/assets/seasons.json").readText())
    private fun at(iso: String) = Seasons.at(java.time.Instant.parse(iso).epochSecond, seasons)?.id

    @Test fun boundariesAreWholeUtcDays() {
        assertEquals(2, seasons.size)
        assertNull(at("2026-07-06T23:59:59Z")); assertEquals("summer26", at("2026-07-07T00:00:00Z"))
        assertEquals("summer26", at("2026-08-30T23:59:59Z")); assertNull(at("2026-08-31T00:00:00Z"))
        assertEquals("s1", at("2025-12-01T12:00:00Z")); assertNull(at("2026-03-18T00:00:00Z"))   // Season 2 is not listed
    }

    @Test fun badJsonIsNoSeasons() { assertEquals(0, Seasons.parse("{").size) }
}
