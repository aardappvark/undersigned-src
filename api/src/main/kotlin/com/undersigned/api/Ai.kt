package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Claude, used only to help people READ the ledger (owner, 9 Oct 2026): suggested translations, moderation triage for the
 * operator, plain-language update notes, suggested summaries, suggested paraphrase groups, and a docs helper. Every output
 * is labelled a suggestion. Nothing here writes, rates, orders, scores, hides or judges a review: a moderator still decides
 * and signs, and the signed score never reads any of it (docs/COMPLIANCE_REVIEWS.md; integrity policy section 6).
 *
 * One model (Haiku), answers cached in the database so a text is paid for once, a daily cap on calls, and validation of
 * every structured answer (ids must be real, reasons must be one of the four). [call] replaces the network in tests.
 */
class Ai(private val db: Db, private val key: String? = Config.ANTHROPIC_KEY, private val call: ((String, String, Int) -> String?)? = null,
         /** The local model for translations; null = Claude only. Tests pass their own. */
         private val local: ((String, String) -> String?)? = null) {
    init {
        db.exec("CREATE TABLE IF NOT EXISTS ai_cache (kind TEXT NOT NULL, k TEXT NOT NULL, out TEXT NOT NULL, model TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (kind, k))")
        db.exec("CREATE TABLE IF NOT EXISTS ai_usage (day TEXT PRIMARY KEY, calls INTEGER NOT NULL)")
        // Tokens per day (10 Oct 2026), so what Claude costs is measured, not estimated.
        runCatching { db.exec("ALTER TABLE ai_usage ADD COLUMN in_tok INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.exec("ALTER TABLE ai_usage ADD COLUMN out_tok INTEGER NOT NULL DEFAULT 0") }
        db.exec("CREATE TABLE IF NOT EXISTS ai_caller (day TEXT NOT NULL, id TEXT NOT NULL, calls INTEGER NOT NULL, PRIMARY KEY (day, id))")
        // Fresh answers and tokens per day by who they were for (anon, pro, pass, credits, system): the economics ledger.
        db.exec("CREATE TABLE IF NOT EXISTS ai_segment (day TEXT NOT NULL, segment TEXT NOT NULL, calls INTEGER NOT NULL DEFAULT 0, in_tok INTEGER NOT NULL DEFAULT 0, out_tok INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (day, segment))")
    }
    val enabled: Boolean get() = call != null || !key.isNullOrBlank()

    private fun today() = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
    fun callsToday(): Int = db.query("SELECT calls FROM ai_usage WHERE day = ?", today()) { it.getInt(1) }.firstOrNull() ?: 0

    /** One completion, or null when disabled, over the daily cap, or the model could not be reached. */
    /**
     * Who a fresh Claude answer is for, set per request by the API (owner, 10 Oct 2026: one heavy key must not use up the
     * day's shared cap). Cached answers and the local model never reach [complete], so they never count. [debit] charges
     * per-call credits for the answer; background work (the weekly digest, hooks) has no caller and only the global cap.
     */
    data class Caller(val id: String, val limit: Int, val segment: String = "anon", val debit: (() -> Boolean)? = null)
    class AllowanceUsed(val caller: Caller, message: String) : RuntimeException(message)

    fun segmentCalls(segment: String, day: String = today()): Int = db.query("SELECT calls FROM ai_segment WHERE day = ? AND segment = ?", day, segment) { it.getInt(1) }.firstOrNull() ?: 0

    fun usedToday(id: String): Int = db.query("SELECT calls FROM ai_caller WHERE day = ? AND id = ?", today(), id) { it.getInt(1) }.firstOrNull() ?: 0

    /** Today's measured use: calls, input and output tokens, and their cost at [Config.AI_PRICE_IN] / [Config.AI_PRICE_OUT]. */
    fun usage(day: String = today()): JSONObject = db.query("SELECT calls, in_tok, out_tok FROM ai_usage WHERE day = ?", day) { rs ->
        JSONObject().put("day", day).put("calls", rs.getInt(1)).put("input_tokens", rs.getLong(2)).put("output_tokens", rs.getLong(3))
            .put("usd", Math.round((rs.getLong(2) * Config.AI_PRICE_IN + rs.getLong(3) * Config.AI_PRICE_OUT) / 1e6 * 10_000) / 10_000.0)
    }.firstOrNull() ?: JSONObject().put("day", day).put("calls", 0).put("input_tokens", 0).put("output_tokens", 0).put("usd", 0.0)

    private fun complete(system: String, user: String, maxTokens: Int): String? {
        if (!enabled || callsToday() >= Config.AI_DAILY_CALLS) return null
        val segment = who.get()?.segment ?: "system"
        who.get()?.let { c ->
            // Callers without a key share one budget a day, however many IPs they come from (economics, 10 Oct 2026).
            if (c.segment == "anon" && segmentCalls("anon") >= Config.AI_ANON_POOL_DAY) throw AllowanceUsed(c, "today's free AI answers (${Config.AI_ANON_POOL_DAY} across everyone without a key) are used. Answers already made are still served; a key or Pro wallet has its own allowance. It resets at 00:00 UTC.")
            if (usedToday(c.id) >= c.limit) throw AllowanceUsed(c, "today's AI allowance for this caller is used (${c.limit} fresh answers a day; cached answers stay free). It resets at 00:00 UTC; per-call credits have no daily limit.")
            c.debit?.let { if (!it()) throw AllowanceUsed(c, "no calls left on this key for an AI answer (each fresh one uses one extra call)") }
            db.exec("INSERT INTO ai_caller (day, id, calls) VALUES (?, ?, 1) ON CONFLICT(day, id) DO UPDATE SET calls = calls + 1", today(), c.id)
            db.exec("DELETE FROM ai_caller WHERE day < ?", java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(2).toString())   // two days, then gone (privacy policy)
        }
        db.exec("INSERT INTO ai_usage (day, calls) VALUES (?, 1) ON CONFLICT(day) DO UPDATE SET calls = calls + 1", today())
        db.exec("INSERT INTO ai_segment (day, segment, calls) VALUES (?, ?, 1) ON CONFLICT(day, segment) DO UPDATE SET calls = calls + 1", today(), segment)
        call?.let { return it(system, user, maxTokens) }
        return runCatching {
            val c = URI("https://api.anthropic.com/v1/messages").toURL().openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 60_000
                c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01")
                c.setRequestProperty("content-type", "application/json"); c.setRequestProperty("User-Agent", "Undersigned-API/1")
                // Reading aids need no deliberation: with thinking left on, Haiku 5.5 spent the whole budget thinking and
                // answered nothing (the weekly digest, 10 Oct 2026). Thinking off; the budget is the answer's alone.
                val body = JSONObject().put("model", Config.AI_MODEL).put("max_tokens", maxTokens).put("system", system)
                    .put("thinking", JSONObject().put("type", "disabled"))
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode != 200) { log("ai: HTTP ${c.responseCode}"); return@runCatching null }
                val r = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                r.optJSONObject("usage")?.let { u ->
                    val inTok = u.optLong("input_tokens") + u.optLong("cache_read_input_tokens") + u.optLong("cache_creation_input_tokens"); val outTok = u.optLong("output_tokens")
                    db.exec("UPDATE ai_usage SET in_tok = in_tok + ?, out_tok = out_tok + ? WHERE day = ?", inTok, outTok, today())
                    db.exec("UPDATE ai_segment SET in_tok = in_tok + ?, out_tok = out_tok + ? WHERE day = ? AND segment = ?", inTok, outTok, today(), segment)
                }
                val a = r.optJSONArray("content") ?: run { log("ai: no content (stop=${r.optString("stop_reason")}, ${r.toString().take(200)})"); return@runCatching null }
                val text = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { b -> b.optString("type") == "text" }?.optString("text") }.joinToString("").trim()
                // An empty answer is a fact worth a log line (10 Oct 2026: the weekly digest answered 503 for a day with nothing in the log).
                if (text.isEmpty()) log("ai: empty answer (stop=${r.optString("stop_reason")}, blocks=${(0 until a.length()).joinToString(",") { a.optJSONObject(it)?.optString("type") ?: "?" }}, max_tokens=$maxTokens)")
                text.ifEmpty { null }
            } finally { c.disconnect() }
        }.getOrElse { log("ai: ${it.message?.take(80)}"); null }
    }

    /** A translation through the local model first, then Claude; the answer is cleaned of echoes and labels. */
    /** Which model made the answer being cached (the local one or Claude), recorded in ai_cache.model. */
    private val lastModel = ThreadLocal<String>()
    private fun translateWith(system: String, user: String, source: String, maxTokens: Int, claudeFirst: Boolean = false, lang: String? = null): String? {
        // A reply is signed and permanent, so it goes to the stronger model first (the local one slipped on Hungarian
        // grammar in a live check, 9 Oct 2026); reading aids go local first, for free.
        if (claudeFirst) { lastModel.set(Config.AI_MODEL); complete(system, user, maxTokens)?.let { clean(it, source, lang) }?.takeIf { it.isNotBlank() }?.let { return it } }
        local?.let { runCatching { it(system, user) }.getOrNull() }?.let { clean(it, source, lang) }?.takeIf { it.isNotBlank() }?.let { lastModel.set(Config.OLLAMA_MODEL); return it }
        lastModel.set(Config.AI_MODEL)
        return complete(system, user, maxTokens)?.let { clean(it, source, lang) }?.takeIf { it.isNotBlank() }
    }

    /** Models sometimes repeat the source or add "Japanese translation:"; keep only the translation (found live, 9 Oct 2026). */
    fun clean(out: String, source: String, lang: String? = null): String {
        var lines = out.trim().lines().map { it.trim() }
        // A model that "corrects itself" mid-answer ("Wait, I need to correct that…"): keep only what follows the last such line.
        val aside = Regex("^(wait|actually|correction|note|sorry|oops|let me (correct|fix|redo))\\b.*", RegexOption.IGNORE_CASE)
        lines.indexOfLast { aside.matches(it) }.let { if (it >= 0) lines = lines.drop(it + 1) }
        val label = Regex("^(\\*\\*)?[\\p{L} ()-]{0,40}(translation|traducción|traduction|Übersetzung|翻訳|翻译|번역|перевод)[^:：]{0,20}[:：](\\*\\*)?$", RegexOption.IGNORE_CASE)
        val idx = lines.indexOfLast { label.matches(it) }
        if (idx >= 0) lines = lines.drop(idx + 1)
        lines = lines.filterNot { it.equals(source.trim(), ignoreCase = true) }
        // Into a language with its own script, a line of three or more Latin words and none of that script is the model
        // talking (or an English paraphrase), not the translation (live check, 10 Oct 2026: Japanese came back English first).
        SCRIPTS[lang?.substringBefore('-')]?.let { script ->
            val english = Regex("[A-Za-z]{2,}(\\W+[A-Za-z]{2,}){2,}")
            if (lines.any { script.containsMatchIn(it) }) lines = lines.filterNot { !script.containsMatchIn(it) && english.containsMatchIn(it) }
        }
        return lines.joinToString("\n").trim().removeSurrounding("\"").trim()
    }

    /** True when [out] is plausibly in [lang]: for a language with its own script, at least one character of it. */
    fun inLanguage(out: String, lang: String): Boolean = SCRIPTS[lang.substringBefore('-')]?.containsMatchIn(out) ?: true
    /** Whether [text] is probably already in [lang]: a scripted language by its script, a Latin-script one by the absence of any other script. */
    fun sameLanguageLikely(text: String, lang: String): Boolean = SCRIPTS[lang.substringBefore('-')]?.containsMatchIn(text)
        ?: !Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}\\p{IsCyrillic}\\p{IsGreek}\\p{IsArabic}\\p{IsDevanagari}\\p{IsBengali}\\p{IsThai}]").containsMatchIn(text)

    private val SCRIPTS: Map<String, Regex> = mapOf(
        "ja" to Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]"), "zh" to Regex("\\p{IsHan}"), "ko" to Regex("\\p{IsHangul}"),
        "ru" to Regex("\\p{IsCyrillic}"), "uk" to Regex("\\p{IsCyrillic}"), "el" to Regex("\\p{IsGreek}"), "ar" to Regex("\\p{IsArabic}"),
        "hi" to Regex("\\p{IsDevanagari}"), "bn" to Regex("\\p{IsBengali}"), "th" to Regex("\\p{IsThai}"))

    private fun cached(kind: String, k: String, compute: () -> String?): String? {
        db.query("SELECT out FROM ai_cache WHERE kind = ? AND k = ?", kind, k) { it.getString(1) }.firstOrNull()?.let { return it }
        lastModel.remove()
        val out = compute() ?: return null
        db.exec("INSERT OR REPLACE INTO ai_cache (kind, k, out, model, at) VALUES (?, ?, ?, ?, ?)", kind, k, out, lastModel.get() ?: Config.AI_MODEL, System.currentTimeMillis() / 1000)
        return out
    }
    private fun sha(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    /** The first JSON object in a model answer (models sometimes wrap it in prose or a code fence). */
    private fun json(s: String?): JSONObject? = s?.let { t -> val i = t.indexOf('{'); val j = t.lastIndexOf('}'); if (i < 0 || j <= i) null else runCatching { JSONObject(t.substring(i, j + 1)) }.getOrNull() }

    private fun languageName(code: String) = java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH).ifEmpty { code }

    private val TRANSLATE_SYSTEM = "You translate app reviews faithfully. Keep the meaning, tone and strength exactly: do not add, soften, " +
        "strengthen, correct or explain anything. Keep app names, token names and numbers as written. If the text is already in the " +
        "target language, return it unchanged. Output ONLY the translated text: no label, no heading, no quotes, no original."

    /** A suggested translation of one public text into [lang]: faithful, nothing added, softened or strengthened. */
    fun translate(recordId: String, text: String, lang: String): String? = cached("translate2", "$recordId|$lang|${sha(text)}") {
        translateWith(TRANSLATE_SYSTEM, "Target language: ${languageName(lang)}.\n\nText:\n$text", text, 600, lang = lang)?.takeIf { inLanguage(it, lang) }
    }

    /** Any short text (a store review, Pro) into [lang]: the same rules, cached by the text itself. */
    fun translateText(text: String, lang: String): String? = cached("translate_text", "$lang|${sha(text)}") {
        translateWith(TRANSLATE_SYSTEM, "Target language: ${languageName(lang)}.\n\nText:\n$text", text, 700, lang = lang)?.takeIf { inLanguage(it, lang) }
    }

    /**
     * A reply drafted in any language, put into the language the review it answers is written in (Pro): the writer reads
     * it, may edit it, and signs it as their own words.
     */
    fun translateReply(reply: String, review: String): String? = cached("reply", "${sha(review)}|${sha(reply)}") {
        translateWith("You translate a reply to an app review into the SAME language the review is written in. Keep the reply's " +
            "meaning and tone exactly; do not add, soften or promise anything. Keep app names and numbers as written. If the review " +
            "is in the reply's language, return the reply unchanged. Output ONLY the translated reply.",
            "Review (only to tell its language):\n$review\n\nReply to translate:\n$reply", reply, 400, claudeFirst = true)
    }

    /**
     * For the moderator only: which of the four allowed reasons, if any, a flagged text may meet, quoting the words. A low
     * rating, a harsh opinion or criticism is never a reason. A suggestion: the moderator decides and signs.
     */
    fun triage(recordId: String, text: String): JSONObject? = cached("triage", "$recordId|${sha(text)}") {
        json(complete("You help a review platform's moderator. Text may be hidden ONLY for: illegal (illegal content), personal (someone's " +
            "personal data: names with contact details, addresses, phone numbers, keys or seed words), abuse (abuse or harassment aimed at a " +
            "person), impersonation (pretending to be a publisher). A low rating, harsh or negative opinion, criticism of an app or its " +
            "publisher, spam or off-topic text are NOT reasons: answer none. Reply with JSON only: " +
            "{\"reason\": \"illegal|personal|abuse|impersonation|none\", \"quote\": \"<the exact words, or empty>\", \"why\": \"<one sentence>\"}",
            "Review text:\n$text", 300))?.takeIf { it.optString("reason") in setOf("illegal", "personal", "abuse", "impersonation", "none") }?.toString()
    }?.let { JSONObject(it) }

    /** Two neutral sentences on what an update changed, from the store's own notes and the facts we saw. Never a verdict. */
    fun changeNote(pkg: String, kind: String, old: String?, new: String?, whatsNew: String?): String? = cached("change", "$pkg|$kind|$old|$new|${sha(whatsNew ?: "")}") {
        complete("You write two short, neutral sentences describing what changed in an Android app update, for a change log. State facts " +
            "only: version numbers, a signing-certificate change, and what the publisher's release notes say. No advice, no judgement, " +
            "never say safe, unsafe, suspicious or scam. If the signing certificate changed, say so plainly as a fact.",
            "App: $pkg\nChange: ${if (kind == "release") "new release (the store listing's updated date moved)" else kind}\nBefore: ${old ?: "-"}\nAfter: ${new ?: "-"}\nPublisher's release notes: ${whatsNew?.take(1500) ?: "(none)"}", 200)
    }

    /**
     * A suggested summary of COUNTED reviews: the points most often mentioned, each with how many reviews mention it and
     * which, in proportion whatever their sentiment, never an overall verdict. Ids are checked against the input.
     */
    fun summary(pkg: String, reviews: List<Triple<String, Int, String>>): JSONObject? {
        if (reviews.size < Config.AI_SUMMARY_MIN) return null
        val ids = reviews.map { it.first }.toSet()
        val key = "$pkg|${sha(reviews.joinToString("\n") { "${it.first}|${it.second}|${it.third}" })}"
        val out = cached("summary", key) {
            json(complete("You summarise app reviews for readers, neutrally. List up to 5 points reviewers mention most often, positive and " +
                "negative in proportion to how often each is mentioned. For each: a short point in plain words, and the ids of the reviews " +
                "that mention it. Do not rate the app, recommend it, or say it is good, bad, safe or a scam. Reply with JSON only: " +
                "{\"points\": [{\"text\": \"...\", \"review_ids\": [\"...\"]}]}",
                reviews.joinToString("\n") { (id, rating, text) -> "id=$id stars=$rating text=${text.replace('\n', ' ')}" }, 700))
                ?.let { j -> val pts = j.optJSONArray("points") ?: return@let null
                    JSONObject().put("points", JSONArray((0 until pts.length()).mapNotNull { i -> pts.optJSONObject(i)?.let { p ->
                        val rid = p.optJSONArray("review_ids")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in ids }.distinct() }.orEmpty()
                        if (rid.isEmpty() || p.optString("text").isBlank()) null else JSONObject().put("text", p.optString("text").take(200)).put("review_ids", JSONArray(rid)).put("count", rid.size) } }))
                }?.toString()
        } ?: return null
        return JSONObject(out).put("label", "Suggested summary, written by AI from ${reviews.size} counted reviews. Read the reviews themselves.")
            .put("from_counted_reviews", reviews.size).put("model", Config.AI_MODEL)
    }

    /** Groups of reviews saying the same thing in near-identical words (beyond exact copies). A suggestion, not a finding. */
    fun paraphrases(pkg: String, reviews: List<Pair<String, String>>): JSONArray {
        if (reviews.size < 3) return JSONArray()
        val ids = reviews.map { it.first }.toSet()
        val out = cached("paraphrase", "$pkg|${sha(reviews.joinToString("\n") { "${it.first}|${it.second}" })}") {
            json(complete("Find groups of 2 or more app reviews that say the same thing in near-identical wording (paraphrases of one another, " +
                "not just the same opinion). Only clear cases. Reply with JSON only: {\"groups\": [[\"id\", \"id\"]]}; {\"groups\": []} if none.",
                reviews.joinToString("\n") { (id, t) -> "id=$id text=${t.replace('\n', ' ')}" }, 400))?.toString()
        } ?: return JSONArray()
        val g = JSONObject(out).optJSONArray("groups") ?: return JSONArray()
        return JSONArray((0 until g.length()).mapNotNull { i -> g.optJSONArray(i)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in ids }.distinct() }?.takeIf { it.size >= 2 }?.let { JSONArray(it) } })
    }

    /**
     * In-app help: answers a reader's question about how reviews are collected, checked, weighted and shown, from the
     * integrity policy ONLY, in their language. Labelled a suggestion; it points to the policy for anything not covered.
     */
    fun help(question: String, policy: String, lang: String): String? = cached("help", "$lang|${sha(question.trim().lowercase())}") {
        val system = "You answer questions from people reading Undersigned, a review app for the Solana dApp Store, using ONLY the policy below. " +
            "Answer in ${languageName(lang)}, in under 120 words, plainly. If the policy does not answer it, say so and point to the full policy. " +
            "Never give financial advice, never say an app or review is good, bad, safe or fake, and never suggest buying or staking any token.\n\nPOLICY:\n$policy"
        local?.let { runCatching { it(system, question.take(400)) }.getOrNull() }?.trim()?.takeIf { it.length > 20 }?.also { lastModel.set(Config.OLLAMA_MODEL) }
            ?: complete(system, question.take(400), 350).also { lastModel.set(Config.AI_MODEL) }
    }

    /**
     * The weekly digest: store-wide rating bursts and app updates of the last seven days, in neutral plain words, every
     * item a fact with its app named. Never a verdict, never advice. Generated once a week and kept.
     */
    fun digest(weekKey: String, facts: String): String? = cached("digest", weekKey) {
        if (facts.isBlank()) return@cached "No store-wide rating bursts or app updates were seen this week."
        val system = "You write a short weekly digest of facts about the Solana dApp Store for app users and publishers. Use ONLY the facts " +
            "given. Two short sections: 'Rating bursts' and 'App updates'. One line per item, naming the app. State facts (counts, dates, " +
            "versions, a new release date, a signing-certificate change); a burst is a fact about timing, never a verdict. With more " +
            "than 15 updates, list the 15 most recent and give the total. A signing-certificate change is always listed. No advice, " +
            "never say safe, unsafe, suspicious, scam or fake. Under 300 words. Plain text, no markdown headings beyond the two section names."
        complete(system, facts.take(12000), 800).also { lastModel.set(Config.AI_MODEL) }
            ?: local?.let { runCatching { it(system, facts.take(12000)) }.getOrNull() }?.also { lastModel.set(Config.OLLAMA_MODEL) }
    }

    companion object {
        /** The caller of the request this thread is serving (see [Caller]); null for background work. */
        val who = ThreadLocal<Caller?>()

        /** One chat completion from the local Ollama server (non-streaming; gemma4 needs think:false). */
        fun ollama(system: String, user: String): String? {
            val c = URI("${Config.OLLAMA_URL}/api/chat").toURL().openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 3_000; c.readTimeout = 90_000
                c.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("model", Config.OLLAMA_MODEL).put("stream", false).put("think", false).put("options", JSONObject().put("temperature", 0.2))
                    .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", user)))
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode != 200) return null
                return JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONObject("message")?.optString("content")?.trim()?.ifEmpty { null }
            } finally { c.disconnect() }
        }
    }

    /** The docs helper: answers questions about using the API and MCP, from the published docs only. */
    fun ask(question: String, docs: String): String? = cached("ask", sha(question.trim().lowercase())) {
        complete("You answer developers' questions about the Undersigned API and MCP server, using ONLY the documentation below. Be brief " +
            "(under 150 words), give the exact endpoint or tool, and say plainly when the docs do not cover something. Refuse anything " +
            "unrelated to using this API. Never invent endpoints.\n\nDOCUMENTATION:\n$docs", question.take(500), 400)
    }
}
