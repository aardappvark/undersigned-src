package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MCP at POST /mcp: handshake, read-only tool list, real answers, paid tools gated, notifications, errors, GET refused. */
class McpTest {
    private val api: Api = run {
        val db = Db(File.createTempFile("us-mcp", ".db").apply { deleteOnExit() }.path)
        db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", "r".repeat(87), "r".repeat(87), "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM", 1_700_000_000L,
            ReviewMemo.encode(ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.WORKS), 3, "Works well on my Seeker every day")))
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM", System.currentTimeMillis())
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App B").put("u", "2026-09-01").put("c", "Tools")).toString())
        val rpc = Rpc(listOf("http://127.0.0.1:9")); val ix = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        Api(Views(ix, Boards(ix, Board(db, rpc), Prices(db)), db), Billing(db, { null }, { false }), ix)
    }
    private fun rpc(method: String, params: JSONObject? = null, id: Any? = 1) = api.handle("POST", "/mcp", emptyMap(),
        JSONObject().put("jsonrpc", "2.0").put("method", method).apply { if (id != null) put("id", id); if (params != null) put("params", params) }.toString(), null, "5.5.5.5")
    private fun call(tool: String, args: JSONObject = JSONObject()) = rpc("tools/call", JSONObject().put("name", tool).put("arguments", args)).body.getJSONObject("result")

    @Test fun `handshake answers a supported protocol version and says it is read-only`() {
        val r = rpc("initialize", JSONObject().put("protocolVersion", "2025-06-18").put("capabilities", JSONObject()).put("clientInfo", JSONObject().put("name", "t").put("version", "1"))).body.getJSONObject("result")
        assertEquals("2025-06-18", r.getString("protocolVersion")); assertTrue("read-only" in r.getString("instructions"))
        assertEquals(Mcp.VERSIONS.first(), rpc("initialize", JSONObject().put("protocolVersion", "1999-01-01")).body.getJSONObject("result").getString("protocolVersion"))
        assertEquals(202, rpc("notifications/initialized", id = null).status)
    }

    @Test fun `tools are listed, all read-only, none writes a review`() {
        val tools = rpc("tools/list").body.getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertTrue("get_reviews" in names && "search_apps" in names && "get_app" in names)
        val writes = listOf("write", "submit", "sign", "post", "create", "flag", "vote", "publish", "send", "draft", "reply", "hide")
        assertTrue(names.none { n -> writes.any { n.startsWith(it) || n.contains("_$it") } }, "a tool name suggests writing: $names")   // no write tool
        assertTrue((0 until tools.length()).all { tools.getJSONObject(it).getJSONObject("annotations").getBoolean("readOnlyHint") })
    }

    @Test fun `tools answer with real data, and paid tools say how to get a pass`() {
        val app = call("get_app", JSONObject().put("package", "com.a.b"))
        assertFalse(app.getBoolean("isError")); assertEquals("com.a.b", app.getJSONObject("structuredContent").getString("package"))
        val reviews = call("get_reviews", JSONObject().put("package", "com.a.b").put("sort", "skr")).getJSONObject("structuredContent")
        assertEquals(1, reviews.getJSONArray("reviews").length())
        val unknown = call("get_app", JSONObject().put("package", "com.nope.x")); assertTrue(unknown.getBoolean("isError"))
        val paid = call("store_watch"); assertTrue(paid.getBoolean("isError")); assertTrue("needs a key" in paid.getJSONArray("content").getJSONObject(0).getString("text") && "per-call" in paid.getJSONArray("content").getJSONObject(0).getString("text"))
    }

    @Test fun `protocol errors, batches and GET`() {
        assertEquals(-32601, rpc("nope").body.getJSONObject("error").getInt("code"))
        assertEquals(400, api.handle("POST", "/mcp", emptyMap(), "not json", null, "5.5.5.6").status)
        // batches are refused: one POST is metered once, so it must carry one call
        val batch = api.handle("POST", "/mcp", emptyMap(), "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}]", null, "5.5.5.7")
        assertEquals(400, batch.status); assertEquals(-32600, batch.body.getJSONObject("error").getInt("code"))
        assertEquals(405, api.handle("GET", "/mcp", emptyMap(), "", null, "5.5.5.8").status)
    }

    @Test fun `resources, prompts and the record tools work`() {
        val res = rpc("resources/list").body.getJSONObject("result").getJSONArray("resources")
        assertEquals(setOf("undersigned://policy", "undersigned://pricing", "undersigned://tags"), (0 until res.length()).map { res.getJSONObject(it).getString("uri") }.toSet())
        val policy = rpc("resources/read", JSONObject().put("uri", "undersigned://policy")).body.getJSONObject("result").getJSONArray("contents").getJSONObject(0).getString("text")
        assertTrue("never weighed" in policy && "\n" in policy)
        assertEquals("com.a.b", JSONObject(rpc("resources/read", JSONObject().put("uri", "undersigned://app/com.a.b")).body.getJSONObject("result").getJSONArray("contents").getJSONObject(0).getString("text")).getString("package"))
        assertEquals(-32002, rpc("resources/read", JSONObject().put("uri", "undersigned://nope")).body.getJSONObject("error").getInt("code"))
        val prompts = rpc("prompts/list").body.getJSONObject("result").getJSONArray("prompts"); assertEquals(2, prompts.length())
        val msg = rpc("prompts/get", JSONObject().put("name", "audit_app_reputation").put("arguments", JSONObject().put("package", "com.a.b"))).body.getJSONObject("result")
            .getJSONArray("messages").getJSONObject(0).getJSONObject("content").getString("text")
        assertTrue("com.a.b" in msg && "not verdicts" in msg)
        assertEquals(-32602, rpc("prompts/get", JSONObject().put("name", "compare_apps").put("arguments", JSONObject().put("package_a", "x"))).body.getJSONObject("error").getInt("code"))
        val recs = call("list_records", JSONObject().put("type", "rev")).getJSONObject("structuredContent").getJSONArray("records"); assertEquals(1, recs.length())
        assertFalse(call("get_record", JSONObject().put("id", recs.getJSONObject(0).getString("id"))).getBoolean("isError"))
        assertTrue(call("get_record", JSONObject().put("id", "nope")).getBoolean("isError"))
        assertFalse(call("payments_board").getBoolean("isError")); assertFalse(call("get_pricing").getBoolean("isError"))
    }
}
