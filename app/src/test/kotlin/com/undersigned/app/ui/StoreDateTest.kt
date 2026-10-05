package com.undersigned.app.ui

import com.undersigned.app.ui.components.storeDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StoreDateTest {
    @Test fun `a store timestamp becomes the phone's medium date, not the raw ISO day`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.UK)
            val out = storeDate("2026-02-13T09:41:00.000Z")
            assertTrue(out, out.contains("2026") && out.contains("Feb") && !out.startsWith("2026-"))
        } finally { Locale.setDefault(before) }
    }
    // The catalogue's "updated" is a plain day. It must read as the same calendar day in every time zone, never the
    // day before (a UTC midnight shown in a zone west of UTC).
    @Test fun `a plain day keeps its calendar day in any time zone`() {
        val before = Locale.getDefault(); val tz = java.util.TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.UK)
            for (z in listOf("America/Los_Angeles", "Pacific/Kiritimati", "UTC")) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(z))
                val out = storeDate("2026-09-04")
                assertTrue("$z: $out", out.contains("4 Sept") || out.contains("4 Sep"))
            }
        } finally { Locale.setDefault(before); java.util.TimeZone.setDefault(tz) }
    }

    @Test fun `anything unparseable falls back to the raw day`() {
        assertEquals("2026-02-13", storeDate("2026-02-13 garbage"))
        assertEquals("", storeDate(""))
    }
}
