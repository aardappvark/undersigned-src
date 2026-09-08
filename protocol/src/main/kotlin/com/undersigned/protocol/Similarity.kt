package com.undersigned.protocol

/**
 * Near-duplicate detection for review notes. Deliberately conservative: it LABELS, it never hides.
 * A note is "similar" to another when both have at least [MIN_WORDS] words and their 3-word shingle
 * sets overlap with Jaccard ≥ [THRESHOLD]. Short notes ("Works great") are exempt — many honest people
 * write those. Exact copies by the SAME wallet across different apps are reported separately as
 * "copied across apps", which is the pattern automated review activity shows; a different person writing the same
 * sentence about the same app is not.
 */
object Similarity {
    const val MIN_WORDS = 6
    const val THRESHOLD = 0.6

    fun normalise(text: String): List<String> = text.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun shingles(words: List<String>, n: Int = 3): Set<String> = if (words.size < n) emptySet() else (0..words.size - n).map { words.subList(it, it + n).joinToString(" ") }.toSet()
    fun jaccard(a: Set<String>, b: Set<String>): Double { if (a.isEmpty() || b.isEmpty()) return 0.0; val i = a.intersect(b).size; return i.toDouble() / (a.size + b.size - i) }

    data class Finding(val recordId: String, val similarTo: List<String>, val copiedAcrossApps: Boolean)

    /** Findings for every review with a note; O(n²) over notes, fine for thousands. */
    fun findings(entries: List<LedgerEntry>): Map<String, Finding> {
        val notes = entries.mapNotNull { e -> (e.memo as? ReviewMemo.Review)?.text?.let { t -> val w = normalise(t); if (w.size >= MIN_WORDS) Triple(e, w, shingles(w)) else null } }
        val out = HashMap<String, Finding>()
        for ((e, w, sh) in notes) {
            val sim = ArrayList<String>(); var copied = false
            for ((o, ow, osh) in notes) {
                if (o.signature == e.signature) continue
                if (jaccard(sh, osh) >= THRESHOLD) { sim.add(o.signature); if (o.signer == e.signer && (o.memo as ReviewMemo.Review).packageName != (e.memo as ReviewMemo.Review).packageName && w == ow) copied = true }
            }
            if (sim.isNotEmpty()) out[e.signature] = Finding(e.signature, sim, copied)
        }
        return out
    }

    /** Compose-time nudge: is this draft near-identical to any existing note? */
    fun similarToExisting(draft: String, entries: List<LedgerEntry>): Boolean {
        val w = normalise(draft); if (w.size < MIN_WORDS) return false
        val sh = shingles(w)
        return entries.any { e -> (e.memo as? ReviewMemo.Review)?.text?.let { t -> val ow = normalise(t); ow.size >= MIN_WORDS && jaccard(sh, shingles(ow)) >= THRESHOLD } == true }
    }
}
