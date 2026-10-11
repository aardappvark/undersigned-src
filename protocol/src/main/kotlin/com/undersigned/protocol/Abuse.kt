package com.undersigned.protocol

/**
 * Abusive language (owner, 11 Oct 2026: "moderation by swear words, racist, abusive or other harassment"). One list, shared
 * by the app, the server and every reader, so the outcome is the same everywhere: a note, reply, dispute, store note or own
 * tag that matches is not signed by the app, not relayed, and, if it reaches the chain some other way, its text is hidden
 * automatically with the reason "abuse" (Moderation.textHidden). The rating still counts: this hides text, never a voice.
 * A wallet that keeps at it is excluded by a moderator's signed `ban` record (Moderation.banned).
 *
 * Matching is on whole words after normalisation (lower case, accents and leetspeak folded, repeated letters collapsed,
 * punctuation dropped), so "Scunthorpe" and "assessment" pass and "F*ck", "f u c k" and "fuuuck" do not. Phrases are
 * matched on the normalised text. The list is short on purpose: slurs, strong profanity and threats, not rudeness.
 */
object Abuse {
    /** Whole words. */
    val WORDS: Set<String> = setOf(
        // strong profanity
        "fuck", "fucker", "fucking", "fucked", "motherfucker", "shit", "shitty", "bullshit", "asshole", "arsehole", "bitch", "bitches",
        "cunt", "cunts", "dick", "dickhead", "cock", "cocksucker", "pussy", "twat", "wanker", "whore", "slut", "bastard", "prick", "douchebag",
        // slurs (ethnic, religious, sexuality, disability)
        "nigger", "nigga", "niggers", "chink", "chinks", "gook", "spic", "spics", "wetback", "kike", "kikes", "raghead", "towelhead", "paki",
        "faggot", "faggots", "fag", "fags", "dyke", "tranny", "trannies", "retard", "retards", "retarded", "spastic", "mongoloid",
        "coon", "coons", "jap", "japs", "beaner", "zipperhead", "shemale",
        // harassment and threats
        "kys", "rapist", "pedo", "pedophile", "paedophile", "nazi", "nazis",
    )
    /** Phrases, matched on the normalised text (single spaces). */
    val PHRASES: List<String> = listOf(
        "kill yourself", "kill your self", "go die", "hang yourself", "i will kill you", "ill kill you", "i will find you", "burn in hell",
        "you should die", "hope you die", "drink bleach", "neck yourself", "gas the", "white power", "heil hitler", "sieg heil",
    )
    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i', '|' to 'l', '€' to 'e', '£' to 'l')
    private val ACCENTS = mapOf('à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a', 'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e', 'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o', 'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u', 'ç' to 'c', 'ñ' to 'n', 'ß' to 's')

    /** Look-alike letters from other scripts folded to Latin (audit, 12 Oct 2026): "fuсk" with a Cyrillic с reads as the English word. */
    private val CONFUSABLES = mapOf('а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'к' to 'k', 'н' to 'h', 'м' to 'm', 'т' to 't', 'і' to 'i', 'ј' to 'j', 'ѕ' to 's', 'ѵ' to 'v', 'в' to 'b', 'ф' to 'f', 'ғ' to 'f', 'ԍ' to 'g', 'ⅰ' to 'i', 'ⅼ' to 'l', 'ԁ' to 'd', 'ԛ' to 'q', 'ѡ' to 'w', 'ԝ' to 'w', 'ɡ' to 'g', 'ʏ' to 'y',
        'α' to 'a', 'ε' to 'e', 'ο' to 'o', 'ρ' to 'p', 'ν' to 'v', 'ι' to 'i', 'κ' to 'k', 'τ' to 't', 'υ' to 'u', 'η' to 'n', 'ϲ' to 'c', 'ѕ' to 's', 'ʜ' to 'h', 'ɴ' to 'n')
    /** Characters that are invisible or change nothing on screen: format controls (zero-width, bidi), combining marks, the soft hyphen. */
    private fun invisible(c: Char): Boolean = c == '\u00AD' || Character.getType(c).let { it == Character.FORMAT.toInt() || it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt() }
    /** Lower case, compatibility-normalised (fullwidth and the like), invisible marks dropped, look-alikes and leet and accents folded, joiners inside a word removed ("fu-ck"), letters only, runs of a letter collapsed to two, single spaces. */
    fun normalise(text: String): String {
        val folded = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).lowercase()
            .replace(Regex("(?<=\\p{L})[-._'’`]+(?=\\p{L})"), "")   // a joiner between letters is a disguise, never a word break
        // A whole word in another script is that script's word ("ракі", "јар"); look-alikes are folded only where a Latin letter already sits beside them (re-audit, 12 Oct 2026).
        val latinWords = Regex("[\\p{L}\\p{M}\\p{Cf}]+").findAll(folded).filter { w -> w.value.any { it in 'a'..'z' } }.map { it.range }.toList()
        fun inLatinWord(i: Int) = latinWords.any { i in it }
        val sb = StringBuilder(folded.length)
        for ((i, ch0) in folded.withIndex()) {
            if (invisible(ch0)) continue
            val ch = (if (inLatinWord(i)) CONFUSABLES[ch0] else null) ?: ch0
            val c = LEET[ch] ?: ACCENTS[ch] ?: ch
            // A masking character inside a word ("f*ck", "sh#t") is kept as '*' so [match] can treat it as a wildcard.
            sb.append(if (c.isLetter()) c else if (c == '*' || c == '#' || c == '•') '*' else ' ')
        }
        val collapsed = Regex("(\\p{L})\\1{2,}").replace(sb.toString()) { it.groupValues[1] + it.groupValues[1] }
        return collapsed.trim().replace(Regex("\\s+"), " ")
    }

    /** The listed words and phrases found in [text]; empty when clean. Also tries the text with single-letter spacing removed ("f u c k"). */
    fun match(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val n = normalise(text)
        val joined = n.replace(Regex("\\b(\\p{L}) (?=\\p{L}\\b)"), "$1")   // "f u c k" -> "fuck"
        val hits = LinkedHashSet<String>()
        for (variant in listOf(n, joined)) {
            val words = variant.split(' ')
            for (w in words) {
                if (w in WORDS) hits += w
                // Masked ("f*ck", "f**k"): each mask stands for any letters; a masked word that fits a listed word counts.
                if ('*' in w && w.length >= 3) { val re = Regex("^" + w.replace("*", "\\p{L}*") + "$"); WORDS.firstOrNull { re.matches(it) }?.let { hits += it } }
                // doubled letters collapse to two, so "fuuuck" became "fuuck": try the single-letter form as well
                // Only for words long enough that a stretched spelling is the likely reading: "Jaap" (a name) must not become "jap" (12 Oct 2026).
                val single = Regex("(\\p{L})\\1").replace(w) { it.groupValues[1] }
                if (w.length > 4 && single in WORDS) hits += single
            }
            for (p in PHRASES) if (variant.contains(p)) hits += p
        }
        return hits.toList()
    }

    fun blocked(text: String?): Boolean = match(text).isNotEmpty()

    /** A listed word written for a reader: its first vowel starred ("sh*t", "f*cking"); a phrase is masked word by word. Never shown in full (owner, 12 Oct 2026). */
    fun mask(word: String): String = word.split(' ').joinToString(" ") { w ->
        val i = w.indexOfFirst { it in "aeiouAEIOU" }
        if (w.length < 3) w else if (i >= 0) w.substring(0, i) + "*" + w.substring(i + 1) else w.substring(0, 1) + "*" + w.substring(2)
    }
    fun masked(text: String?): List<String> = match(text).map(::mask)
}
