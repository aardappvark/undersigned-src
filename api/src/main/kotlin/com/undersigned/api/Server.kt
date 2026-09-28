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
class Api(private val views: Views, private val billing: Billing, private val indexer: Indexer, private val limiter: RateLimiter = RateLimiter()) {

    /** A route's gate: FREE for anyone, LIMITED = free with fewer rows, PAID = pass only. */
    enum class Tier { FREE, LIMITED, PAID }
    data class Caller(val paid: Boolean, val key: String?)
    class Response(val status: Int, val body: JSONObject, val headers: Map<String, String> = emptyMap())
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
        route("GET", "/v1/payments", Tier.LIMITED, "Payments board: confirmed treasuries by USD received") { r -> ok(views.paymentsBoard(rows(r, Config.FREE_TOP, 500))) }
    }

    fun handle(method: String, path: String, query: Map<String, String>, body: String, auth: String?, ip: String): Response {
        if (method == "OPTIONS") return Response(204, JSONObject())
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

    private fun paymentRequired(why: String) = Response(402, JSONObject().put("error", "payment_required").put("message", why).put("pricing", pricing()))

    private fun redeem(r: Req): Response {
        val b = runCatching { JSONObject(r.body) }.getOrNull() ?: return Response(400, JSONObject().put("error", "bad_json").put("message", "send {\"signature\": \"<payment tx>\", \"message_signature\": \"<wallet signature>\"}"))
        val sig = b.optString("signature"); val msig = b.optString("message_signature")
        if (sig.isEmpty() || msig.isEmpty()) return Response(400, JSONObject().put("error", "missing_fields").put("message", "signature and message_signature are required; sign: \"${billing.message(sig.ifEmpty { "<signature>" })}\""))
        val p = billing.redeem(sig, msig)
        return ok(JSONObject().put("key", p.key).put("wallet", p.wallet).put("requests_left", p.requestsLeft).put("ends_at", p.endsAt)
            .put("use", "Authorization: Bearer ${p.key}"))
    }

    fun pricing(): JSONObject {
        fun url(amount: String, token: String?) = "solana:${Config.TREASURY}?amount=$amount" + (token?.let { "&spl-token=$it" } ?: "") +
            "&reference=${Config.API_REFERENCE}&label=${enc("Undersigned")}&message=${enc("Undersigned API pass")}&memo=${Config.PAYMENT_MEMO}"
        return JSONObject()
            .put("pass", JSONObject().put("requests", Config.PASS_REQUESTS).put("days", Config.PASS_DAYS).put("per_minute", Config.PASS_PER_MINUTE)
                .put("price", JSONObject().put("sol", Config.PASS_SOL).put("skr", Config.PASS_SKR).put("skr_with_seeker_genesis_token", Config.PASS_SKR_SGT)))
            .put("free", JSONObject().put("per_minute", Config.FREE_PER_MINUTE).put("per_day", Config.FREE_PER_DAY).put("rows", Config.FREE_ROWS))
            .put("pay_to", Config.TREASURY).put("reference", Config.API_REFERENCE).put("memo", Config.PAYMENT_MEMO)
            .put("solana_pay", JSONObject().put("sol", url(Config.PASS_SOL.toString(), null)).put("skr", url(Config.PASS_SKR.toBigDecimal().stripTrailingZeros().toPlainString(), Config.SKR_MINT))
                .put("skr_with_seeker_genesis_token", url(Config.PASS_SKR_SGT.toBigDecimal().stripTrailingZeros().toPlainString(), Config.SKR_MINT)))
            .put("redeem", JSONObject().put("post", "/v1/keys").put("body", JSONObject().put("signature", "<payment transaction signature>").put("message_signature", "<the paying wallet's signature of the message>"))
                .put("message", "Undersigned API key for <payment transaction signature>"))
            .put("note", "Paying again adds ${Config.PASS_REQUESTS} requests and ${Config.PASS_DAYS} days. The payment is read from balances on-chain; only the paying wallet can redeem it.")
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
            val bytes = if (r.status == 204) ByteArray(0) else r.body.toString(if (query["pretty"] == "1") 2 else 0).toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Authorization, Content-Type")
            ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
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
    val api = Api(views, billing, indexer)
    runCatching { indexer.rebuild() }   // serve what the database already holds at once
    if (Config.INDEX) {
        indexer.start()
        Thread({ while (true) { Thread.sleep(60_000); runCatching { boards.refresh(indexer.state) }.onFailure { log("boards: ${it.message?.take(80)}") }; Thread.sleep(14 * 60_000L) } }, "boards").apply { isDaemon = true }.start()
    }
    api.serve(Config.PORT)
    log("Undersigned API on ${Config.HOST}:${Config.PORT} (db ${Config.DB_PATH}, indexing ${Config.INDEX})")
}
