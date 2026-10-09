package com.undersigned.protocol

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Long review text, fully on-chain (owner, 10 Oct 2026). The review record keeps a short preview (its own text, at most
 * [ReviewMemo.MAX_TEXT_CHARS]); a [ReviewMemo.LongTextHead] beside it in the SAME transaction names a random tag, how many
 * pieces there are and a hash of the full text; the pieces ride as [ReviewMemo.TextPart]s in the same wallet approval
 * (more transactions when the text needs them). A reader shows the full text only when every piece is there, signed by the
 * review's own signer, and the hash matches; otherwise it shows the preview.
 */
object LongText {
    val TAG = Regex("[0-9a-f]{16}")
    const val MAX_CHARS = 2000
    const val MAX_BYTES = 4800
    /** Bytes of text per piece: base64 of this plus the record's prefix stays under the 566-byte memo cap. */
    const val CHUNK_BYTES = 396
    const val MAX_PARTS = 13

    fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    /** Keeps line breaks, drops other control characters, trims, and caps at [MAX_CHARS] / [MAX_BYTES]. */
    fun clean(text: String): String {
        var t = text.replace("\r\n", "\n").filter { it == '\n' || !it.isISOControl() }.trim().take(MAX_CHARS)
        while (t.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) t = t.dropLast(1)
        return t
    }

    /** The preview the review record carries: one line, no '|', the first words up to 140 characters / 280 bytes. */
    fun preview(text: String): String {
        val one = text.replace('\n', ' ').replace('|', '/').trim()
        if (one.length <= ReviewMemo.MAX_TEXT_CHARS && one.toByteArray(StandardCharsets.UTF_8).size <= ReviewMemo.MAX_TEXT_BYTES) return one
        var cut = one.take(ReviewMemo.MAX_TEXT_CHARS - 1)
        while (cut.toByteArray(StandardCharsets.UTF_8).size > ReviewMemo.MAX_TEXT_BYTES - 3) cut = cut.dropLast(1)
        val space = cut.lastIndexOf(' '); if (space > 80) cut = cut.take(space)
        return cut.trimEnd() + "…"
    }

    /** Whether [text] needs pieces (it does not fit the review record as it is). */
    fun needsParts(text: String): Boolean = preview(text) != text

    /** Splits [text] into pieces of at most [CHUNK_BYTES] UTF-8 bytes, never inside a character. */
    fun split(text: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var bytes = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i); val s = String(Character.toChars(cp)); val b = s.toByteArray(StandardCharsets.UTF_8).size
            if (bytes + b > CHUNK_BYTES) { out += cur.toString(); cur.setLength(0); bytes = 0 }
            cur.append(s); bytes += b; i += Character.charCount(cp)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** The records for a long text: the head (beside the review) and the pieces. Null when the text fits the review alone. */
    fun records(text: String, tag: String): Pair<ReviewMemo.LongTextHead, List<ReviewMemo.TextPart>>? {
        val t = clean(text); if (!needsParts(t)) return null
        val parts = split(t); require(parts.size <= MAX_PARTS) { "text too long" }
        return ReviewMemo.LongTextHead(tag, parts.size, hash(t)) to parts.mapIndexed { i, c -> ReviewMemo.TextPart(tag, i, c) }
    }

    /** Review record id -> its full text, where every piece is on the ledger from the review's signer and the hash matches. */
    fun assemble(entries: List<LedgerEntry>): Map<String, String> {
        val heads = entries.filter { it.memo is ReviewMemo.LongTextHead }.groupBy { it.signature.substringBefore(':') }
        if (heads.isEmpty()) return emptyMap()
        val parts = entries.filter { it.memo is ReviewMemo.TextPart }.groupBy { it.signer to (it.memo as ReviewMemo.TextPart).tag }
        val out = HashMap<String, String>()
        for (e in entries) {
            if (e.memo !is ReviewMemo.Review) continue
            val h = heads[e.signature.substringBefore(':')]?.firstOrNull { it.signer == e.signer }?.memo as? ReviewMemo.LongTextHead ?: continue
            val mine = parts[e.signer to h.tag].orEmpty().map { it.memo as ReviewMemo.TextPart }.associateBy { it.index }
            if ((0 until h.parts).any { it !in mine }) continue
            val full = (0 until h.parts).joinToString("") { mine.getValue(it).chunk }
            if (hash(full) == h.hash) out[e.signature] = full
        }
        return out
    }
}
