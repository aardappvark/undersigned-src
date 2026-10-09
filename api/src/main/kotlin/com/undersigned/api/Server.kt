package com.undersigned.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * The HTTP API (docs/API.md). Plain JDK server; every answer is JSON. Free callers are metered by IP, pass holders by key;
 * paid endpoints answer 402 with the price and how to pay, so an agent can act on it without reading docs.
 */
class Api(private val views: Views, private val billing: Billing, private val indexer: Indexer, private val limiter: RateLimiter = RateLimiter(), private val hooks: Hooks? = null,
          private val blockhash: () -> String = { Rpc().latestBlockhash() }, private val sanctioned: (String) -> Boolean = Screening::isSanctioned) {
    /** Human pages and badges (Web.kt); set after construction because pages call back into [handle]'s views. */
    var web: Web? = null

    /** A route's gate: FREE for anyone, LIMITED = free with fewer rows, PAID = pass only. */
    enum class Tier { FREE, LIMITED, PAID }
    data class Caller(val paid: Boolean, val key: String?)
    /** JSON by default; [raw] with its [contentType] for pages and badges. */
    class Response(val status: Int, val body: JSONObject, val headers: Map<String, String> = emptyMap(), val raw: ByteArray? = null, val contentType: String = "application/json; charset=utf-8")
    private class Route(val method: String, val pattern: Regex, val tier: Tier, val summary: String, val handler: (Req) -> Response)
    class Req(val params: Map<String, String>, val query: Map<String, String>, val body: String, val caller: Caller) {
        fun int(k: String, def: Int, max: Int) = query[k]?.toIntOrNull()?.coerceIn(0, max) ?: def
    }

    private val routes = ArrayList<Route>()
    private val mcp = Mcp(views) { pricing() }
    private fun route(method: String, path: String, tier: Tier, summary: String, handler: (Req) -> Response) {
        val names = Regex("\\{(\\w+)}").findAll(path).map { it.groupValues[1] }.toList()
        val rx = Regex("^" + path.replace(Regex("\\{\\w+}"), "([^/]+)") + "$")
        routes.add(Route(method, rx, tier, summary) { r -> handler(r) }.also { pathNames[it] = names; paths[it] = path })
    }
    private val pathNames = HashMap<Route, List<String>>()
    private val paths = HashMap<Route, String>()

    private fun ok(o: JSONObject) = Response(200, o)
    private fun notFound(what: String) = Response(404, JSONObject().put("error", "not_found").put("message", what))
    private fun rows(r: Req, free: Int, max: Int = 500) = if (r.caller.paid) r.int("limit", 50, max) else minOf(r.int("limit", free, free), free)

    init {
        route("GET", "/v1/health", Tier.FREE, "Indexer status: last syncs, last error, skipped transactions, hook failures") { ok(JSONObject().put("ok", true).put("indexed_at", indexer.state.builtAt / 1000).put("ledger_synced", indexer.lastLedgerSync / 1000).put("store_synced", indexer.lastStoreSync / 1000)
            .put("last_error", indexer.lastError ?: JSONObject.NULL).put("skipped_transactions", indexer.skippedTx).put("hook_failures_24h", hooks?.failures24h() ?: 0).put("limiter_entries", limiter.size).put("public", Config.PUBLIC).put("test_prices", Config.testPrices)
            .put("snapshot_days", indexer.snapshotDays().size).put("last_snapshot_day", indexer.snapshotDays().lastOrNull() ?: JSONObject.NULL).put("last_backup", indexer.lastBackup ?: JSONObject.NULL)) }
        // MCP for agents (Mcp.kt): read-only tools over the same views, metered like any LIMITED route.
        route("POST", "/mcp", Tier.LIMITED, "Model Context Protocol (Streamable HTTP, JSON-RPC): read-only tools for AI agents. Add this URL to Claude, Cursor or any MCP client") { r -> mcp.handle(r.body, r.caller) }
        // Reading aids (Ai.kt), each labelled a suggestion. A translation is for Pro members (a wallet holding Pro on-chain)
        // and for pass or credits holders; others are told how to upgrade. The summary and the docs helper are free.
        route("GET", "/v1/records/{id}/translation", Tier.LIMITED, "Suggested translation of a review's or reply's text into lang (Pro wallet via ?wallet=, or a pass / credits key)") { r ->
            val ai = views.ai?.takeIf { it.enabled } ?: return@route Response(503, JSONObject().put("error", "unavailable").put("message", "translations are not available right now"))
            val lang = r.query["lang"]?.takeIf { it in Config.AI_LANGS } ?: return@route Response(400, JSONObject().put("error", "bad_lang").put("message", "lang must be one of ${Config.AI_LANGS.sorted().joinToString(",")}"))
            val wallet = r.query["wallet"]?.trim()
            if (!r.caller.paid && (wallet == null || !views.holdsPro(wallet))) return@route Response(402, JSONObject().put("error", "pro_required")
                .put("message", "Suggested translations are a Pro feature: hold Pro in the wallet you pass as ?wallet=, or use an API pass or pay-per-call credits.").put("pricing", pricing()))
            val e = indexer.state.entries.firstOrNull { it.signature == r.params["id"] } ?: return@route notFound("no such record")
            val text = views.visibleText(e) ?: return@route notFound("this record has no text to translate")
            val out = ai.translate(e.signature, text, lang) ?: return@route Response(503, JSONObject().put("error", "unavailable").put("message", "the translation could not be made right now; try again"))
            ok(JSONObject().put("id", e.signature).put("lang", lang).put("text", out).put("label", "Suggested translation, by AI. The original is the author's own words.").put("model", Config.AI_MODEL))
        }
        // Pro: a store review's text into your language, and a reply you drafted into the language of the review it answers.
        fun proOrKey(r: Req, wallet: String?): Response? = if (r.caller.paid || (wallet != null && views.holdsPro(wallet))) null
            else Response(402, JSONObject().put("error", "pro_required").put("message", "This is a Pro feature: hold Pro in the wallet you send, or use an API pass or pay-per-call credits.").put("pricing", pricing()))
        route("POST", "/v1/translate", Tier.LIMITED, "Suggested translation, by AI, of a store review's text (up to 1,000 characters) into lang. Body {text, lang, wallet}. Pro or a key") { r ->
            val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return@route Response(400, JSONObject().put("error", "bad_json"))
            proOrKey(r, b.optString("wallet").ifBlank { null })?.let { return@route it }
            val text = b.optString("text").trim().takeIf { it.length in 1..1000 } ?: return@route Response(400, JSONObject().put("error", "bad_text").put("message", "text: 1 to 1,000 characters"))
            val lang = b.optString("lang").takeIf { it in Config.AI_LANGS } ?: return@route Response(400, JSONObject().put("error", "bad_lang"))
            val out = views.ai?.takeIf { it.enabled }?.translateText(text, lang) ?: return@route Response(503, JSONObject().put("error", "unavailable").put("message", "no translation right now; try again"))
            ok(JSONObject().put("lang", lang).put("text", out).put("label", "Suggested translation, by AI. The original is the author's own words."))
        }
        route("POST", "/v1/translate/reply", Tier.LIMITED, "Your reply draft, put by AI into the language of the review it answers. Body {reply, review_id or review_text, wallet}. Pro or a key; you check it and sign it as your own words") { r ->
            val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return@route Response(400, JSONObject().put("error", "bad_json"))
            proOrKey(r, b.optString("wallet").ifBlank { null })?.let { return@route it }
            val reply = b.optString("reply").trim().takeIf { it.length in 1..500 } ?: return@route Response(400, JSONObject().put("error", "bad_reply").put("message", "reply: 1 to 500 characters"))
            val review = b.optString("review_id").ifBlank { null }?.let { id -> views.recordEntry(id)?.let { views.visibleText(it) } } ?: b.optString("review_text").trim().takeIf { it.length in 1..1000 }
                ?: return@route Response(400, JSONObject().put("error", "bad_review").put("message", "send review_id (a signed review with text) or review_text"))
            val out = views.ai?.takeIf { it.enabled }?.translateReply(reply, review) ?: return@route Response(503, JSONObject().put("error", "unavailable").put("message", "no translation right now; try again"))
            ok(JSONObject().put("reply", out).put("label", "Suggested translation, by AI. Check it: you sign the reply as your own words."))
        }
        route("POST", "/v1/help", Tier.FREE, "In-app help: a question about how reviews are collected, checked, weighted and shown, answered by AI from the integrity policy only. Body {question, lang}") { r ->
            val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return@route Response(400, JSONObject().put("error", "bad_json"))
            val q = b.optString("question").trim().takeIf { it.length in 3..400 } ?: return@route Response(400, JSONObject().put("error", "bad_question").put("message", "question: 3 to 400 characters"))
            val lang = b.optString("lang").takeIf { it in Config.AI_LANGS } ?: "en"
            val ans = views.ai?.takeIf { it.enabled }?.help(q, Mcp.POLICY.replace("\\n", "\n"), lang) ?: return@route Response(503, JSONObject().put("error", "unavailable").put("message", "no answer right now; read the policy: https://aardappvark.github.io/undersigned-legal/integrity.html"))
            ok(JSONObject().put("answer", ans).put("lang", lang).put("label", "Suggested answer, by AI, from the review integrity policy.").put("policy", "https://aardappvark.github.io/undersigned-legal/integrity.html"))
        }
        route("GET", "/v1/digest", Tier.LIMITED, "This week's digest: store-wide rating bursts and app updates in plain words, by AI from the facts seen (Pro wallet via ?wallet=, or a pass / credits key)") { r ->
            proOrKey(r, r.query["wallet"]?.trim())?.let { return@route it }
            views.digest()?.let(::ok) ?: Response(503, JSONObject().put("error", "unavailable").put("message", "the digest is not available right now"))
        }
        route("GET", "/v1/apps/{pkg}/summary", Tier.FREE, "Suggested summary, by AI, of an app's counted reviews: the points most mentioned, each with its count and review ids") { r ->
            if (indexer.state.byPackage[r.params["pkg"]!!] == null) return@route notFound("unknown package")
            views.summary(r.params["pkg"]!!)?.let(::ok) ?: ok(JSONObject().put("package", r.params["pkg"]).put("summary", JSONObject.NULL)
                .put("message", "No summary: it needs at least ${Config.AI_SUMMARY_MIN} counted reviews with text."))
        }
        route("POST", "/v1/ask", Tier.FREE, "Docs helper: ask how to use this API or MCP; answered by AI from the published docs only") { r ->
            val q = runCatching { JSONObject(r.body).optString("question") }.getOrNull()?.trim()?.takeIf { it.length in 3..500 }
                ?: return@route Response(400, JSONObject().put("error", "bad_question").put("message", "send {\"question\": \"...\"} (3 to 500 characters)"))
            val ai = views.ai?.takeIf { it.enabled } ?: return@route Response(503, JSONObject().put("error", "unavailable"))
            val answer = ai.ask(q, web?.llmsText().orEmpty() + "\n\nEndpoints:\n" + routes.joinToString("\n") { "${it.method} ${paths[it]} (${it.tier.name.lowercase()}): ${it.summary}" })
                ?: return@route Response(503, JSONObject().put("error", "unavailable").put("message", "no answer right now; see ${Config.PUBLIC_URL}/api"))
            ok(JSONObject().put("answer", answer).put("label", "Suggested answer, by AI, from the published docs. Check the docs: ${Config.PUBLIC_URL}/api"))
        }
        route("GET", "/v1/changelog", Tier.FREE, "What changed in the API and MCP, newest first") { ok(Changelog.json()) }
        route("GET", "/v1/pricing", Tier.FREE, "Pass price and how to pay") { ok(pricing()) }
        route("GET", "/v1/openapi.json", Tier.FREE, "This API's description") { ok(openApi()) }
        route("POST", "/v1/keys", Tier.FREE, "Redeem a pass payment for an API key") { r -> redeem(r) }
        route("POST", "/v1/checkout", Tier.FREE, "Start a purchase: a secret, and Solana Pay URLs whose memo commits to it. Body {\"calls\": N} for pay-per-call credits instead of a pass") { r ->
            val c = billing.checkout()
            val calls = runCatching { JSONObject(r.body.ifBlank { "{}" }).optLong("calls", 0) }.getOrDefault(0L).coerceIn(0, 10_000_000)
            ok(JSONObject().put("secret", c.secret).put("memo", c.memo).put("solana_pay", if (calls > 0) perCallUrls(c.memo, calls) else payUrls(c.memo))
                .apply { if (calls > 0) put("calls", maxOf(calls, (Config.CREDITS_MIN_SOL / Config.PER_CALL_SOL).toLong())) }
                .put("then", JSONObject().put("post", "/v1/keys").put("body", JSONObject().put("signature", "<payment transaction signature>").put("secret", c.secret)))
                .put("keep_secret", "Anyone holding this secret can redeem the payment. It is not stored until you redeem.").put("test_prices", Config.testPrices))
        }
        route("GET", "/v1/keys/me", Tier.FREE, "Your pass: requests left and end date") { r ->
            val p = r.caller.key?.let { billing.pass(it) } ?: return@route Response(401, JSONObject().put("error", "no_key").put("message", "send Authorization: Bearer <key>"))
            ok(JSONObject().put("wallet", p.wallet).put("requests_left", p.requestsLeft).put("ends_at", p.endsAt))
        }
        route("GET", "/v1/stats", Tier.FREE, "Ledger totals") { ok(views.stats()) }
        route("GET", "/v1/dataset.json", Tier.FREE, "The open dataset: every record, every app's signed score, every signer's public facts (rebuilt every few minutes)") { cachedDataset() }
        route("GET", "/v1/tags", Tier.FREE, "Review tag vocabulary with counts") { ok(views.tags()) }
        // LIMITED, not PAID (compliance review, 9 Oct 2026): every record is already free in /v1/dataset.json, so a pass buys
        // filtering and larger pages, never access to the records themselves.
        route("GET", "/v1/records", Tier.LIMITED, "Every decoded record, newest first; filter by type, package, signer, since, until; page with before") { r ->
            ok(views.records(r.query["type"], r.query["package"], r.query["signer"], r.query["since"]?.toLongOrNull(), r.query["until"]?.toLongOrNull(), r.query["before"], rows(r, Config.FREE_ROWS, 1000)))
        }
        route("GET", "/v1/records/{id}", Tier.FREE, "One record by id (signature or signature:n)") { r -> views.recordById(r.params["id"]!!)?.let(::ok) ?: notFound("no such record") }
        route("GET", "/v1/apps", Tier.LIMITED, "Catalogue search: q, category, sort (name|signed|updated); or packages=a,b,c for several apps at once") { r ->
            r.query["packages"]?.let { list ->
                val pkgs = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(if (r.caller.paid) 100 else Config.FREE_ROWS)
                return@route ok(JSONObject().put("apps", JSONArray(pkgs.mapNotNull { views.app(it) })).put("unknown", JSONArray(pkgs.filter { views.app(it) == null })))
            }
            ok(views.apps(r.query["q"], r.query["category"], r.query["sort"], rows(r, Config.FREE_ROWS, 2000))) }
        route("GET", "/v1/apps/{pkg}", Tier.FREE, "One dApp: signed score, store details and rating, publisher, payments") { r -> views.app(r.params["pkg"]!!)?.let(::ok) ?: notFound("unknown package") }
        route("GET", "/v1/apps/{pkg}/reviews", Tier.LIMITED, "Signed reviews with every signal; limit, offset, sort (newest|helpful|skr|sol: stake as read when each review appeared; never changes a score)") { r -> ok(views.reviews(r.params["pkg"]!!, rows(r, Config.FREE_ROWS), if (r.caller.paid) r.int("offset", 0, 1_000_000) else 0, r.query["sort"])) }
        route("GET", "/v1/apps/{pkg}/versions", Tier.PAID, "Signed score per version") { r -> ok(views.versions(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/bursts", Tier.FREE, "Review and store-rating bursts with sparklines") { r -> ok(views.bursts(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/similar", Tier.PAID, "Copy-paste findings among its reviews") { r -> ok(views.similar(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/store-ratings", Tier.PAID, "Daily store star-count snapshots") { r -> ok(views.storeRatings(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/payments", Tier.PAID, "Payments received, with a monthly series") { r -> views.appPayments(r.params["pkg"]!!)?.let(::ok) ?: notFound("not a confirmed listing") }
        route("GET", "/v1/watch", Tier.PAID, "Store-wide rating bursts in the last N days (days=3)") { r -> ok(views.watch(r.int("days", 3, 30).coerceAtLeast(1))) }
        route("GET", "/v1/changes", Tier.PAID, "Changes seen across the store: new releases (any listing's updated date moved), version codes and signing certificates; since=epoch seconds") { r -> ok(views.changes(r.query["since"]?.toLongOrNull())) }
        route("GET", "/v1/wallets/{address}", Tier.LIMITED, "A wallet: attested device, stake weight, standing, reviews; full=1 with a pass lists its records") { r ->
            val a = r.params["address"]!!
            if (runCatching { Base58.decode(a).size }.getOrNull() != 32) return@route Response(400, JSONObject().put("error", "bad_address"))
            ok(views.wallet(a, r.caller.paid && r.query["full"] == "1"))
        }
        route("GET", "/v1/leaderboard", Tier.LIMITED, "Rankings: metric (${Views.METRICS.joinToString("|")}), category") { r ->
            ok(views.leaderboard(r.query["metric"] ?: "signed_score", r.query["category"], rows(r, Config.FREE_TOP, 500)))
        }
        route("POST", "/v1/hooks", Tier.PAID, "Create a webhook: {url, packages[], events[] (${Hooks.EVENTS.joinToString("|")})}; returns its signing secret once") { r ->
            val h = hooks ?: return@route notFound("hooks are off on this server")
            val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return@route Response(400, JSONObject().put("error", "bad_json"))
            fun arr(k: String) = b.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            try { val k = h.create(r.caller.key!!, b.optString("url"), arr("events"), arr("packages"))
                ok(JSONObject().put("id", k.id).put("url", k.url).put("events", JSONArray(k.events.toList())).put("packages", JSONArray(k.packages.toList())).put("secret", k.secret)
                    .put("verify", "Each POST carries X-Undersigned-Signature: sha256=HMAC-SHA256(secret, raw body) in hex. The secret is shown only now."))
            } catch (e: Hooks.Invalid) { Response(400, JSONObject().put("error", "invalid").put("message", e.message)) }
        }
        route("GET", "/v1/hooks", Tier.PAID, "Your webhooks") { r -> ok(JSONObject().put("hooks", JSONArray((hooks?.list(r.caller.key!!) ?: emptyList()).map { JSONObject().put("id", it.id).put("url", it.url).put("events", JSONArray(it.events.toList())).put("packages", JSONArray(it.packages.toList())) }))) }
        route("POST", "/v1/hooks/{id}/test", Tier.PAID, "Send a signed test ping to one of your webhooks now; returns the receiver's status") { r ->
            hooks?.test(r.caller.key!!, r.params["id"]!!)?.let { st -> ok(JSONObject().put("hook", r.params["id"]).put("status", st).put("delivered", st in 200..299)) } ?: notFound("no such hook on this pass") }
        route("GET", "/v1/hooks/{id}/deliveries", Tier.PAID, "The last 100 delivery attempts to one of your webhooks, with the receiver's status") { r ->
            hooks?.deliveries(r.caller.key!!, r.params["id"]!!)?.let { ok(JSONObject().put("hook", r.params["id"]).put("deliveries", it)) } ?: notFound("no such hook on this pass") }
        route("DELETE", "/v1/hooks/{id}", Tier.PAID, "Delete one of your webhooks") { r -> if (hooks?.delete(r.caller.key!!, r.params["id"]!!) == true) ok(JSONObject().put("deleted", r.params["id"])) else notFound("no such hook on this pass") }
        route("POST", "/v1/tx/review", Tier.FREE, "Build an unsigned review transaction for a browser wallet: {account, package, rating, tags[1-3], text?}") { r -> reviewTx(r) }
        // Local only (never on a public host): the fleet's publisher claims and treasury listings in a few transactions, for the
        // treasury wallet to sign from this Mac's browser. The list is docs/FLEET_SEEDING.md (resources/fleet_seeding.json).
        route("POST", "/v1/tx/seed", Tier.FREE, "Local only: unsigned claim + treasury-listing transactions for the fleet: {account}") { r -> seedTx(r) }
        route("POST", "/v1/tx/record", Tier.FREE, "Build an unsigned helpful vote, reply or flag: {account, kind: up|reply|flag, target, text?, reason?}") { r -> recordTx(r) }
        // Solana Pay transaction request (a QR a phone wallet scans): GET names the requester, POST {account} returns the transaction.
        route("GET", "/v1/pay/review", Tier.FREE, "Solana Pay transaction request: label and icon") { ok(JSONObject().put("label", "Undersigned").put("icon", Config.PUBLIC_URL + "/icon.svg")) }
        route("POST", "/v1/pay/review", Tier.FREE, "Solana Pay transaction request: body {account}; query package, rating, tags (comma-separated), text") { r ->
            val account = runCatching { JSONObject(r.body).optString("account") }.getOrDefault("")
            val res = buildReview(account, r.query["package"].orEmpty().trim(), r.query["rating"]?.toIntOrNull() ?: 0, r.query["tags"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }, r.query["text"]?.trim()?.ifEmpty { null })
            if (res.status != 200) res else ok(JSONObject().put("transaction", res.body.getString("transaction")).put("message", "Signed review of ${res.body.getString("app")}. Permanent once signed; only the network fee is paid."))
        }
        route("GET", "/v1/payments", Tier.LIMITED, "Payments board: confirmed treasuries by USD received") { r -> ok(views.paymentsBoard(rows(r, Config.FREE_TOP, 500))) }
    }

    fun handle(method: String, path: String, query: Map<String, String>, body: String, auth: String?, ip: String, acceptLanguage: String? = null): Response {
        if (method == "OPTIONS") return Response(204, JSONObject())
        // Stateless MCP: no server-initiated stream, so the transport's GET answers 405 (Mcp.kt).
        if (path == "/mcp" && method != "POST") return Response(405, JSONObject().put("error", "method_not_allowed").put("message", "MCP here is stateless: POST JSON-RPC to /mcp"), mapOf("Allow" to "POST"))
        // The fleet seeding page and its builder answer this Mac only. Through the tunnel the address is the last
        // X-Forwarded-For hop (the visitor's, appended by Cloudflare), so a public request never reads as loopback.
        if ((path == "/seed" || path == "/v1/tx/seed" || path == "/v1/admin/triage") && !isLoopback(ip)) return notFound("no such endpoint; see /v1/openapi.json")
        if (path == "/v1/admin/triage" && method == "GET") return Response(200, triage())
        // Human pages and badges: not the metered API. Their own, looser per-IP budget.
        if (method == "GET" && web != null && (!path.startsWith("/v1/") || path.startsWith("/v1/badge/"))) {
            limiter.take("web:$ip", Config.WEB_PER_MINUTE, Config.WEB_PER_DAY)?.let { return Response(429, JSONObject().put("error", "rate_limited"), mapOf("Retry-After" to "60"), "Too many requests; try again in a minute.".toByteArray(), "text/plain; charset=utf-8") }
            return web!!.handle(path, query, I18n.pick(query["lang"] ?: acceptLanguage))
        }
        val match = routes.firstNotNullOfOrNull { rt -> if (rt.method != method) null else rt.pattern.matchEntire(path)?.let { rt to it } }
            ?: return notFound("no such endpoint; see /v1/openapi.json")
        val (rt, m) = match
        val key = auth?.removePrefix("Bearer ")?.trim()?.takeIf { auth.startsWith("Bearer ") && it.isNotEmpty() }
        fun ipBudget(): Response? = limiter.take("ip:$ip", Config.FREE_PER_MINUTE, Config.FREE_PER_DAY)?.let { w ->
            Response(429, JSONObject().put("error", "rate_limited").put("message", "free tier: ${Config.FREE_PER_MINUTE} per minute and ${Config.FREE_PER_DAY} per day per IP ($w used up); a pass lifts it").put("pricing", pricing()),
                mapOf("Retry-After" to if (w == "minute") "60" else "3600")) }
        // A key opens the metered lane only once it is known to exist: a made-up key costs its sender the IP budget, not a
        // limiter entry and a database write each. A key that is sent must be good: an ended or spent pass is told so.
        val known = key != null && billing.pass(key) != null
        val caller = when {
            path == "/v1/keys/me" -> { ipBudget()?.let { return it }; Caller(false, key) }
            key != null && !known -> { ipBudget()?.let { return it }; return paymentRequired("this key does not exist") }
            key != null && rt.tier != Tier.FREE -> {
                if (limiter.take("key:$key", Config.PASS_PER_MINUTE, null) != null) return Response(429, JSONObject().put("error", "rate_limited").put("message", "${Config.PASS_PER_MINUTE} requests per minute per pass"), mapOf("Retry-After" to "60"))
                if (!billing.consume(key)) return paymentRequired("this key has no requests left or has ended")
                Caller(true, key)
            }
            else -> { ipBudget()?.let { return it }; Caller(false, key) }
        }
        if (rt.tier == Tier.PAID && !caller.paid) return paymentRequired("this endpoint needs a pass")
        val params = (pathNames[rt] ?: emptyList()).mapIndexed { i, n -> n to m.groupValues[i + 1] }.toMap()   // URI.path arrives decoded
        // Rate-limit headers on every metered answer (9 Oct 2026): what is left, and when it resets.
        fun withLimits(r: Response): Response {
            val h = LinkedHashMap(r.headers)
            if (caller.paid && caller.key != null) {
                val left = billing.pass(caller.key)
                h["X-RateLimit-Limit"] = Config.PASS_PER_MINUTE.toString()
                limiter.remaining("key:${caller.key}", Config.PASS_PER_MINUTE, null).let { h["X-RateLimit-Remaining"] = it["minute_remaining"].toString(); h["X-RateLimit-Reset"] = it["minute_reset"].toString() }
                left?.let { h["X-Pass-Requests-Left"] = it.requestsLeft.toString(); h["X-Pass-Ends-At"] = it.endsAt.toString() }
            } else {
                val l = limiter.remaining("ip:$ip", Config.FREE_PER_MINUTE, Config.FREE_PER_DAY)
                h["X-RateLimit-Limit"] = Config.FREE_PER_DAY.toString(); h["X-RateLimit-Remaining"] = l["day_remaining"].toString(); h["X-RateLimit-Reset"] = l["day_reset"].toString()
                h["X-RateLimit-Minute-Limit"] = Config.FREE_PER_MINUTE.toString(); h["X-RateLimit-Minute-Remaining"] = l["minute_remaining"].toString()
            }
            return Response(r.status, r.body, h, r.raw, r.contentType)
        }
        return try { withLimits(rt.handler(Req(params, query, body, caller))) }
        catch (e: Billing.Refused) { Response(e.status, JSONObject().put("error", "refused").put("message", e.message)) }
        catch (e: Exception) { log("500 $path: ${e.message?.take(120)}"); Response(500, JSONObject().put("error", "internal")) }
    }

    /**
     * For the operator, on this machine only: every record currently flagged, with the AI's suggested reason (one of the four,
     * or none) and the words it rests on. A suggestion: the moderator reads the text, decides, and signs `mod` themselves.
     */
    fun triage(): JSONObject {
        val st = indexer.state; val ai = views.ai
        val flagged = st.flaggers.filterValues { it.isNotEmpty() }.keys.mapNotNull { id -> st.entries.firstOrNull { it.signature == id } }
        return JSONObject().put("label", "Suggested by AI for the moderator. Low ratings, harsh opinions and criticism are never reasons.")
            .put("items", JSONArray(flagged.map { e -> val text = views.visibleText(e)
                JSONObject().put("id", e.signature).put("flaggers", st.flaggers[e.signature]?.size ?: 0).put("text", text ?: JSONObject.NULL)
                    .put("suggestion", text?.let { ai?.triage(e.signature, it) } ?: JSONObject.NULL) }))
    }

    /**
     * The web review path (docs/API.md H): the server only BUILDS the carrier transaction, the reviewer's own wallet signs
     * and sends it, so the server can never write a review for anyone. Same validation as the app (ReviewMemo.encode), a
     * package that is in the store, a store version (the web has no installed version, so the store's current one is
     * recorded and the page says so), and the sanctions screen the app runs before every transaction.
     */
    private fun reviewTx(r: Req): Response {
        val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return Response(400, JSONObject().put("error", "bad_json"))
        val tags = (b.optJSONArray("tags") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) } }
        return buildReview(b.optString("account"), b.optString("package").trim(), b.optInt("rating"), tags, b.optString("text").trim().ifEmpty { null })
    }

    private fun buildReview(account: String, pkg: String, rating: Int, tagWires: List<String>, text: String?): Response {
        val signer = runCatching { Base58.decode(account) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(400, JSONObject().put("error", "bad_account"))
        val app = indexer.state.byPackage[pkg] ?: return Response(400, JSONObject().put("error", "unknown_package").put("message", "only apps listed in the dApp Store can be reviewed"))
        val version = indexer.state.details[pkg]?.versionCode?.takeIf { it > 0 }
            ?: return Response(409, JSONObject().put("error", "version_unknown").put("message", "the store's current version of this app has not been read yet; try again later"))
        val tags = tagWires.mapNotNull { w -> com.undersigned.protocol.ReviewMemo.Tag.entries.firstOrNull { it.wire == w } }
        val memo = runCatching { com.undersigned.protocol.ReviewMemo.encode(com.undersigned.protocol.ReviewMemo.Review(pkg, rating, tags, version, text)) }
            .getOrElse { return Response(400, JSONObject().put("error", "invalid_review").put("message", "rating 1–5, 1 to 3 tags from the vocabulary, text at most 140 characters (${it.message})")) }
        return signable(account, signer, memo) { it.put("app", app.name).put("version", version) }
    }

    /**
     * A helpful vote, a reply or a flag from the web, on the same terms as a review: the server builds, the wallet signs.
     * The target must be a record this server holds; a reply answers a review; a flag carries one of the four reasons.
     */
    private fun recordTx(r: Req): Response {
        val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return Response(400, JSONObject().put("error", "bad_json"))
        val account = b.optString("account"); val target = b.optString("target")
        val signer = runCatching { Base58.decode(account) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(400, JSONObject().put("error", "bad_account"))
        val rec = indexer.state.entries.firstOrNull { it.signature == target } ?: return Response(400, JSONObject().put("error", "unknown_target").put("message", "no such record"))
        val memo = runCatching { when (b.optString("kind")) {
            "up" -> ReviewMemo.encode(ReviewMemo.Upvote(target))
            "reply" -> { require(rec.memo is ReviewMemo.Review) { "a reply answers a review" }; ReviewMemo.encode(ReviewMemo.Reply(target, b.optString("text").trim())) }
            "flag" -> ReviewMemo.encode(ReviewMemo.Flag(target, ReviewMemo.FlagReason.entries.firstOrNull { it.wire == b.optString("reason") } ?: error("reason must be spam, abuse, offtopic or misleading")))
            else -> error("kind must be up, reply or flag")
        } }.getOrElse { return Response(400, JSONObject().put("error", "invalid_record").put("message", it.message)) }
        return signable(account, signer, memo) { it.put("kind", b.optString("kind")) }
    }

    private fun seedTx(r: Req): Response {
        val account = runCatching { JSONObject(r.body).optString("account") }.getOrDefault("")
        val signer = runCatching { Base58.decode(account) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(400, JSONObject().put("error", "bad_account"))
        val fleet = JSONArray(Api::class.java.getResourceAsStream("/fleet_seeding.json")!!.bufferedReader().use { it.readText() })
        val mine = indexer.state.entries.filter { it.signer == account }
        val claimed = mine.mapNotNull { (it.memo as? ReviewMemo.Claim)?.packageName }.toSet()
        // A listing stands if this wallet's latest trs/trx for the package is a trs.
        val listed = mine.filter { it.memo is ReviewMemo.Treasury || it.memo is ReviewMemo.Untreasury }.sortedBy { it.blockTime }
            .associate { e -> ((e.memo as? ReviewMemo.Treasury)?.packageName ?: (e.memo as ReviewMemo.Untreasury).packageName) to (e.memo is ReviewMemo.Treasury) }.filterValues { it }.keys
        val memos = ArrayList<String>(); val plan = JSONArray()
        for (i in 0 until fleet.length()) { val a = fleet.getJSONObject(i); val p = a.getString("package")
            if (p !in claimed) { memos.add(ReviewMemo.encode(ReviewMemo.Claim(p))); plan.put("claim $p") }
            if (p !in listed) { memos.add(ReviewMemo.encode(ReviewMemo.Treasury(p, a.getString("code")))); plan.put("list $p") }
        }
        if (memos.isEmpty()) return ok(JSONObject().put("transactions", JSONArray()).put("records", 0).put("message", "Every fleet app is already claimed and listed by this wallet."))
        if (sanctioned(account)) return Response(403, JSONObject().put("error", "refused"))
        val bh = runCatching { Base58.decode(blockhash()) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(503, JSONObject().put("error", "rpc_unavailable"))
        val txs = Tx.memoBatches(signer, memos, bh)
        return ok(JSONObject().put("transactions", JSONArray(txs.map { java.util.Base64.getEncoder().encodeToString(it) })).put("records", memos.size).put("plan", plan))
    }

    /** Screens the wallet, takes a blockhash, builds the carrier and says whether the wallet is counted today (cache only). */
    private fun signable(account: String, signer: ByteArray, memo: String, extra: (JSONObject) -> JSONObject): Response {
        if (sanctioned(account)) return Response(403, JSONObject().put("error", "refused").put("message", "This wallet address appears on a sanctions list and cannot be used here."))
        val bh = runCatching { Base58.decode(blockhash()) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(503, JSONObject().put("error", "rpc_unavailable"))
        val tx = runCatching { Tx.reviewTransaction(signer, memo, bh) }.getOrElse { return Response(400, JSONObject().put("error", "too_large").put("message", it.message)) }
        val known = indexer.state.facts[account] ?: indexer.cachedFacts(account)
        if (known == null) indexer.wanted.add(account)
        return ok(extra(JSONObject().put("transaction", java.util.Base64.getEncoder().encodeToString(tx)).put("memo", memo).put("counted_now", known?.hasGenesisToken ?: JSONObject.NULL)
            .put("counted_only_if", "the signing wallet holds a Seeker Genesis Token").put("permanent", "A signed record stays on-chain for good; withdrawing it later leaves the record.")))
    }

    /** The dataset is built at most once per rebuild and served from memory, so a crawler cannot make the server rebuild it per request. */
    @Volatile private var dataset: Pair<Long, JSONObject>? = null
    private fun cachedDataset(): Response {
        val at = indexer.state.builtAt
        val d = dataset?.takeIf { it.first == at }?.second ?: views.dataset().also { dataset = at to it }
        return Response(200, d, mapOf("Cache-Control" to "public, max-age=600", "Content-Disposition" to "inline; filename=undersigned-dataset.json"))
    }

    /**
     * 402 with everything an agent needs to pay and retry without reading docs: the pricing, and `accepts`, the two ways to
     * pay in the shape x402 uses (scheme, network, payTo, amounts), each paid with one on-chain transfer and redeemed once.
     */
    private fun paymentRequired(why: String) = Response(402, JSONObject().put("error", "payment_required").put("message", why).put("pricing", pricing())
        .put("accepts", JSONArray()
            .put(JSONObject().put("scheme", "undersigned-pass").put("network", "solana-mainnet").put("payTo", Config.TREASURY).put("reference", Config.API_REFERENCE)
                .put("amount", JSONObject().put("sol", Config.passSol).put("skr", Config.passSkr)).put("buys", "${Config.PASS_REQUESTS} requests over ${Config.PASS_DAYS} days"))
            .put(JSONObject().put("scheme", "undersigned-per-call").put("network", "solana-mainnet").put("payTo", Config.TREASURY).put("reference", Config.API_REFERENCE)
                .put("perCall", JSONObject().put("sol", Config.PER_CALL_SOL).put("skr", Config.PER_CALL_SKR)).put("minimum", JSONObject().put("sol", Config.CREDITS_MIN_SOL).put("skr", Config.CREDITS_MIN_SKR))
                .put("buys", "calls at the per-call rate, valid ${Config.CREDITS_DAYS} days")))
        .put("how", "POST /v1/checkout (body {\"calls\": N} for per-call) -> pay a solana_pay URL from any wallet -> POST /v1/keys {signature, secret} -> retry with Authorization: Bearer <key>"))

    private fun redeem(r: Req): Response {
        val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return Response(400, JSONObject().put("error", "bad_json").put("message", "send {\"signature\": \"<payment tx>\", \"message_signature\": \"<wallet signature>\"}"))
        val sig = b.optString("signature"); val msig = b.optString("message_signature"); val secret = b.optString("secret")
        if (sig.isEmpty() || (msig.isEmpty() && secret.isEmpty())) return Response(400, JSONObject().put("error", "missing_fields").put("message", "send signature plus either secret (from POST /v1/checkout) or message_signature (the paying wallet's signature of \"${billing.message(sig.ifEmpty { "<signature>" })}\")"))
        val p = billing.redeem(sig, msig.ifEmpty { null }, secret = secret.ifEmpty { null })
        return ok(JSONObject().put("key", p.key).put("wallet", p.wallet).put("requests_left", p.requestsLeft).put("ends_at", p.endsAt)
            .put("use", "Authorization: Bearer ${p.key}"))
    }

    fun pricing(): JSONObject {
        return JSONObject()
            .put("pass", JSONObject().put("requests", Config.PASS_REQUESTS).put("days", Config.PASS_DAYS).put("per_minute", Config.PASS_PER_MINUTE)
                .put("price", JSONObject().put("sol", Config.passSol).put("skr", Config.passSkr).put("skr_with_seeker_genesis_token", Config.passSkrSgt)))
            .put("test_prices", Config.testPrices)
            .put("checkout", "POST /v1/checkout for a secret and ready Solana Pay URLs; no message signing needed")
            .put("free", JSONObject().put("per_minute", Config.FREE_PER_MINUTE).put("per_day", Config.FREE_PER_DAY).put("rows", Config.FREE_ROWS))
            .put("pay_to", Config.TREASURY).put("reference", Config.API_REFERENCE).put("memo", Config.PAYMENT_MEMO)
            .put("solana_pay", payUrls(Config.PAYMENT_MEMO))
            .put("redeem", JSONObject().put("post", "/v1/keys").put("body", JSONObject().put("signature", "<payment transaction signature>").put("message_signature", "<the paying wallet's signature of the message>"))
                .put("message", "Undersigned API key for <payment transaction signature>"))
            .put("pay_per_call", JSONObject().put("per_call", JSONObject().put("sol", Config.PER_CALL_SOL).put("skr", Config.PER_CALL_SKR))
                .put("minimum", JSONObject().put("sol", Config.CREDITS_MIN_SOL).put("skr", Config.CREDITS_MIN_SKR)).put("days", Config.CREDITS_DAYS)
                .put("how", "POST /v1/checkout with {\"calls\": N}, pay the URL it returns, redeem with POST /v1/keys. Any payment from the minimum up to just under the pass price buys calls; at the pass price or more it buys a pass."))
            .put("note", "Paying again adds ${Config.PASS_REQUESTS} requests and ${Config.PASS_DAYS} days to a pass, or more calls to credits. The payment is read from balances on-chain; only the payer (or the checkout secret's holder) can redeem it.")
    }

    /** Solana Pay URLs for [calls] pay-per-call credits (at least the minimum top-up), with the pass reference and [memo]. */
    fun perCallUrls(memo: String, calls: Long): JSONObject {
        // Whole lamports and raw SKR units, never doubles: 1,000 x 0.00001 must read 0.01, not 0.010000001.
        val lamports = maxOf(Config.lamports(Config.CREDITS_MIN_SOL), calls * Config.perCallLamports)
        val skrRaw = maxOf(Config.skrRaw(Config.CREDITS_MIN_SKR), calls * Config.perCallSkrRaw)
        val sol = java.math.BigDecimal.valueOf(lamports, 9).stripTrailingZeros().toPlainString()
        val skr = java.math.BigDecimal.valueOf(skrRaw, 6).stripTrailingZeros().toPlainString()
        fun url(amount: String, token: String?) = "solana:${Config.TREASURY}?amount=$amount" + (token?.let { "&spl-token=$it" } ?: "") +
            "&reference=${Config.API_REFERENCE}&label=${enc("Undersigned")}&message=${enc("Undersigned API calls")}&memo=${enc(memo)}"
        return JSONObject().put("sol", url(sol, null)).put("skr", url(skr, Config.SKR_MINT))
    }

    /** Solana Pay transfer requests for each rail, with the pass reference and [memo]. */
    fun payUrls(memo: String): JSONObject {
        fun amt(v: Double) = v.toBigDecimal().stripTrailingZeros().toPlainString()
        fun url(amount: String, token: String?) = "solana:${Config.TREASURY}?amount=$amount" + (token?.let { "&spl-token=$it" } ?: "") +
            "&reference=${Config.API_REFERENCE}&label=${enc("Undersigned")}&message=${enc("Undersigned API pass")}&memo=${enc(memo)}"
        return JSONObject().put("sol", url(amt(Config.passSol), null)).put("skr", url(amt(Config.passSkr), Config.SKR_MINT)).put("skr_with_seeker_genesis_token", url(amt(Config.passSkrSgt), Config.SKR_MINT))
    }

    /** A sample call per main route, whose live answer gives the response schema (types inferred, nothing hand-written to drift). */
    private val SAMPLES = mapOf("/v1/apps" to "/v1/apps?limit=1", "/v1/apps/{pkg}" to "/v1/apps/com.solflare.mobile", "/v1/apps/{pkg}/reviews" to "/v1/apps/com.solflare.mobile/reviews?limit=1",
        "/v1/apps/{pkg}/bursts" to "/v1/apps/com.solflare.mobile/bursts", "/v1/stats" to "/v1/stats", "/v1/pricing" to "/v1/pricing", "/v1/records" to "/v1/records?limit=1",
        "/v1/tags" to "/v1/tags", "/v1/health" to "/v1/health", "/v1/leaderboard" to "/v1/leaderboard?metric=store_rating&limit=1", "/v1/payments" to "/v1/payments?limit=1")
    private val schemaCache = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
    private fun sampleSchema(method: String, sample: String): JSONObject? = if (method != "GET") null else schemaCache.getOrPut(sample) {
        val u = java.net.URI("http://x$sample"); val q = u.rawQuery?.split('&')?.associate { it.substringBefore('=') to it.substringAfter('=') } ?: emptyMap()
        val r = runCatching { handle("GET", u.path, q, "", null, "127.0.0.1") }.getOrNull()
        if (r == null || r.status != 200) return null
        inferSchema(r.body)
    }
    /** JSON Schema from a value: objects with their properties, arrays from their first item, nulls as nullable. */
    private fun inferSchema(v: Any?): JSONObject = when (v) {
        is JSONObject -> JSONObject().put("type", "object").put("properties", JSONObject().apply { v.keys().forEach { k -> put(k, inferSchema(v.opt(k))) } })
        is JSONArray -> JSONObject().put("type", "array").put("items", if (v.length() > 0) inferSchema(v.opt(0)) else JSONObject())
        is Boolean -> JSONObject().put("type", "boolean")
        is Int, is Long -> JSONObject().put("type", "integer")
        is Number -> JSONObject().put("type", "number")
        is String -> JSONObject().put("type", "string")
        else -> JSONObject().put("nullable", true)
    }

    private fun ref(schema: String, d: String) = JSONObject().put("description", d)
        .put("content", JSONObject().put("application/json", JSONObject().put("schema", JSONObject().put("\$ref", "#/components/schemas/$schema"))))

    /** Each query parameter's real type: integers with bounds, and enums where the values are fixed. */
    private fun paramSchema(path: String, name: String): JSONObject = when (name) {
        "limit" -> JSONObject().put("type", "integer").put("minimum", 1).put("maximum", if (path == "/v1/records") 1000 else 500)
        "offset" -> JSONObject().put("type", "integer").put("minimum", 0)
        "days" -> JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 30)
        "since", "until" -> JSONObject().put("type", "integer").put("description", "Epoch seconds")
        "full" -> JSONObject().put("type", "string").put("enum", JSONArray().put("1"))
        "metric" -> JSONObject().put("type", "string").put("enum", JSONArray(Views.METRICS))
        "sort" -> JSONObject().put("type", "string").put("enum", JSONArray(if (path.endsWith("/reviews")) listOf("newest", "helpful", "skr", "sol") else listOf("name", "signed", "updated")))
        else -> JSONObject().put("type", "string")
    }

    fun openApi(): JSONObject {
        val p = JSONObject()
        for (rt in routes) {
            val path = paths[rt]!!
            if (path == "/v1/tx/seed") continue   // loopback-only fleet seeding; answers 404 publicly
            val qp = QUERY[path]?.map { JSONObject().put("name", it).put("in", "query").put("required", false).put("schema", paramSchema(path, it)) } ?: emptyList()
            val op = JSONObject().put("summary", rt.summary).put("x-tier", rt.tier.name.lowercase())
                .put("parameters", JSONArray((pathNames[rt] ?: emptyList()).map { JSONObject().put("name", it).put("in", "path").put("required", true).put("schema", JSONObject().put("type", "string")) } + qp))
                .put("responses", JSONObject().put("200", JSONObject().put("description", "JSON; X-RateLimit-Limit / -Remaining / -Reset headers say what is left")
                        .put("content", JSONObject().put("application/json", JSONObject().put("schema", SAMPLES[path]?.let { sampleSchema(rt.method, it) } ?: JSONObject().put("type", "object")))))
                    .put("400", ref("Error", "A malformed request"))
                    .put("404", ref("Error", "Not found"))
                    .put("429", ref("Error", "Rate limited; Retry-After says when to try again"))
                    .apply { if (rt.tier != Tier.FREE) put("402", ref("PaymentRequired", "A pass is needed; the body carries the price, Solana Pay URLs and how to redeem")) })
                .apply { if (rt.tier != Tier.FREE) put("security", JSONArray().put(JSONObject().put("pass", JSONArray())).put(JSONObject())) }
            (p.optJSONObject(path) ?: JSONObject().also { p.put(path, it) }).put(rt.method.lowercase(), op)
        }
        return JSONObject().put("openapi", "3.0.3")
            .put("info", JSONObject().put("title", "Undersigned API").put("version", "1").put("description", "Signed Seeker reviews, dApp Store data and on-chain payments for Solana dApp Store apps. Read-only. See docs/API.md."))
            .put("servers", JSONArray().put(JSONObject().put("url", Config.PUBLIC_URL).put("description", "Public API")))
            .put("components", JSONObject().put("securitySchemes", JSONObject().put("pass", JSONObject().put("type", "http").put("scheme", "bearer").put("description", "An API pass key (us_…); see /v1/pricing")))
                .put("schemas", JSONObject()
                    .put("Error", JSONObject().put("type", "object").put("required", JSONArray().put("error"))
                        .put("properties", JSONObject().put("error", JSONObject().put("type", "string")).put("message", JSONObject().put("type", "string"))))
                    .put("PaymentRequired", JSONObject().put("type", "object").put("required", JSONArray().put("error").put("pricing"))
                        .put("properties", JSONObject().put("error", JSONObject().put("type", "string").put("enum", JSONArray().put("payment_required")))
                            .put("message", JSONObject().put("type", "string")).put("pricing", JSONObject().put("type", "object").put("description", "As GET /v1/pricing"))))))
            .put("paths", p)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Query parameters per path, for the OpenAPI description. */
    private val QUERY = mapOf(
        "/v1/records" to listOf("type", "package", "signer", "since", "until", "before", "limit"), "/v1/apps" to listOf("q", "category", "sort", "limit", "packages"),
        "/v1/apps/{pkg}/reviews" to listOf("limit", "offset", "sort"), "/v1/watch" to listOf("days"), "/v1/changes" to listOf("since"), "/v1/wallets/{address}" to listOf("full"),
        "/v1/leaderboard" to listOf("metric", "category", "limit"), "/v1/payments" to listOf("limit"),
    )

    // ---- JDK server glue ------------------------------------------------------------------------------------------

    /** Drops rate-limit windows that have passed (caller addresses are held in memory only, for at most a day). */
    fun sweepLimiter() = limiter.sweep()

    companion object {
        /**
         * The client address. Behind a trusted proxy it is the LAST hop of X-Forwarded-For — the one the proxy itself
         * appended; the first hop is whatever the client wrote and would let anyone choose their own rate-limit bucket.
         */
        fun isLoopback(ip: String) = ip == "127.0.0.1" || ip == "::1" || ip == "0:0:0:0:0:0:0:1"

        fun clientIp(remote: String, forwardedFor: String?, trustProxy: Boolean): String =
            if (trustProxy) forwardedFor?.split(',')?.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: remote else remote
    }

    fun serve(port: Int): HttpServer {
        // A connection that sends its request slowly, or reads its answer slowly, gives up its thread after these.
        System.setProperty("sun.net.httpserver.maxReqTime", "30"); System.setProperty("sun.net.httpserver.maxRspTime", "120")
        val server = HttpServer.create(InetSocketAddress(Config.HOST, port), 64)
        server.executor = Executors.newFixedThreadPool(32)
        server.createContext("/") { ex -> respond(ex) }
        server.start()
        return server
    }

    private fun respond(ex: HttpExchange) {
        try {
            val query = runCatching { ex.requestURI.rawQuery?.split('&')?.mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it[0].isNotEmpty() }?.let { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it.getOrElse(1) { "" }, "UTF-8") } }?.toMap() ?: emptyMap() }.getOrNull()
            val body = if (ex.requestMethod == "POST") ex.requestBody.use { String(it.readNBytes(16_384)) } else ""
            val ip = clientIp(ex.remoteAddress.address.hostAddress, ex.requestHeaders.getFirst("X-Forwarded-For"), Config.TRUST_PROXY)
            val head = ex.requestMethod == "HEAD"
            val r = if (query == null) Response(400, JSONObject().put("error", "bad_query").put("message", "a percent-escape in the query is malformed"))
                else handle(if (head) "GET" else ex.requestMethod, ex.requestURI.path.trimEnd('/').ifEmpty { "/" }, query, body, ex.requestHeaders.getFirst("Authorization"), ip, ex.requestHeaders.getFirst("Accept-Language"))
            val bytes = if (r.status == 204 || head) ByteArray(0) else r.raw ?: r.body.toString(if (query?.get("pretty") == "1") 2 else 0).toByteArray()
            ex.responseHeaders.add("Content-Type", r.contentType)
            ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Mcp-Protocol-Version, Mcp-Session-Id")
            ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
            r.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            ex.responseHeaders.add("X-Content-Type-Options", "nosniff")
            ex.sendResponseHeaders(r.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        } catch (e: Exception) { log("respond: ${e.message?.take(100)}") } finally { ex.close() }
    }
}

/**
 * `api hide <record id> <reason…>`, `api unhide <record id>`, `api hidden`: the operator's hide list (see /report). The
 * running server picks a change up at its next rebuild (within five minutes). Anything else starts the server.
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] in setOf("hide", "unhide", "hidden")) {
        val ix = Indexer(Db(), Rpc(), Store())
        when (args[0]) {
            // The same four reasons a moderator may give, and no others (compliance review, 9 Oct 2026); the list is public at /moderation.
            "hide" -> { val why = args.getOrNull(2)?.let { com.undersigned.protocol.ReviewMemo.ModReason.fromWire(it) }
                require(args.size == 3 && why != null) { "usage: api hide <record id> <${com.undersigned.protocol.ReviewMemo.ModReason.entries.joinToString("|") { it.wire }}>" }
                ix.hide(args[1], why.wire); println("hidden: ${args[1]} (${why.wire})") }
            "unhide" -> println(if (ix.unhide(args[1])) "shown again: ${args[1]}" else "not on the list: ${args[1]}")
            else -> ix.operatorHidden().forEach { (id, why) -> println("$id\t$why") }
        }
        return
    }
    Config.checkStartup()
    // Webhook delivery reuses the resolution that was checked for private addresses (Hooks.checkUrl) for a minute.
    java.security.Security.setProperty("networkaddress.cache.ttl", "60")
    val db = Db()
    val rpc = Rpc()
    val indexer = Indexer(db, rpc)
    val boards = Boards(indexer, Board(db, rpc), Prices(db))
    val views = Views(indexer, boards, db, Ai(db, local = Ai::ollama).also { log("ai: " + if (it.enabled) "on (${Config.AI_MODEL}, cap ${Config.AI_DAILY_CALLS} calls a day)" else "off (no ANTHROPIC_API_KEY)") })
    val billing = Billing(db, { sig -> runCatching { rpc.tx(sig, Config.TREASURY) }.getOrNull() }, { w -> indexer.facts(w).hasGenesisToken }, Screening::screenForPayment)
    val hooks = Hooks(db, billing)
    indexer.onRebuilt = { fresh -> hooks.tick(indexer, fresh) }
    if (Config.INDEX) hooks.startWorker()
    hooks.weeklyDigest = { views.digest() }
    val api = Api(views, billing, indexer, hooks = hooks)
    api.web = Web(views, indexer).also { w -> w.apiInfo = { api.pricing() }; w.apiDocs = { api.openApi() } }
    runCatching { indexer.rebuild() }   // serve what the database already holds at once
    if (Config.INDEX) {
        indexer.start()
        Thread({ while (true) { Thread.sleep(60_000); runCatching { boards.refresh(indexer.state) }.onFailure { log("boards: ${it.message?.take(80)}") }; Thread.sleep(14 * 60_000L) } }, "boards").apply { isDaemon = true }.start()
    }
    Thread({ while (true) { Thread.sleep(10 * 60_000L); runCatching { api.sweepLimiter() } } }, "limiter").apply { isDaemon = true }.start()
    api.serve(Config.PORT)
    log("Undersigned API on ${Config.HOST}:${Config.PORT} (db ${Config.DB_PATH}, indexing ${Config.INDEX}, public ${Config.PUBLIC}${if (Config.testPrices) ", TEST PRICES" else ""})")
}
