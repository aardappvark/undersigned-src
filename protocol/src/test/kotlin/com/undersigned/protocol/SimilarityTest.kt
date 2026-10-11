package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Identical short notes (20+ characters) by different wallets are labelled, naming the match; within three days they are "recent". */
class SimilarityTest {
    private fun sig(c: Char) = c.toString().repeat(87)
    private fun rev(c: Char, who: String, pkg: String, t: Long, text: String?) = LedgerEntry(sig(c), who, t, ReviewMemo.Review(pkg, 4, listOf(ReviewMemo.Tag.WORKS), 3, text))
    private val a = "A".repeat(44); private val b = "B".repeat(44)
    private val t0 = 1_700_000_000L

    @Test fun `identical 20-char notes by two wallets are matched and named, recent within three days`() {
        val e = listOf(rev('a', a, "com.x", t0, "Best wallet on Seeker!!"), rev('b', b, "com.x", t0 + 3600, "best wallet on seeker"))
        val f = Similarity.findings(e)
        assertEquals(setOf(sig('a'), sig('b')), f.keys)
        val fa = f[sig('a')]!!
        assertEquals(listOf(Similarity.Match(sig('b'), "com.x", b, t0 + 3600)), fa.matches)
        assertTrue(fa.recent); assertFalse(fa.copiedAcrossApps)
    }

    @Test fun `the same pair ten days apart is still a match but not recent`() {
        val e = listOf(rev('a', a, "com.x", t0, "Best wallet on Seeker!!"), rev('b', b, "com.y", t0 + 10 * 86_400, "best wallet on seeker"))
        val f = Similarity.findings(e)
        assertFalse(f[sig('a')]!!.recent); assertEquals("com.y", f[sig('a')]!!.matches.single().packageName)
    }

    @Test fun `short notes under 20 characters are exempt, and near-identical short notes are not matched`() {
        assertTrue(Similarity.findings(listOf(rev('a', a, "com.x", t0, "Works great app"), rev('b', b, "com.x", t0 + 60, "works great app"))).isEmpty())
        assertTrue(Similarity.findings(listOf(rev('a', a, "com.x", t0, "Best wallet on Seeker!!"), rev('b', b, "com.x", t0 + 60, "Best wallet on Seeker now"))).isEmpty(), "under six words only an identical note counts")
    }

    @Test fun `your own revision of the same app is never a match, and the draft nudge uses the same rule`() {
        val e = listOf(rev('a', a, "com.x", t0, "Best wallet on Seeker!!"), rev('b', a, "com.x", t0 + 60, "best wallet on seeker"))
        assertNull(Similarity.findings(e)[sig('a')])
        assertTrue(Similarity.similarToExisting("Best wallet on Seeker", e))
        assertFalse(Similarity.similarToExisting("Best wallet on Seeker", e, author = a, packageName = "com.x"))
        assertFalse(Similarity.similarToExisting("Works great app", e))
    }
}
