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

    /** Lower case, leet and accents folded, letters only (others become spaces), runs of a letter collapsed to two, single spaces. */
    fun normalise(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
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
                val single = Regex("(\\p{L})\\1").replace(w) { it.groupValues[1] }
                if (single in WORDS) hits += single
            }
            for (p in PHRASES) if (variant.contains(p)) hits += p
        }
        return hits.toList()
    }

    fun blocked(text: String?): Boolean = match(text).isNotEmpty()
}
