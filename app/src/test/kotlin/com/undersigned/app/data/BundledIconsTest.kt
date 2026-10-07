package com.undersigned.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped icons cover the shipped list (tools/bundle_icons.py). The property that matters is "an app in the list has
 * its icon on the phone with no network", so this reads the real assets, not a fixture.
 */
class BundledIconsTest {
    private val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
    private val index = File(assets, "icons/index.tsv").readLines().filter { it.isNotBlank() }.associate { l -> l.split('\t').let { it[0] to it[1] } }

    /** Icon addresses that are dead at the store itself (404 / storage access disabled, 8 Oct 2026): the store shows none either. */
    private val deadAtStore = setOf("cat.xiaoxi.furrend", "com.bcy.fsapp", "com.godl.app", "ltd.xyzer.app.bongocat")

    @Test fun `every listed app with an icon address ships its icon`() {
        val list = JSONArray(File(assets, "catalogue_snapshot.json").readText())
        val missing = (0 until list.length()).map { list.getJSONObject(it) }.filter { it.optString("i").isNotEmpty() }
            .map { it.getString("p") }.filter { it !in index && it !in deadAtStore }
        assertEquals("apps in the list without a shipped icon (run tools/bundle_icons.py)", emptyList<String>(), missing)
    }

    @Test fun `every index entry is a small real WebP`() {
        index.keys.forEach { pkg ->
            val f = File(assets, "icons/$pkg.webp")
            assertTrue("$pkg.webp missing", f.isFile)
            val head = f.readBytes()
            assertTrue("$pkg.webp is not WebP", head.size > 12 && String(head, 0, 4) == "RIFF" && String(head, 8, 4) == "WEBP")
            assertTrue("$pkg.webp is ${head.size} bytes", head.size < 20_000)
        }
    }
}
