package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reading aids with a fake model: answers cached (paid for once), structured answers validated (only the four reasons,
 * only real review ids), translations only for Pro wallets or keys, never of hidden text, and the daily cap holds.
 */
class AiTest {
    private fun tmpDb() = Db(File.createTempFile("us-ai", ".db").apply { deleteOnExit() }.path)
    private fun sig(c: Char) = c.toString().repeat(87)

    @Test fun `translations are cached, triage only names the four reasons, summaries keep only real ids`() {
        var calls = 0
        val answers = ArrayDeque(listOf("Funktioniert gut", "{\"reason\":\"spam\",\"quote\":\"x\",\"why\":\"y\"}", "{\"reason\":\"personal\",\"quote\":\"+36 1 234\",\"why\":\"a phone number\"}",
            "Sure: {\"points\":[{\"text\":\"Fast swaps\",\"review_ids\":[\"a\",\"zzz\"]},{\"text\":\"\",\"review_ids\":[\"b\"]}]}"))
        val ai = Ai(tmpDb(), call = { _, _, _ -> calls++; answers.removeFirst() })
        assertEquals("Funktioniert gut", ai.translate("r1", "Works well", "de")); assertEquals("Funktioniert gut", ai.translate("r1", "Works well", "de")); assertEquals(1, calls)
        assertNull(ai.triage("r2", "spam spam"))                                         // "spam" is not one of the four: rejected
        assertEquals("personal", ai.triage("r3", "call me on +36 1 234")!!.getString("reason"))
        val sm = ai.summary("com.a.b", listOf(Triple("a", 5, "fast"), Triple("b", 4, "ok"), Triple("c", 1, "crashes")))!!
        val pts = sm.getJSONArray("points"); assertEquals(1, pts.length())
        assertEquals(listOf("a"), (0 until pts.getJSONObject(0).getJSONArray("review_ids").length()).map { pts.getJSONObject(0).getJSONArray("review_ids").getString(it) })
        assertTrue(sm.getString("label").startsWith("Suggested summary"))
        assertNull(ai.summary("com.a.b", listOf(Triple("a", 5, "fast"))))                 // too few counted reviews: no call, no summary
    }

    @Test fun `the daily cap stops calls`() {
        var calls = 0
        val db = tmpDb(); val ai = Ai(db, call = { _, _, _ -> calls++; "x" })
        db.exec("INSERT INTO ai_usage (day, calls) VALUES (?, ?)", java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(), Config.AI_DAILY_CALLS)
        assertNull(ai.translate("r", "text", "fr")); assertEquals(0, calls)
    }

    @Test fun `translation is for Pro wallets or keys, never of hidden text, and the summary and docs helper answer`() {
        val db = tmpDb()
        val reviewer = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"; val member = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"
        fun rec(c: Char, who: String, m: ReviewMemo, t: Long = 1_700_000_000L) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sig(c), sig(c), who, t, ReviewMemo.encode(m))
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App").put("u", "2026-09-01").put("c", "Tools")).toString())
        rec('a', reviewer, ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.WORKS), 3, "Works well on my Seeker"))
        rec('p', member, ReviewMemo.Pro(12), System.currentTimeMillis() / 1000 - 60)
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", reviewer, System.currentTimeMillis())
        db.exec("INSERT INTO operator_hidden (id, reason, at) VALUES (?, 'abuse', 0)", sig('h'))
        rec('h', reviewer, ReviewMemo.Review("com.x.y", 1, listOf(ReviewMemo.Tag.CRASHES), 3, "hidden words"))
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES (?, 'w', 10, ?, 0)", Billing.hashKey("us_k"), System.currentTimeMillis() / 1000 + 3600)
        val rpc = Rpc(listOf("http://127.0.0.1:9")); val ix = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val ai = Ai(db, call = { sys, _, _ -> if (sys.startsWith("You answer developers")) "Use GET /v1/apps/{pkg}/reviews?sort=newest." else "Funktioniert gut auf meinem Seeker" })
        val api = Api(Views(ix, Boards(ix, Board(db, rpc), Prices(db)), db, ai), Billing(db, { null }, { false }), ix)
        fun get(path: String, q: Map<String, String> = emptyMap(), auth: String? = null) = api.handle("GET", path, q, "", auth, "6.6.6.6")
        val tr = "/v1/records/${sig('a')}/translation"
        assertEquals(402, get(tr, mapOf("lang" to "de")).status)                                       // no Pro, no key
        assertEquals(402, get(tr, mapOf("lang" to "de", "wallet" to reviewer)).status)                  // a wallet without Pro
        val ok = get(tr, mapOf("lang" to "de", "wallet" to member)); assertEquals(200, ok.status)        // a Pro wallet
        assertTrue(ok.body.getString("label").startsWith("Suggested translation"))
        assertEquals(200, get(tr, mapOf("lang" to "de"), "Bearer us_k").status)                          // a key
        assertEquals(400, get(tr, mapOf("lang" to "xx"), "Bearer us_k").status)
        assertEquals(404, get("/v1/records/${sig('h')}/translation", mapOf("lang" to "de"), "Bearer us_k").status)   // hidden text is never translated
        assertTrue(get("/v1/apps/com.a.b/summary").body.isNull("summary"))                              // one counted review: too few
        val ask = api.handle("POST", "/v1/ask", emptyMap(), "{\"question\":\"How do I list reviews?\"}", null, "6.6.6.7")
        assertEquals(200, ask.status); assertTrue("/reviews" in ask.body.getString("answer"))
        assertEquals(404, api.handle("GET", "/v1/admin/triage", emptyMap(), "", null, "6.6.6.8").status) // the operator's triage is loopback only
    }

    @Test fun `the local model goes first, Claude only when it fails, and echoes and labels are stripped`() {
        var claude = 0; var local = 0
        val ai = Ai(tmpDb(), call = { _, _, _ -> claude++; "Claude says hola" }, local = { _, _ -> local++; if (local == 1) "Swaps settle fast.\n\nJapanese translation:\n\nスワップは速い。" else null })
        assertEquals("スワップは速い。", ai.translate("r1", "Swaps settle fast.", "ja")); assertEquals(1, local); assertEquals(0, claude)
        assertEquals("Claude says hola", ai.translate("r2", "Other text", "es")); assertEquals(2, local); assertEquals(1, claude)
        assertEquals("Szia", ai.clean("\"Szia\"", "Hello")); assertEquals("Bonjour", ai.clean("**French translation:**\nBonjour", "Hello"))
    }

    @Test fun `a model correcting itself mid-answer leaves only the translation, and English is never served as Japanese`() {
        val ai = Ai(tmpDb(), call = { _, _, _ -> null })
        // The live answer of 10 Oct 2026, verbatim.
        val live = "Quick reviews test, end-to-end relay.\n\nWait, I need to correct that: the output must be the Japanese translation only.\n\nクイックレビューのテスト、エンドツーエンドのリレー。"
        assertEquals("クイックレビューのテスト、エンドツーエンドのリレー。", ai.clean(live, "Test of quick reviews, relay end to end.", "ja"))
        assertEquals("クイックレビューのテスト。", ai.clean("Quick reviews test, end to end.\nクイックレビューのテスト。", "x", "ja"))
        assertEquals("Jupiter スワップは速い。", ai.clean("Jupiter スワップは速い。", "x", "ja"))   // app names inside the script line stay
        assertEquals(false, ai.inLanguage("Quick reviews test, end-to-end relay.", "ja")); assertEquals(true, ai.inLanguage("Hola", "es"))
        assertEquals("Hola amigo", ai.clean("Hola amigo", "Hello friend", "es"))             // Latin targets untouched
    }

    @Test fun `store-review and reply translation are Pro or key only`() {
        val db = tmpDb()
        val member = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"
        db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sig('p'), sig('p'), member, System.currentTimeMillis() / 1000 - 60, ReviewMemo.encode(ReviewMemo.Pro(12)))
        val rpc = Rpc(listOf("http://127.0.0.1:9")); val ix = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val ai = Ai(db, call = { _, user, _ -> if ("Reply to translate" in user) "Köszönjük, javítjuk." else "Gyorsan működik" })
        val api = Api(Views(ix, Boards(ix, Board(db, rpc), Prices(db)), db, ai), Billing(db, { null }, { false }), ix)
        fun post(path: String, body: JSONObject) = api.handle("POST", path, emptyMap(), body.toString(), null, "6.6.6.9")
        assertEquals(402, post("/v1/translate", JSONObject().put("text", "Works fast").put("lang", "hu")).status)
        assertEquals("Gyorsan működik", post("/v1/translate", JSONObject().put("text", "Works fast").put("lang", "hu").put("wallet", member)).body.getString("text"))
        assertEquals(402, post("/v1/translate/reply", JSONObject().put("reply", "Thanks, we will fix it.").put("review_text", "Lassú").put("wallet", "nobody")).status)
        val r = post("/v1/translate/reply", JSONObject().put("reply", "Thanks, we will fix it.").put("review_text", "Lassú és összeomlik").put("wallet", member))
        assertEquals(200, r.status); assertEquals("Köszönjük, javítjuk.", r.body.getString("reply")); assertTrue("you sign" in r.body.getString("label"))
    }
}
