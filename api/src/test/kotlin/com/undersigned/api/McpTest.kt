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
        assertTrue(names.none { "review" in it && it != "get_reviews" && it != "similar_reviews" && it != "review_tags" })   // no write tool
        assertTrue((0 until tools.length()).all { tools.getJSONObject(it).getJSONObject("annotations").getBoolean("readOnlyHint") })
    }

    @Test fun `tools answer with real data, and paid tools say how to get a pass`() {
        val app = call("get_app", JSONObject().put("package", "com.a.b"))
        assertFalse(app.getBoolean("isError")); assertEquals("com.a.b", app.getJSONObject("structuredContent").getString("package"))
        val reviews = call("get_reviews", JSONObject().put("package", "com.a.b").put("sort", "skr")).getJSONObject("structuredContent")
        assertEquals(1, reviews.getJSONArray("reviews").length())
        val unknown = call("get_app", JSONObject().put("package", "com.nope.x")); assertTrue(unknown.getBoolean("isError"))
        val paid = call("store_watch"); assertTrue(paid.getBoolean("isError")); assertTrue("needs an API pass" in paid.getJSONArray("content").getJSONObject(0).getString("text"))
    }

    @Test fun `protocol errors, batches and GET`() {
        assertEquals(-32601, rpc("nope").body.getJSONObject("error").getInt("code"))
        assertEquals(400, api.handle("POST", "/mcp", emptyMap(), "not json", null, "5.5.5.6").status)
        val batch = api.handle("POST", "/mcp", emptyMap(), "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}]", null, "5.5.5.7")
        assertEquals(2, JSONArray(String(batch.raw!!)).length())
        assertEquals(405, api.handle("GET", "/mcp", emptyMap(), "", null, "5.5.5.8").status)
    }
}
