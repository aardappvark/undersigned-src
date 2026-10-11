package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The `ctg` record: a Pro reviewer's own tags, beside the fixed vocabulary; attached only to its own review, shown only for Pro. */
class CustomTagsTest {
    private fun sig(c: Char) = c.toString().repeat(87)
    private val pro = "P".repeat(44); private val free = "F".repeat(44)

    @Test fun roundTripsAndRejectsBadValues() {
        val c = ReviewMemo.CustomTags("com.example.app", listOf("offline-maps", "dark-mode"))
        val wire = ReviewMemo.encode(c)
        assertEquals("SRL1|ctg|com.example.app|offline-maps,dark-mode", wire)
        assertEquals(c, ReviewMemo.decode(wire))
        for (bad in listOf(
            "SRL1|ctg|com.example.app|a",                       // too short
            "SRL1|ctg|com.example.app|Offline",                 // upper case
            "SRL1|ctg|com.example.app|has space",
            "SRL1|ctg|com.example.app|one,two,three",           // more than two
            "SRL1|ctg|com.example.app|same,same",               // duplicates
            "SRL1|ctg|com.example.app|works",                   // a fixed tag's wire
            "SRL1|ctg|com.example.app|scam",                    // blocked
            "SRL1|ctg|com.example.app|earn",
            "SRL1|ctg|com.example.app|trailing-",
            "SRL1|ctg|com.example.app|abcdefghijklmnopqrstu",   // 21 chars
            "SRL1|ctg|bad pkg|fine",
            "SRL1|ctg|com.example.app")) assertNull(ReviewMemo.decode(bad), bad)
        assertTrue(ReviewMemo.validCustomTag("a1"))
        assertFalse(ReviewMemo.validCustomTag("-ab"))
    }

    @Test fun attachesOnlyToItsOwnReviewAndOnlyForPro() {
        val t = 1_700_000_000L
        val paidPro = LedgerEntry(sig('p'), pro, t - 86_400, ReviewMemo.Pro(12))
        val proReview = LedgerEntry(sig('a'), pro, t, ReviewMemo.Review("com.a", 5, listOf(ReviewMemo.Tag.WORKS), 3, null))
        val proTags = LedgerEntry(sig('a') + ":1", pro, t, ReviewMemo.CustomTags("com.a", listOf("offline-maps")))
        val freeReview = LedgerEntry(sig('b'), free, t, ReviewMemo.Review("com.a", 4, listOf(ReviewMemo.Tag.FAST), 3, null))
        val freeTags = LedgerEntry(sig('b') + ":1", free, t, ReviewMemo.CustomTags("com.a", listOf("nice-ui")))
        val strayTags = LedgerEntry(sig('c'), pro, t, ReviewMemo.CustomTags("com.a", listOf("alone")))          // no review in its transaction
        val otherApp = LedgerEntry(sig('a') + ":2", pro, t, ReviewMemo.CustomTags("com.b", listOf("wrong-app")))  // another package, same tx
        val entries = listOf(paidPro, proReview, proTags, freeReview, freeTags, strayTags, otherApp)
        assertEquals(mapOf(sig('a') to listOf("offline-maps"), sig('b') to listOf("nice-ui")), Usage.customTags(entries))
        val paid: (LedgerEntry) -> Boolean = { it.memo is ReviewMemo.Pro }
        assertEquals(mapOf(sig('a') to listOf("offline-maps")), Usage.proCustomTags(entries, paid), "the free wallet's tags are dropped")
        assertEquals(emptyMap(), Usage.proCustomTags(entries) { false }, "an unpaid licence grants nothing")
    }
}
