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
    @Test fun `anything unparseable falls back to the raw day`() {
        assertEquals("2026-02-13", storeDate("2026-02-13 garbage"))
        assertEquals("", storeDate(""))
    }
}
