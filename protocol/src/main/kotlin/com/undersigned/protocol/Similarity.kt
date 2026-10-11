package com.undersigned.protocol

/**
 * Near-duplicate detection for review notes. Deliberately conservative: it LABELS, it never hides.
 * A note is "similar" to another when both have at least [MIN_WORDS] words and their 3-word shingle
 * sets overlap with Jaccard ≥ [THRESHOLD]. Short notes ("Works great") are exempt — many honest people
 * write those. Exact copies by the SAME wallet across different apps are reported separately as
 * "copied across apps", which is the pattern automated review activity shows; a different person writing the same
 * sentence about the same app is not.
 *
 * Second rule (owner, 11 Oct 2026: "an auto flag for copied identical reviews over a short period of time, 20 characters
 * or more"): a note of at least [MIN_CHARS] characters that is IDENTICAL after normalisation to another wallet's note is
 * also a finding, however few words it has; and any finding whose match was signed within [SHORT_WINDOW_SECONDS] is
 * marked [Finding.recent]. Every finding names its matches ([Match]: record, app, signer, time) so a reader can say
 * "similar text to a review of X signed on D" — a statement of fact, never an accusation. It labels; it never hides.
 */
object Similarity {
    const val MIN_WORDS = 6
    const val THRESHOLD = 0.6
    /** Identical notes this long or longer count even under [MIN_WORDS] words. */
    const val MIN_CHARS = 20
    /** Matches signed this close together are [Finding.recent]: three days. */
    const val SHORT_WINDOW_SECONDS = 3 * 86_400L

    fun normalise(text: String): List<String> = text.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun shingles(words: List<String>, n: Int = 3): Set<String> = if (words.size < n) emptySet() else (0..words.size - n).map { words.subList(it, it + n).joinToString(" ") }.toSet()
    fun jaccard(a: Set<String>, b: Set<String>): Double { if (a.isEmpty() || b.isEmpty()) return 0.0; val i = a.intersect(b).size; return i.toDouble() / (a.size + b.size - i) }

    /** One review this one resembles, with what a reader needs to name it. */
    data class Match(val recordId: String, val packageName: String, val signer: String, val time: Long)
    data class Finding(val recordId: String, val similarTo: List<String>, val copiedAcrossApps: Boolean, val matches: List<Match> = emptyList(), val recent: Boolean = false)

    private class Note(val e: LedgerEntry, val words: List<String>, val sh: Set<String>) { val joined = words.joinToString(" "); val long = words.size >= MIN_WORDS }
    private fun alike(a: Note, b: Note): Boolean = if (a.long && b.long) jaccard(a.sh, b.sh) >= THRESHOLD else a.joined.length >= MIN_CHARS && a.joined == b.joined

    /** Findings for every review with a note; O(n²) over notes, fine for thousands. */
    fun findings(entries: List<LedgerEntry>): Map<String, Finding> {
        val notes = entries.mapNotNull { e -> (e.memo as? ReviewMemo.Review)?.text?.let { t -> val w = normalise(t); Note(e, w, shingles(w)) } }
            .filter { it.long || it.joined.length >= MIN_CHARS }
        val out = HashMap<String, Finding>()
        for (n in notes) {
            val e = n.e; val sim = ArrayList<String>(); val matches = ArrayList<Match>(); var copied = false; var recent = false
            for (m in notes) {
                val o = m.e
                if (o.signature == e.signature) continue
                // A revision of your own review of the same app is not a copy. Updating a review keeps most of its
                // wording, so without this every edit was labelled "similar" — publicly — against its own earlier
                // version (found 24 Sep 2026). Reusing your text on a DIFFERENT app is still caught below.
                val op = (o.memo as ReviewMemo.Review).packageName; val ep = (e.memo as ReviewMemo.Review).packageName
                if (o.signer == e.signer && op == ep) continue
                if (alike(n, m)) {
                    sim.add(o.signature); matches.add(Match(o.signature, op, o.signer, o.blockTime))
                    if (o.signer == e.signer && op != ep && n.words == m.words) copied = true
                    if (kotlin.math.abs(o.blockTime - e.blockTime) <= SHORT_WINDOW_SECONDS) recent = true
                }
            }
            if (sim.isNotEmpty()) out[e.signature] = Finding(e.signature, sim, copied, matches.sortedBy { it.time }, recent)
        }
        return out
    }

    /**
     * Compose-time nudge: is this draft near-identical to any existing note? [author] and [packageName] exclude the
     * author's own earlier review of the same app, so updating a review does not warn you about yourself.
     */
    fun similarToExisting(draft: String, entries: List<LedgerEntry>, author: String? = null, packageName: String? = null): Boolean {
        val w = normalise(draft); val d = Note(LedgerEntry("", "", 0, ReviewMemo.Claim("x.y")), w, shingles(w))
        if (!d.long && d.joined.length < MIN_CHARS) return false
        return entries.any { e ->
            val r = e.memo as? ReviewMemo.Review ?: return@any false
            if (author != null && e.signer == author && r.packageName == packageName) return@any false
            r.text?.let { t -> val ow = normalise(t); alike(d, Note(e, ow, shingles(ow))) } == true
        }
    }
}
