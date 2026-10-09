package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Help from the policy, the weekly digest (Pro or key, and its webhook), batch lookup, changelog, status, inferred schemas. */
class GapsTest {
    private fun sig(c: Char) = c.toString().repeat(87)
    private val member = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"

    private fun setup(): Triple<Db, Api, Hooks> {
        val db = Db(File.createTempFile("us-gaps", ".db").apply { deleteOnExit() }.path)
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App B").put("u", "2026-09-01").put("c", "Tools"))
            .put(JSONObject().put("p", "com.c.d").put("n", "App D").put("u", "2026-09-01").put("c", "Tools")).toString())
        db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sig('p'), sig('p'), member, System.currentTimeMillis() / 1000 - 60, ReviewMemo.encode(ReviewMemo.Pro(12)))
        db.exec("INSERT INTO changes (pkg, at, kind, old, new) VALUES ('com.a.b', ?, 'version', '41', '42')", System.currentTimeMillis() / 1000 - 3600)
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('us_k', 'w', 50, ?, 0)", System.currentTimeMillis() / 1000 + 3600)
        val rpc = Rpc(listOf("http://127.0.0.1:9")); val ix = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val ai = Ai(db, call = { sys, user, _ -> when {
            sys.startsWith("You answer questions from people reading") -> "Each attested device counts once."
            sys.startsWith("You write a short weekly digest") -> "App updates\nApp B: version 41 to 42." + if ("UPDATE App B" in user) "" else " (missing fact)"
            else -> "x" } })
        val views = Views(ix, Boards(ix, Board(db, rpc), Prices(db)), db, ai)
        val billing = Billing(db, { null }, { false })
        val hooks = Hooks(db, billing) { _, _, _ -> 200 }.also { h -> h.weeklyDigest = { views.digest() } }
        return Triple(db, Api(views, billing, ix, hooks = hooks).also { it.web = Web(views, ix) }, hooks)
    }

    @Test fun `help answers from the policy, the digest is Pro or key, and a digest hook gets it once a week`() {
        val (db, api, hooks) = setup()
        fun call(m: String, path: String, q: Map<String, String> = emptyMap(), body: String = "", auth: String? = null) = api.handle(m, path, q, body, auth, "7.7.7.7")
        val help = call("POST", "/v1/help", body = "{\"question\":\"How does scoring work?\",\"lang\":\"de\"}")
        assertEquals(200, help.status); assertEquals("Each attested device counts once.", help.body.getString("answer")); assertTrue(help.body.getString("label").startsWith("Suggested"))
        assertEquals(402, call("GET", "/v1/digest").status)
        val dg = call("GET", "/v1/digest", mapOf("wallet" to member)); assertEquals(200, dg.status)
        assertTrue("App B: version 41 to 42." in dg.body.getString("text") && "(missing fact)" !in dg.body.getString("text")); assertEquals(1, dg.body.getInt("updates"))
        assertEquals(200, call("GET", "/v1/digest", auth = "Bearer us_k").status)
        hooks.create("us_k", "http://localhost:9999/h", listOf("digest"), listOf("com.a.b"))
        hooks.digestTick(); hooks.digestTick()
        assertEquals(1, db.query("SELECT count(*) FROM hook_outbox") { it.getInt(1) }.first())          // once per week
    }

    @Test fun `batch lookup, changelog, status and inferred response schemas`() {
        val (_, api, _) = setup()
        fun get(path: String, q: Map<String, String> = emptyMap()) = api.handle("GET", path, q, "", null, "8.8.8.8")
        val batch = get("/v1/apps", mapOf("packages" to "com.a.b,com.c.d,com.nope.x")).body
        assertEquals(2, batch.getJSONArray("apps").length()); assertEquals("com.nope.x", batch.getJSONArray("unknown").getString(0))
        assertTrue(get("/v1/changelog").body.getJSONArray("entries").length() >= 3)
        assertTrue("Ledger (Solana)" in String(get("/status").raw!!)); assertTrue("Changelog" in String(get("/changelog").raw!!))
        val spec = get("/v1/openapi.json").body
        val schema = spec.getJSONObject("paths").getJSONObject("/v1/stats").getJSONObject("get").getJSONObject("responses").getJSONObject("200")
            .getJSONObject("content").getJSONObject("application/json").getJSONObject("schema")
        assertEquals("object", schema.getString("type")); assertTrue(schema.getJSONObject("properties").has("records"))
    }

    // Owner, 10 Oct 2026: one caller must not use up the day's shared AI cap. Fresh answers count per caller; cached ones never do.
    @Test fun `each caller has a daily AI allowance, cached answers are free, others are unaffected`() {
        val (_, api, _) = setup()
        fun help(q: String, ip: String = "8.8.8.8", auth: String? = null) = api.handle("POST", "/v1/help", emptyMap(), "{\"question\":\"$q\"}", auth, ip)
        repeat(Config.AI_PER_IP_DAY) { assertEquals(200, help("How does weighting work, case $it?").status) }
        val over = help("One question too many?")
        assertEquals(429, over.status); assertEquals("ai_allowance", over.body.getString("error")); assertTrue(over.headers.containsKey("Retry-After"))
        assertEquals(200, help("How does weighting work, case 3?").status)            // cached: free, still answered
        assertEquals(200, help("One question too many?", ip = "9.9.9.9").status)      // another caller
        assertEquals(200, help("And from a pass?", auth = "Bearer us_k").status)       // help is a free route: the key is not charged
    }

    @Test fun `a per-call key pays one extra call for each fresh AI answer`() {
        val db = Db(File.createTempFile("us-aicap", ".db").apply { deleteOnExit() }.path)
        val ai = Ai(db, call = { _, _, _ -> "an answer" })
        var debits = 0
        Ai.who.set(Ai.Caller("key:c", Int.MAX_VALUE) { debits++ < 1 })
        try {
            assertEquals("an answer", ai.help("First?", "policy", "en")); assertEquals(1, debits)
            assertEquals("an answer", ai.help("First?", "policy", "en")); assertEquals(1, debits)      // cached: no debit
            assertFailsWith<Ai.AllowanceUsed> { ai.help("Second?", "policy", "en") }                  // no calls left
        } finally { Ai.who.remove() }
    }
}
