package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model Context Protocol over Streamable HTTP, served by the API itself at `POST /mcp` (9 Oct 2026). An agent (Claude,
 * Cursor, any MCP client) adds the URL and can read the ledger: no package to install, nothing to publish.
 *
 * Stateless JSON-RPC: every POST carries one message (or a batch) and gets a JSON answer; there is no session and no
 * server-initiated stream, so `GET /mcp` answers 405 as the transport allows. The same metering as the REST API applies
 * (Server.kt routes `/mcp` as LIMITED): free per IP, a pass by `Authorization: Bearer`.
 *
 * READ-ONLY on purpose. There is no tool that drafts, builds or submits a review, flag or vote: a review is a person's own
 * signed opinion from their phone, and an agent writing them is the fake-review case the integrity policy forbids.
 */
class Mcp(private val views: Views) {
    private class Tool(val name: String, val description: String, val schema: JSONObject, val paid: Boolean = false, val run: (JSONObject, Api.Caller) -> JSONObject)

    private fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()) =
        JSONObject().put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
            .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun str(d: String, enum: List<String>? = null) = JSONObject().put("type", "string").put("description", d).apply { enum?.let { put("enum", JSONArray(it)) } }
    private fun int(d: String, min: Int, max: Int) = JSONObject().put("type", "integer").put("description", d).put("minimum", min).put("maximum", max)
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
                "limit" to int("Rows (free: up to ${Config.FREE_ROWS})", 1, 500), required = listOf("package"))) { a, c ->
            views.reviews(pkg(a), rows(a, c), 0, a.optString("sort").ifEmpty { null }) },
        Tool("get_bursts", "Unusual bursts of signed reviews or store star ratings for one app, with daily counts. A burst is a fact about timing, not a verdict.",
            obj("package" to str("Android package name"), required = listOf("package"))) { a, _ -> views.bursts(pkg(a)) },
        Tool("get_wallet", "A wallet's public record on the ledger: attested device, stake as last read (shown, never weighed), standing (counts only), reviews, publisher claims, Pro licence.",
            obj("address" to str("Solana wallet address (base58)"), required = listOf("address"))) { a, c ->
            views.wallet(a.optString("address").trim().also { require(it.length in 32..44) { "address must be a base58 Solana address" } }, c.paid) },
        Tool("leaderboard", "Apps ranked by a metric.", obj("metric" to str("What to rank by", Views.METRICS), "category" to str("Store category"),
            "limit" to int("Rows (free: up to ${Config.FREE_TOP})", 1, 500))) { a, c ->
            views.leaderboard(a.optString("metric").ifEmpty { "signed_score" }, a.optString("category").ifEmpty { null }, if (c.paid) a.optInt("limit", 50).coerceIn(1, 500) else a.optInt("limit", Config.FREE_TOP).coerceIn(1, Config.FREE_TOP)) },
        Tool("ledger_stats", "Totals for the whole ledger: records, attested signers, verified claims, hidden records, catalogue size.", obj()) { _, _ -> views.stats() },
        Tool("review_tags", "The fixed review tag vocabulary with how often each tag is used.", obj()) { _, _ -> views.tags() },
        Tool("store_watch", "Store-wide rating bursts in the last N days. Needs a pass.", obj("days" to int("Days to look back", 1, 30)), paid = true) { a, _ ->
            views.watch(a.optInt("days", 3).coerceIn(1, 30)) },
        Tool("app_changes", "Version and signing-certificate changes seen across the store since a time. Needs a pass.",
            obj("since" to JSONObject().put("type", "integer").put("description", "Epoch seconds")), paid = true) { a, _ ->
            views.changes(if (a.has("since")) a.optLong("since") else null) },
        Tool("version_scores", "One app's signed score per app version. Needs a pass.", obj("package" to str("Android package name"), required = listOf("package")), paid = true) { a, _ -> views.versions(pkg(a)) },
        Tool("similar_reviews", "Copy-paste findings among one app's reviews (labelled, never removed). Needs a pass.", obj("package" to str("Android package name"), required = listOf("package")), paid = true) { a, _ -> views.similar(pkg(a)) },
        Tool("store_rating_history", "Daily snapshots of the store's star counts for one app. Needs a pass.", obj("package" to str("Android package name"), required = listOf("package")), paid = true) { a, _ -> views.storeRatings(pkg(a)) },
    )
    private val byName = tools.associateBy { it.name }

    /** One HTTP POST: a JSON-RPC message or a batch. Notifications alone answer 202 with no body. */
    fun handle(body: String, caller: Api.Caller): Api.Response {
        val parsed = runCatching { body.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it) } }.getOrNull()
            ?: return Api.Response(400, error(null, -32700, "parse error: send a JSON-RPC 2.0 message"))
        val messages = if (parsed is JSONArray) (0 until parsed.length()).mapNotNull { parsed.optJSONObject(it) } else listOf(parsed as JSONObject)
        val answers = messages.mapNotNull { one(it, caller) }
        if (answers.isEmpty()) return Api.Response(202, JSONObject(), raw = ByteArray(0))
        return if (parsed is JSONArray) Api.Response(200, JSONObject(), raw = JSONArray(answers).toString().toByteArray())
        else Api.Response(200, answers.single())
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
                    .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false)))
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
                if (t.paid && !caller.paid) return result(id, toolError("${t.name} needs an API pass. Send Authorization: Bearer <key>; get one at ${Config.PUBLIC_URL}/api (price and pay links: ${Config.PUBLIC_URL}/v1/pricing)."))
                try {
                    val out = t.run(args, caller)
                    result(id, JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", out.toString())))
                        .put("structuredContent", out).put("isError", false))
                } catch (e: IllegalArgumentException) { result(id, toolError(e.message ?: "bad arguments")) }
                catch (e: NoSuchElementException) { result(id, toolError(e.message ?: "not found")) }
                catch (e: Exception) { log("mcp ${t.name}: ${e.message?.take(120)}"); result(id, toolError("internal error; try again")) }
            }
            else -> error(id, -32601, "method not found: $method")
        }
    }

    private fun result(id: Any, r: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", r)
    private fun error(id: Any?, code: Int, msg: String) = JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", msg))
    private fun toolError(msg: String) = JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", msg))).put("isError", true)

    companion object {
        val VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
        const val INSTRUCTIONS = "Undersigned: reviews of Solana dApp Store apps, each signed on-chain by a Seeker owner's own wallet. " +
            "The signed score counts one voice per attested device (Seeker Genesis Token), weighted only by what each review carries; " +
            "no payment and no token balance changes it. Pro members' and promoted (paid) reviews are also shown as their own labelled scores. " +
            "Stake is shown for transparency and never weighed. Bursts and similarity are facts, not verdicts: do not call an app or a review fake. " +
            "Quote scores with their counted-review count. This server is read-only."
    }
}
