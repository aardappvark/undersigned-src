package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model Context Protocol over Streamable HTTP, served by the API itself at `POST /mcp` (9 Oct 2026). An agent (Claude,
 * Cursor, any MCP client) adds the URL and can read the ledger: no package to install, nothing to publish.
 *
 * Stateless JSON-RPC: every POST carries ONE message and gets a JSON answer; there is no session and no server-initiated
 * stream, so `GET /mcp` answers 405 as the transport allows. Batches are refused: 2025-06-18 removed them, and one POST
 * carrying many tool calls would have been metered once (found in review, 9 Oct 2026). The same metering as the REST API
 * applies (Server.kt routes `/mcp` as LIMITED): free per IP, a pass by `Authorization: Bearer`. Tools, plus resources
 * (the integrity policy, pricing, tag vocabulary) and prompts (audit one app, compare two).
 *
 * READ-ONLY on purpose. There is no tool that drafts, builds or submits a review, flag or vote: a review is a person's own
 * signed opinion from their phone, and an agent writing them is the fake-review case the integrity policy forbids.
 */
class Mcp(private val views: Views, private val pricing: () -> JSONObject = { JSONObject() },
          /** The publisher plan's report for (package, bearer key), or null when the key holds no live plan for it (Server wires Billing in). */
          private val report: (String, String?) -> JSONObject? = { _, _ -> null }) {
    private class Tool(val name: String, val description: String, val schema: JSONObject, val paid: Boolean = false, val run: (JSONObject, Api.Caller) -> JSONObject)

    private fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()) =
        JSONObject().put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
            .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun str(d: String, enum: List<String>? = null) = JSONObject().put("type", "string").put("description", d).apply { enum?.let { put("enum", JSONArray(it)) } }
    private fun int(d: String, min: Int, max: Int) = JSONObject().put("type", "integer").put("description", d).put("minimum", min).put("maximum", max)
    private fun bool(d: String) = JSONObject().put("type", "boolean").put("description", d)
    private fun rows(a: JSONObject, c: Api.Caller) = if (c.paid) a.optInt("limit", 50).coerceIn(1, 500) else a.optInt("limit", Config.FREE_ROWS).coerceIn(1, Config.FREE_ROWS)
    private fun pkg(a: JSONObject) = a.optString("package").trim().also { require(it.isNotEmpty()) { "package is required, e.g. com.example.app" } }

    private val tools = listOf(
        Tool("search_apps", "Search the Solana dApp Store catalogue with each app's signed score (reviews signed on-chain by Seeker owners) beside the store's own rating.",
            obj("query" to str("Words in the app's name or package"), "category" to str("Store category, e.g. Tools, DeFi, Games"),
                "sort" to str("Order", listOf("name", "signed", "updated")), "limit" to int("Rows (free: up to ${Config.FREE_ROWS})", 1, 500))) { a, c ->
            views.apps(a.optString("query").ifEmpty { null }, a.optString("category").ifEmpty { null }, a.optString("sort").ifEmpty { null }, rows(a, c)) },
        Tool("get_app", "One dApp: signed score with its count, Pro members' and promoted scores shown apart, the store's rating, publisher proof, store details and payments received.",
            obj("package" to str("Android package name, e.g. com.example.app"), required = listOf("package"))) { a, _ ->
            views.app(pkg(a)) ?: throw NoSuchElementException("unknown package; search_apps finds the right one") },
        Tool("get_reviews", "Signed reviews of one app with every signal: attested device, weight, heading (organic, Pro, promoted, related, not counted), stake as read when the review appeared (shown, never weighed), install age, usage time, flags and replies.",
            obj("package" to str("Android package name"), "sort" to str("Order; stake sorts use the stake read when each review appeared and never change a score", listOf("newest", "helpful", "skr", "sol")),
                "limit" to int("Rows (free: up to ${Config.FREE_ROWS})", 1, 500), "offset" to int("Skip this many reviews (with a pass; free callers get the first page). The answer's total says how many there are", 0, 1_000_000),
                required = listOf("package"))) { a, c ->
            views.reviews(pkg(a), rows(a, c), if (c.paid) a.optInt("offset", 0).coerceAtLeast(0) else 0, a.optString("sort").ifEmpty { null }) },
        Tool("get_record", "One ledger record by id (a transaction signature, or signature:n for the n-th record in a transaction), decoded, with what it credited and whether it was withdrawn.",
            obj("id" to str("Record id"), required = listOf("id"))) { a, _ ->
            views.recordById(a.optString("id").trim()) ?: throw NoSuchElementException("no such record") },
        Tool("list_records", "Decoded ledger records, newest first. Filter by type (rev, rep, flg, clm, trs, pro, bst, mod...), package, signer, time; page with before (the answer's next).",
            obj("type" to str("Record type wire, e.g. rev"), "package" to str("Android package name"), "signer" to str("Wallet address"),
                "since" to JSONObject().put("type", "integer").put("description", "Epoch seconds"), "until" to JSONObject().put("type", "integer").put("description", "Epoch seconds"),
                "before" to str("Record id to page from (the previous answer's next)"), "limit" to int("Rows (free: up to ${Config.FREE_ROWS})", 1, 1000))) { a, c ->
            views.records(a.optString("type").ifEmpty { null }, a.optString("package").ifEmpty { null }, a.optString("signer").ifEmpty { null },
                if (a.has("since")) a.optLong("since") else null, if (a.has("until")) a.optLong("until") else null, a.optString("before").ifEmpty { null },
                if (c.paid) a.optInt("limit", 100).coerceIn(1, 1000) else a.optInt("limit", Config.FREE_ROWS).coerceIn(1, Config.FREE_ROWS)) },
        Tool("payments_board", "Apps whose publishers confirmed a treasury, by payments received on-chain (USD on the day of each payment). Off-chain income is invisible; not a statement about profit.",
            obj("limit" to int("Rows (free: up to ${Config.FREE_TOP})", 1, 500))) { a, c ->
            views.paymentsBoard(if (c.paid) a.optInt("limit", 50).coerceIn(1, 500) else a.optInt("limit", Config.FREE_TOP).coerceIn(1, Config.FREE_TOP)) },
        Tool("review_analytics", "Review analytics over every source, told apart and never mixed: source = store (the dApp Store's own reviews, read from its public lists; counts, means and shares only) | all | organic | pro | promoted | quick | other | ours (signed reviews on Undersigned by class). Returns reviews, mean stars, the star distribution, replied and short-text shares, categories with change, the most-reviewed dApps and a series over time; window = 7d|30d|90d|1y|all; narrow to one category or package. Raw rows: /v1/reviews/store.csv and /v1/records.",
            obj("source" to str("store | all | organic | pro | promoted | quick | other | ours (default store)", ReviewAnalytics.SOURCES), "window" to str("7d | 30d | 90d | 1y | all (default all)"), "by" to str("day | week | month for the series (default by window)"),
                "category" to str("One store category"), "package" to str("One dApp's package"), "limit" to int("Most-reviewed dApps to return", 1, 500))) { a, c ->
            val w = a.optString("window").takeIf { it in Analytics.WINDOWS } ?: "all"
            views.reviewAnalytics.summary(a.optString("source").takeIf { it in ReviewAnalytics.SOURCES } ?: "store", w, a.optString("category").ifEmpty { null }, a.optString("package").ifEmpty { null },
                a.optString("by").takeIf { it in listOf("day", "week", "month") } ?: when (w) { "7d", "30d" -> "day"; "90d" -> "week"; else -> "month" }, if (c.paid) a.optInt("limit", 25).coerceIn(1, 500) else a.optInt("limit", Config.FREE_TOP).coerceIn(1, Config.FREE_TOP)) },
        Tool("payments_analytics", "Payment analytics from the Solana ledger over confirmed treasuries and wallets found in app code (always labelled apart): report = summary | leaderboard | categories | trending | patterns | treasuries | series; window = 7d|30d|90d|1y|all. Gross receipts on the day paid, never revenue or profit; a found wallet is a suspicion, not a fact.",
            obj("report" to str("summary | leaderboard | categories | trending | patterns | treasuries | series | app_series"), "window" to str("7d | 30d | 90d | 1y | all (default 30d)"),
                "package" to str("For app_series: the package"), "include" to str("For leaderboard: confirmed | found | all"), "limit" to int("Rows", 1, 100))) { a, c ->
            val w = a.optString("window").takeIf { it in Analytics.WINDOWS } ?: "30d"; val an = views.analytics; val lim = a.optInt("limit", 10).coerceIn(1, 100)
            when (a.optString("report", "summary")) {
                "leaderboard" -> an.leaderboard(w, a.optString("include").takeIf { it in listOf("confirmed", "found", "all") } ?: "all", lim)
                "categories" -> an.categoriesReport(w); "trending" -> an.trending(w, lim); "patterns" -> an.patterns(w); "treasuries" -> an.treasuries(w, lim)
                "series" -> an.storeSeries(w, if (w == "7d") "day" else if (w == "all") "month" else "week")
                "app_series" -> an.appSeries(a.optString("package"), "week") ?: JSONObject().put("error", "no payments seen for that package")
                else -> an.summary(w)
            } },
        Tool("plan_report", "Publisher plan: one app's report (reviews waiting for an answer, the store rating's trend, bursts, certificate and permission changes, scores by version, the category benchmark, payments over time). Needs a publisher key with a live plan for that app (POST /v1/checkout {publisher} then /v1/publisher/plan).",
            obj("package" to str("The app's package")), paid = true) { a, c ->
            report(a.optString("package"), c.key) ?: JSONObject().put("error", "no_plan").put("message", "this key holds no live publisher plan for ${a.optString("package")}; see /plans") },
        Tool("found_treasuries", "Wallets FOUND IN APPS' CODE that received payments from 5+ wallets, NOT confirmed by the publisher: a separate list by app name, never ranked with payments_board. Say 'found in the app's code, not confirmed' whenever you mention one; it may not be the app's treasury.",
            obj("limit" to int("Rows (free: up to ${Config.FREE_ROWS})", 1, 500), "package" to str("Optional: one app, for its full-history analytics (dates, monthly series, new and returning payers, concentration)"),
                "trends" to bool("Optional: store-wide monthly totals instead of the list"))) { a, c ->
            if (a.optBoolean("trends")) return@Tool views.foundTrends()
            a.optString("package").takeIf { it.isNotBlank() }?.let { p -> return@Tool views.foundAnalytics(p) ?: JSONObject().put("error", "nothing found in this app's code") }
            views.foundTreasuries(if (c.paid) a.optInt("limit", 50).coerceIn(1, 500) else a.optInt("limit", Config.FREE_ROWS).coerceIn(1, Config.FREE_ROWS)) },
        Tool("translate_review", "A suggested translation, by AI, of one review's or reply's text into a language. For Pro members (pass wallet) or a pass / credits key. The original stays the author's words.",
            obj("id" to str("Record id of the review or reply"), "lang" to str("Target language code", Config.AI_LANGS.sorted()), "wallet" to str("A wallet holding Pro (not needed with a key)"),
                required = listOf("id", "lang"))) { a, c ->
            val ai = views.ai?.takeIf { it.enabled } ?: throw IllegalArgumentException("translations are not available right now")
            if (!c.paid && views.holdsPro(a.optString("wallet").trim())) Ai.who.set(Ai.Caller("wallet:" + a.optString("wallet").trim(), Config.AI_PER_PRO_WALLET_DAY, "pro"))
            if (!c.paid && !views.holdsPro(a.optString("wallet").trim())) throw IllegalArgumentException("translations need Pro (pass a wallet holding Pro) or a pass / credits key; see get_pricing")
            val e = views.recordEntry(a.optString("id").trim()) ?: throw NoSuchElementException("no such record")
            val text = views.visibleText(e) ?: throw NoSuchElementException("this record has no text to translate")
            val lang = a.optString("lang").takeIf { it in Config.AI_LANGS } ?: throw IllegalArgumentException("unsupported lang")
            JSONObject().put("id", e.signature).put("lang", lang).put("text", ai.translate(e.signature, text, lang) ?: throw IllegalArgumentException("no translation right now"))
                .put("label", "Suggested translation, by AI. The original is the author's own words.") },
        Tool("get_apps", "Several apps at once (the same fields as get_app for each), for comparing or checking a list. Up to 20 packages free, 100 with a key; unknown ones are listed apart.",
            obj("packages" to JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")).put("minItems", 1).put("maxItems", 100).put("description", "Android package names"), required = listOf("packages"))) { a, c ->
            val ps = a.optJSONArray("packages")?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.distinct() }.orEmpty().take(if (c.paid) 100 else Config.FREE_ROWS)
            if (ps.isEmpty()) throw IllegalArgumentException("packages: one or more package names")
            JSONObject().put("apps", JSONArray(ps.mapNotNull { views.app(it) })).put("unknown", JSONArray(ps.filter { views.app(it) == null })) },
        Tool("scoring_help", "Ask how reviews are collected, checked, weighted, ordered and shown: answered by AI from the Review Integrity Policy only (labelled), optionally in another language. For exact wording read the undersigned://policy resource.",
            obj("question" to str("The question, 3 to 400 characters"), "lang" to str("Answer language", Config.AI_LANGS.sorted()), required = listOf("question"))) { a, _ ->
            val q = a.optString("question").trim().takeIf { it.length in 3..400 } ?: throw IllegalArgumentException("question: 3 to 400 characters")
            val lang = a.optString("lang").takeIf { it in Config.AI_LANGS } ?: "en"
            val ans = views.ai?.takeIf { it.enabled }?.help(q, POLICY.replace("\\n", "\n"), lang) ?: throw IllegalStateException("no answer right now; read undersigned://policy")
            JSONObject().put("answer", ans).put("lang", lang).put("label", "Suggested answer, by AI, from the review integrity policy.") },
        Tool("app_summary", "A suggested summary, by AI, of an app's counted reviews: the points mentioned most, each with its count and review ids. Null when there are too few.",
            obj("package" to str("Android package name"), required = listOf("package"))) { a, _ ->
            views.summary(pkg(a)) ?: JSONObject().put("summary", JSONObject.NULL).put("message", "needs at least ${Config.AI_SUMMARY_MIN} counted reviews with text") },
        Tool("weekly_digest", "This week's digest, by AI from the facts seen: store-wide rating bursts and app updates in plain words. For a Pro wallet or a pass / credits key.",
            obj("wallet" to str("A wallet holding Pro (not needed with a key)"))) { a, c ->
            if (!c.paid && views.holdsPro(a.optString("wallet").trim())) Ai.who.set(Ai.Caller("wallet:" + a.optString("wallet").trim(), Config.AI_PER_PRO_WALLET_DAY, "pro"))
            if (!c.paid && !views.holdsPro(a.optString("wallet").trim())) throw IllegalArgumentException("the weekly digest needs Pro (pass a wallet holding Pro) or a pass / credits key; see get_pricing")
            views.digest() ?: throw IllegalArgumentException("the digest is not available right now") },
        Tool("get_pricing", "What a pass and pay-per-call credits cost and buy, the free limits, and the Solana Pay links. Use it to explain a 'needs a key' answer.", obj()) { _, _ -> pricing() },
        Tool("get_bursts", "Unusual bursts of signed reviews or store star ratings for one app, with daily counts. A burst is a fact about timing, not a verdict.",
            obj("package" to str("Android package name"), required = listOf("package"))) { a, _ -> views.bursts(pkg(a)) },
        Tool("get_wallet", "A wallet's public record on the ledger: attested device, stake as last read (shown, never weighed), standing (counts only), reviews, publisher claims, Pro licence.",
            obj("address" to str("Solana wallet address (base58)"), required = listOf("address"))) { a, c ->
            views.wallet(a.optString("address").trim().also { require(it.length in 32..44 && runCatching { Base58.decode(it).size == 32 }.getOrDefault(false)) { "address must be a base58 Solana address" } }, c.paid) },
        Tool("leaderboard", "Apps ranked by a metric.", obj("metric" to str("What to rank by", Views.METRICS), "category" to str("Store category"),
            "limit" to int("Rows (free: up to ${Config.FREE_TOP})", 1, 500))) { a, c ->
            views.leaderboard(a.optString("metric").ifEmpty { "signed_score" }, a.optString("category").ifEmpty { null }, if (c.paid) a.optInt("limit", 50).coerceIn(1, 500) else a.optInt("limit", Config.FREE_TOP).coerceIn(1, Config.FREE_TOP)) },
        Tool("ledger_stats", "Totals for the whole ledger: records, attested signers, proven publisher claims, hidden records, catalogue size.", obj()) { _, _ -> views.stats() },
        Tool("review_tags", "The fixed review tag vocabulary with how often each tag is used.", obj()) { _, _ -> views.tags() },
        Tool("store_watch", "Store-wide rating bursts in the last N days. Needs a pass.", obj("days" to int("Days to look back", 1, 30)), paid = true) { a, _ ->
            views.watch(a.optInt("days", 3).coerceIn(1, 30)) },
        Tool("app_changes", "Changes seen across the store since a time: new releases (kind release, any listing), version codes and signing certificates. Needs a pass.",
            obj("since" to JSONObject().put("type", "integer").put("description", "Epoch seconds")), paid = true) { a, _ ->
            views.changes(if (a.has("since")) a.optLong("since") else null) },
        Tool("version_scores", "One app's signed score per app version. Needs a pass.", obj("package" to str("Android package name"), required = listOf("package")), paid = true) { a, _ -> views.versions(pkg(a)) },
        Tool("similar_reviews", "Copy-paste findings among one app's reviews (labelled, never removed). Needs a pass.", obj("package" to str("Android package name"), required = listOf("package")), paid = true) { a, _ -> views.similar(pkg(a)) },
        Tool("store_rating_history", "Daily snapshots of the store's star counts for one app. Needs a pass.", obj("package" to str("Android package name"), required = listOf("package")), paid = true) { a, _ -> views.storeRatings(pkg(a)) },
    )
    private val byName = tools.associateBy { it.name }

    /** One HTTP POST: one JSON-RPC message. A notification answers 202 with no body. */
    fun handle(body: String, caller: Api.Caller): Api.Response {
        val parsed = runCatching { body.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it) } }.getOrNull()
            ?: return Api.Response(400, error(null, -32700, "parse error: send a JSON-RPC 2.0 message"))
        if (parsed is JSONArray) return Api.Response(400, error(null, -32600, "batches are not supported: send one JSON-RPC message per POST"))
        val answer = one(parsed as JSONObject, caller) ?: return Api.Response(202, JSONObject(), raw = ByteArray(0))
        return Api.Response(200, answer)
    }

    private fun one(m: JSONObject, caller: Api.Caller): JSONObject? {
        val id = m.opt("id")?.takeIf { it != JSONObject.NULL }
        val method = m.optString("method")
        if (id == null) return null   // notifications (initialized, cancelled): nothing to answer
        val p = m.optJSONObject("params") ?: JSONObject()
        return when (method) {
            "initialize" -> {
                val asked = p.optString("protocolVersion")
                result(id, JSONObject().put("protocolVersion", if (asked in VERSIONS) asked else VERSIONS.first())
                    .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false))
                        .put("resources", JSONObject().put("listChanged", false).put("subscribe", false)).put("prompts", JSONObject().put("listChanged", false)))
                    .put("serverInfo", JSONObject().put("name", "undersigned").put("title", "Undersigned").put("version", "1.0.0"))
                    .put("instructions", INSTRUCTIONS))
            }
            "ping" -> result(id, JSONObject())
            "tools/list" -> result(id, JSONObject().put("tools", JSONArray(tools.map { t ->
                JSONObject().put("name", t.name).put("description", t.description).put("inputSchema", t.schema)
                    .put("annotations", JSONObject().put("readOnlyHint", true).put("openWorldHint", false)) })))
            "tools/call" -> {
                val t = byName[p.optString("name")] ?: return error(id, -32602, "unknown tool: ${p.optString("name")}")
                val args = p.optJSONObject("arguments") ?: JSONObject()
                if (t.paid && !caller.paid) return result(id, toolError("${t.name} needs a key: a pass, or pay-per-call credits (${Config.PER_CALL_SOL} SOL or ${Config.PER_CALL_SKR} SKR a call, from ${Config.CREDITS_MIN_SOL} SOL). Call get_pricing for how to pay, then send Authorization: Bearer <key>. Details: ${Config.PUBLIC_URL}/api"))
                try {
                    val out = t.run(args, caller)
                    result(id, JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", out.toString())))
                        .put("structuredContent", out).put("isError", false))
                } catch (e: IllegalArgumentException) { result(id, toolError(e.message ?: "bad arguments")) }
                catch (e: NoSuchElementException) { result(id, toolError(e.message ?: "not found")) }
                catch (e: Ai.AllowanceUsed) { result(id, toolError(e.message ?: "today's AI allowance is used")) }
                catch (e: Exception) { log("mcp ${t.name}: ${e.message?.take(120)}"); result(id, toolError("internal error; try again")) }
            }
            "resources/list" -> result(id, JSONObject().put("resources", JSONArray(RESOURCES.map { (uri, r) ->
                JSONObject().put("uri", uri).put("name", r.first).put("description", r.second).put("mimeType", if (uri.endsWith("policy")) "text/markdown" else "application/json") })))
            "resources/templates/list" -> result(id, JSONObject().put("resourceTemplates", JSONArray().put(JSONObject().put("uriTemplate", "undersigned://app/{package}")
                .put("name", "app").put("description", "One app's signed score, store rating and publisher proof (the get_app tool as a resource)").put("mimeType", "application/json"))))
            "resources/read" -> {
                val uri = p.optString("uri")
                val (mime, text) = when {
                    uri == "undersigned://policy" -> "text/markdown" to POLICY
                    uri == "undersigned://pricing" -> "application/json" to pricing().toString()
                    uri == "undersigned://tags" -> "application/json" to views.tags().toString()
                    uri.startsWith("undersigned://app/") -> "application/json" to (views.app(uri.removePrefix("undersigned://app/"))?.toString() ?: return error(id, -32002, "resource not found: $uri"))
                    else -> return error(id, -32002, "resource not found: $uri")
                }
                result(id, JSONObject().put("contents", JSONArray().put(JSONObject().put("uri", uri).put("mimeType", mime).put("text", text))))
            }
            "prompts/list" -> result(id, JSONObject().put("prompts", JSONArray(PROMPTS.map { (name, pr) -> JSONObject().put("name", name).put("description", pr.first)
                .put("arguments", JSONArray(pr.second.map { JSONObject().put("name", it).put("description", "Android package name, e.g. com.example.app").put("required", true) })) })))
            "prompts/get" -> {
                val name = p.optString("name"); val pr = PROMPTS[name] ?: return error(id, -32602, "unknown prompt: $name")
                val args = p.optJSONObject("arguments") ?: JSONObject()
                val missing = pr.second.filter { args.optString(it).isBlank() }
                if (missing.isNotEmpty()) return error(id, -32602, "missing arguments: ${missing.joinToString()}")
                val text = pr.third(pr.second.associateWith { args.optString(it).trim() })
                result(id, JSONObject().put("description", pr.first).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONObject().put("type", "text").put("text", text)))))
            }
            else -> error(id, -32601, "method not found: $method")
        }
    }

    private fun result(id: Any, r: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", r)
    private fun error(id: Any?, code: Int, msg: String) = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", msg))
    private fun toolError(msg: String) = JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", msg))).put("isError", true)

    companion object {
        val RESOURCES = linkedMapOf(
            "undersigned://policy" to ("Review integrity policy" to "How reviews are collected, checked, weighted, shown and moderated. Read before describing a score."),
            "undersigned://pricing" to ("Pricing and limits" to "Free limits, what a pass costs and buys, and how to pay."),
            "undersigned://tags" to ("Review tags" to "The fixed tag vocabulary with usage counts."),
        )
        val PROMPTS: Map<String, Triple<String, List<String>, (Map<String, String>) -> String>> = linkedMapOf(
            "audit_app_reputation" to Triple("Audit one app's reputation from signed reviews, the store's rating, bursts and publisher proof.", listOf("package")) { a ->
                "Audit the reputation of the Solana dApp Store app ${a["package"]}. Use get_app, get_reviews (sort newest, then helpful) and get_bursts. " +
                    "Report: the signed score WITH its counted-review count; the store's own rating with its count; how they differ; each review heading " +
                    "(organic, Pro members, promoted, related, not counted) and what it means; any bursts as timing facts; whether the publisher is proven and how. " +
                    "Do not call the app or any review fake: bursts and similarity are facts, not verdicts. Stake is shown for transparency and never weighed." },
            "compare_apps" to Triple("Compare two apps side by side on signed reviews and the store's rating.", listOf("package_a", "package_b")) { a ->
                "Compare the Solana dApp Store apps ${a["package_a"]} and ${a["package_b"]}. Use get_app and get_reviews for each. Put the signed score and its " +
                    "counted-review count next to the store's rating and its count for both, then the most-used tags and any bursts. Say plainly when a score rests on " +
                    "few reviews. Bursts are facts, not verdicts." },
        )
        const val POLICY = "# Undersigned review integrity (summary)\n\n" +
            "- A review is a memo transaction its author signs in their own wallet. Anyone can sign one; only wallets holding a Seeker Genesis Token are counted, one voice per device.\n" +
            "- Weight = 1 per attested device x quality (0.5 base; +0.25 phone facts; +0.25 a note of six words or more; halved after a consensus hide). No payment and no token balance changes it.\n" +
            "- Headings: organic; Pro members (pay Undersigned for Pro, same weight, own score); promoted (author paid to promote, same weight, own score, listed after organic, never first); related to Undersigned or the publisher (counted nowhere); not counted.\n" +
            "- Stake (SKR, SOL) is shown beside each review as read when it appeared, for transparency, never weighed.\n" +
            "- Publishers' own reviews and Undersigned's own wallets count nowhere. Moderation hides text only, for four reasons, publicly.\n" +
            "- Bursts and copy-paste similarity are facts about timing and wording, not verdicts.\n\n" +
            "Full policy: https://aardappvark.github.io/undersigned-legal/integrity.html"
        val VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
        const val INSTRUCTIONS = "Undersigned: reviews of Solana dApp Store apps, each signed on-chain by a Seeker owner's own wallet. " +
            "The signed score counts one voice per attested device (Seeker Genesis Token), weighted only by what each review carries; " +
            "no payment and no token balance changes it. Pro members' and promoted (paid) reviews are also shown as their own labelled scores. " +
            "Stake is shown for transparency and never weighed. Bursts and similarity are facts, not verdicts: do not call an app or a review fake. " +
            "Quote scores with their counted-review count. This server is read-only."
    }
}
