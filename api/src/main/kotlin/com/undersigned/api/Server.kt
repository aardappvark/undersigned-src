package com.undersigned.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
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
        route("GET", "/v1/health", Tier.FREE, "Indexer status") { ok(JSONObject().put("ok", true).put("indexed_at", indexer.state.builtAt / 1000).put("ledger_synced", indexer.lastLedgerSync / 1000).put("store_synced", indexer.lastStoreSync / 1000)) }
        route("GET", "/v1/pricing", Tier.FREE, "Pass price and how to pay") { ok(pricing()) }
        route("GET", "/v1/openapi.json", Tier.FREE, "This API's description") { ok(openApi()) }
        route("POST", "/v1/keys", Tier.FREE, "Redeem a pass payment for an API key") { r -> redeem(r) }
        route("POST", "/v1/checkout", Tier.FREE, "Start a pass purchase: a secret, and Solana Pay URLs whose memo commits to it") {
            val c = billing.checkout()
            ok(JSONObject().put("secret", c.secret).put("memo", c.memo).put("solana_pay", payUrls(c.memo))
                .put("then", JSONObject().put("post", "/v1/keys").put("body", JSONObject().put("signature", "<payment transaction signature>").put("secret", c.secret)))
                .put("keep_secret", "Anyone holding this secret can redeem the payment. It is not stored until you redeem.").put("test_prices", Config.testPrices))
        }
        route("GET", "/v1/keys/me", Tier.FREE, "Your pass: requests left and end date") { r ->
            val p = r.caller.key?.let { billing.pass(it) } ?: return@route Response(401, JSONObject().put("error", "no_key").put("message", "send Authorization: Bearer <key>"))
            ok(JSONObject().put("wallet", p.wallet).put("requests_left", p.requestsLeft).put("ends_at", p.endsAt))
        }
        route("GET", "/v1/stats", Tier.FREE, "Ledger totals") { ok(views.stats()) }
        route("GET", "/v1/tags", Tier.FREE, "Review tag vocabulary with counts") { ok(views.tags()) }
        route("GET", "/v1/records", Tier.PAID, "Every decoded record, newest first; filter by type, package, signer, since, until; page with before") { r ->
            ok(views.records(r.query["type"], r.query["package"], r.query["signer"], r.query["since"]?.toLongOrNull(), r.query["until"]?.toLongOrNull(), r.query["before"], r.int("limit", 100, 1000)))
        }
        route("GET", "/v1/records/{id}", Tier.FREE, "One record by id (signature or signature:n)") { r -> views.recordById(r.params["id"]!!)?.let(::ok) ?: notFound("no such record") }
        route("GET", "/v1/apps", Tier.LIMITED, "Catalogue search: q, category, sort (name|signed|updated)") { r -> ok(views.apps(r.query["q"], r.query["category"], r.query["sort"], rows(r, Config.FREE_ROWS, 2000))) }
        route("GET", "/v1/apps/{pkg}", Tier.FREE, "One dApp: signed score, store details and rating, publisher, payments") { r -> views.app(r.params["pkg"]!!)?.let(::ok) ?: notFound("unknown package") }
        route("GET", "/v1/apps/{pkg}/reviews", Tier.LIMITED, "Signed reviews with every signal; limit, offset") { r -> ok(views.reviews(r.params["pkg"]!!, rows(r, Config.FREE_ROWS), if (r.caller.paid) r.int("offset", 0, 1_000_000) else 0)) }
        route("GET", "/v1/apps/{pkg}/versions", Tier.PAID, "Signed score per version") { r -> ok(views.versions(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/bursts", Tier.PAID, "Review and store-rating bursts with sparklines") { r -> ok(views.bursts(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/similar", Tier.PAID, "Copy-paste findings among its reviews") { r -> ok(views.similar(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/store-ratings", Tier.PAID, "Daily store star-count snapshots") { r -> ok(views.storeRatings(r.params["pkg"]!!)) }
        route("GET", "/v1/apps/{pkg}/payments", Tier.PAID, "Payments received, with a monthly series") { r -> views.appPayments(r.params["pkg"]!!)?.let(::ok) ?: notFound("not a confirmed listing") }
        route("GET", "/v1/watch", Tier.PAID, "Store-wide rating bursts in the last N days (days=3)") { r -> ok(views.watch(r.int("days", 3, 30).coerceAtLeast(1))) }
        route("GET", "/v1/changes", Tier.PAID, "Version and certificate changes seen; since=epoch seconds") { r -> ok(views.changes(r.query["since"]?.toLongOrNull())) }
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
        route("DELETE", "/v1/hooks/{id}", Tier.PAID, "Delete one of your webhooks") { r -> if (hooks?.delete(r.caller.key!!, r.params["id"]!!) == true) ok(JSONObject().put("deleted", r.params["id"])) else notFound("no such hook on this pass") }
        route("POST", "/v1/tx/review", Tier.FREE, "Build an unsigned review transaction for a browser wallet: {account, package, rating, tags[1-3], text?}") { r -> reviewTx(r) }
        route("GET", "/v1/payments", Tier.LIMITED, "Payments board: confirmed treasuries by USD received") { r -> ok(views.paymentsBoard(rows(r, Config.FREE_TOP, 500))) }
    }

    fun handle(method: String, path: String, query: Map<String, String>, body: String, auth: String?, ip: String): Response {
        if (method == "OPTIONS") return Response(204, JSONObject())
        // Human pages and badges: not the metered API. Their own, looser per-IP budget.
        if (method == "GET" && web != null && (!path.startsWith("/v1/") || path.startsWith("/v1/badge/"))) {
            limiter.take("web:$ip", Config.WEB_PER_MINUTE, Config.WEB_PER_DAY)?.let { return Response(429, JSONObject().put("error", "rate_limited"), mapOf("Retry-After" to "60"), "Too many requests; try again in a minute.".toByteArray(), "text/plain; charset=utf-8") }
            return web!!.handle(path, query)
        }
        val match = routes.firstNotNullOfOrNull { rt -> if (rt.method != method) null else rt.pattern.matchEntire(path)?.let { rt to it } }
            ?: return notFound("no such endpoint; see /v1/openapi.json")
        val (rt, m) = match
        val key = auth?.removePrefix("Bearer ")?.trim()?.takeIf { auth.startsWith("Bearer ") && it.isNotEmpty() }
        // A key that is sent must be good: an ended or spent pass is told so, never silently served as free.
        val caller = if (key != null && rt.tier != Tier.FREE || key != null && path == "/v1/keys/me") {
            if (path == "/v1/keys/me") Caller(false, key)
            else {
                if (limiter.take("key:$key", Config.PASS_PER_MINUTE, null) != null) return Response(429, JSONObject().put("error", "rate_limited").put("message", "${Config.PASS_PER_MINUTE} requests per minute per pass"), mapOf("Retry-After" to "60"))
                if (!billing.consume(key)) return paymentRequired("this key has no requests left, has ended, or does not exist")
                Caller(true, key)
            }
        } else {
            limiter.take("ip:$ip", Config.FREE_PER_MINUTE, Config.FREE_PER_DAY)?.let { w ->
                return Response(429, JSONObject().put("error", "rate_limited").put("message", "free tier: ${Config.FREE_PER_MINUTE} per minute and ${Config.FREE_PER_DAY} per day per IP ($w used up); a pass lifts it").put("pricing", pricing()),
                    mapOf("Retry-After" to if (w == "minute") "60" else "3600"))
            }
            Caller(false, key)
        }
        if (rt.tier == Tier.PAID && !caller.paid) return paymentRequired("this endpoint needs a pass")
        val params = (pathNames[rt] ?: emptyList()).mapIndexed { i, n -> n to URLDecoder.decode(m.groupValues[i + 1], "UTF-8") }.toMap()
        return try { rt.handler(Req(params, query, body, caller)) }
        catch (e: Billing.Refused) { Response(e.status, JSONObject().put("error", "refused").put("message", e.message)) }
        catch (e: Exception) { log("500 $path: ${e.message?.take(120)}"); Response(500, JSONObject().put("error", "internal")) }
    }

    /**
     * The web review path (docs/API.md H): the server only BUILDS the carrier transaction, the reviewer's own wallet signs
     * and sends it, so the server can never write a review for anyone. Same validation as the app (ReviewMemo.encode), a
     * package that is in the store, a store version (the web has no installed version, so the store's current one is
     * recorded and the page says so), and the sanctions screen the app runs before every transaction.
     */
    private fun reviewTx(r: Req): Response {
        val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return Response(400, JSONObject().put("error", "bad_json"))
        val account = b.optString("account"); val pkg = b.optString("package").trim()
        val signer = runCatching { Base58.decode(account) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(400, JSONObject().put("error", "bad_account"))
        val app = indexer.state.byPackage[pkg] ?: return Response(400, JSONObject().put("error", "unknown_package").put("message", "only apps listed in the dApp Store can be reviewed"))
        val version = indexer.state.details[pkg]?.versionCode?.takeIf { it > 0 }
            ?: return Response(409, JSONObject().put("error", "version_unknown").put("message", "the store's current version of this app has not been read yet; try again later"))
        val tags = (b.optJSONArray("tags") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> com.undersigned.protocol.ReviewMemo.Tag.entries.firstOrNull { it.wire == a.optString(i) } } }
        val text = b.optString("text").trim().ifEmpty { null }
        val memo = runCatching { com.undersigned.protocol.ReviewMemo.encode(com.undersigned.protocol.ReviewMemo.Review(pkg, b.optInt("rating"), tags, version, text)) }
            .getOrElse { return Response(400, JSONObject().put("error", "invalid_review").put("message", "rating 1–5, 1 to 3 tags from the vocabulary, text at most 140 characters (${it.message})")) }
        if (sanctioned(account)) return Response(403, JSONObject().put("error", "refused").put("message", "This wallet address appears on a sanctions list and cannot be used here."))
        val bh = runCatching { Base58.decode(blockhash()) }.getOrNull()?.takeIf { it.size == 32 } ?: return Response(503, JSONObject().put("error", "rpc_unavailable"))
        val tx = runCatching { Tx.reviewTransaction(signer, memo, bh) }.getOrElse { return Response(400, JSONObject().put("error", "too_large").put("message", it.message)) }
        return ok(JSONObject().put("transaction", java.util.Base64.getEncoder().encodeToString(tx)).put("memo", memo).put("app", app.name).put("version", version)
            .put("counted_only_if", "the signing wallet holds a Seeker Genesis Token").put("permanent", "A signed review stays on-chain for good; withdrawing it later leaves the record."))
    }

    private fun paymentRequired(why: String) = Response(402, JSONObject().put("error", "payment_required").put("message", why).put("pricing", pricing()))

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
            .put("note", "Paying again adds ${Config.PASS_REQUESTS} requests and ${Config.PASS_DAYS} days. The payment is read from balances on-chain; only the paying wallet can redeem it.")
    }

    /** Solana Pay transfer requests for each rail, with the pass reference and [memo]. */
    fun payUrls(memo: String): JSONObject {
        fun amt(v: Double) = v.toBigDecimal().stripTrailingZeros().toPlainString()
        fun url(amount: String, token: String?) = "solana:${Config.TREASURY}?amount=$amount" + (token?.let { "&spl-token=$it" } ?: "") +
            "&reference=${Config.API_REFERENCE}&label=${enc("Undersigned")}&message=${enc("Undersigned API pass")}&memo=${enc(memo)}"
        return JSONObject().put("sol", url(amt(Config.passSol), null)).put("skr", url(amt(Config.passSkr), Config.SKR_MINT)).put("skr_with_seeker_genesis_token", url(amt(Config.passSkrSgt), Config.SKR_MINT))
    }

    fun openApi(): JSONObject {
        val p = JSONObject()
        for (rt in routes) {
            val path = paths[rt]!!
            val op = JSONObject().put("summary", rt.summary).put("x-tier", rt.tier.name.lowercase())
                .put("parameters", JSONArray((pathNames[rt] ?: emptyList()).map { JSONObject().put("name", it).put("in", "path").put("required", true).put("schema", JSONObject().put("type", "string")) }))
                .put("responses", JSONObject().put("200", JSONObject().put("description", "JSON")).apply { if (rt.tier == Tier.PAID) put("402", JSONObject().put("description", "A pass is needed; the body carries the price and how to pay")) })
            (p.optJSONObject(path) ?: JSONObject().also { p.put(path, it) }).put(rt.method.lowercase(), op)
        }
        return JSONObject().put("openapi", "3.0.3")
            .put("info", JSONObject().put("title", "Undersigned API").put("version", "1").put("description", "Signed Seeker reviews, dApp Store data and on-chain payments for Solana dApp Store apps. Read-only. See docs/API.md."))
            .put("components", JSONObject().put("securitySchemes", JSONObject().put("pass", JSONObject().put("type", "http").put("scheme", "bearer"))))
            .put("paths", p)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---- JDK server glue ------------------------------------------------------------------------------------------

    fun serve(port: Int): HttpServer {
        val server = HttpServer.create(InetSocketAddress(Config.HOST, port), 0)
        server.executor = Executors.newFixedThreadPool(16)
        server.createContext("/") { ex -> respond(ex) }
        server.start()
        return server
    }

    private fun respond(ex: HttpExchange) {
        try {
            val query = ex.requestURI.rawQuery?.split('&')?.mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it[0].isNotEmpty() }?.let { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it.getOrElse(1) { "" }, "UTF-8") } }?.toMap() ?: emptyMap()
            val body = if (ex.requestMethod == "POST") ex.requestBody.use { String(it.readNBytes(16_384)) } else ""
            // Behind a proxy the client address is the first X-Forwarded-For hop, trusted only when the operator says so.
            val ip = (if (System.getenv("TRUST_PROXY") == "1") ex.requestHeaders.getFirst("X-Forwarded-For")?.split(',')?.first()?.trim() else null) ?: ex.remoteAddress.address.hostAddress
            val r = handle(ex.requestMethod, ex.requestURI.path.trimEnd('/').ifEmpty { "/" }, query, body, ex.requestHeaders.getFirst("Authorization"), ip)
            val bytes = if (r.status == 204) ByteArray(0) else r.raw ?: r.body.toString(if (query["pretty"] == "1") 2 else 0).toByteArray()
            ex.responseHeaders.add("Content-Type", r.contentType)
            ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Authorization, Content-Type")
            ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
            r.headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            ex.sendResponseHeaders(r.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        } catch (e: Exception) { log("respond: ${e.message?.take(100)}") } finally { ex.close() }
    }
}

fun main() {
    val db = Db()
    val rpc = Rpc()
    val indexer = Indexer(db, rpc)
    val boards = Boards(indexer, Board(db, rpc))
    val views = Views(indexer, boards, db)
    val billing = Billing(db, { sig -> runCatching { rpc.tx(sig, Config.TREASURY) }.getOrNull() }, { w -> indexer.facts(w).hasGenesisToken })
    val hooks = Hooks(db, billing)
    indexer.onRebuilt = { fresh -> hooks.tick(indexer, fresh) }
    val api = Api(views, billing, indexer, hooks = hooks)
    api.web = Web(views, indexer)
    runCatching { indexer.rebuild() }   // serve what the database already holds at once
    if (Config.INDEX) {
        indexer.start()
        Thread({ while (true) { Thread.sleep(60_000); runCatching { boards.refresh(indexer.state) }.onFailure { log("boards: ${it.message?.take(80)}") }; Thread.sleep(14 * 60_000L) } }, "boards").apply { isDaemon = true }.start()
    }
    api.serve(Config.PORT)
    log("Undersigned API on ${Config.HOST}:${Config.PORT} (db ${Config.DB_PATH}, indexing ${Config.INDEX})")
}
