package com.undersigned.protocol

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Long review text on-chain: preview, pieces under the memo cap, and reassembly only when every piece checks out. */
class LongTextTest {
    private val tag = "0123456789abcdef"
    private val long = ("The swap screen loads fast and the wallet hand-off works every time. 🚀 Ünïcödé — 日本語のテキストも。\n").repeat(18).trim()

    @Test fun piecesFitTheMemoCapAndRoundTrip() {
        val (head, parts) = LongText.records(long, tag)!!
        assertTrue(parts.size in 2..LongText.MAX_PARTS)
        parts.forEach { p ->
            val wire = ReviewMemo.encode(p)
            assertTrue(wire.toByteArray(StandardCharsets.UTF_8).size <= ReviewMemo.MAX_MEMO_BYTES, "${wire.length}")
            assertEquals(p, ReviewMemo.decode(wire))
        }
        assertEquals(head, ReviewMemo.decode(ReviewMemo.encode(head)))
        assertEquals(LongText.clean(long), parts.joinToString("") { it.chunk })
        val preview = LongText.preview(long)
        assertTrue(preview.length <= ReviewMemo.MAX_TEXT_CHARS && preview.endsWith("…") && '\n' !in preview)
        assertNull(LongText.records("Short and sweet.", tag))
    }

    @Test fun reassemblesOnlyWithEveryPieceFromTheSameSignerAndTheRightHash() {
        val (head, parts) = LongText.records(long, tag)!!
        val review = LedgerEntry("tx0:0", "W", 1, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, LongText.preview(long)))
        val headE = LedgerEntry("tx0:1", "W", 1, head)
        val pieces = parts.mapIndexed { i, p -> LedgerEntry("tx${i + 1}:0", "W", 1, p) }
        assertEquals(LongText.clean(long), LongText.assemble(listOf(review, headE) + pieces).getValue("tx0:0"))
        // A missing piece, a piece from someone else, or a changed piece: the preview stands.
        assertTrue(LongText.assemble(listOf(review, headE) + pieces.drop(1)).isEmpty())
        val forged = pieces.mapIndexed { i, e -> if (i == 0) e.copy(signer = "OTHER") else e }
        assertTrue(LongText.assemble(listOf(review, headE) + forged).isEmpty())
        val changed = pieces.mapIndexed { i, e -> if (i == 0) e.copy(memo = ReviewMemo.TextPart(tag, 0, "x" + (e.memo as ReviewMemo.TextPart).chunk.drop(1))) else e }
        assertTrue(LongText.assemble(listOf(review, headE) + changed).isEmpty())
    }

    @Test fun splittingNeverBreaksACharacter() {
        val emoji = "🚀".repeat(300)
        val parts = LongText.split(emoji)
        assertEquals(emoji, parts.joinToString(""))
        parts.forEach { assertTrue(it.toByteArray(StandardCharsets.UTF_8).size <= LongText.CHUNK_BYTES) }
    }
}
