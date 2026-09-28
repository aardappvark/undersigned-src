package com.undersigned.api

import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val REVIEWER = "8oLtrvMnKLxFfG4PSa5ANxW1Ncvy1h2BCYfZ9mRBHT8F"
private fun sigOf(c: Char) = c.toString().repeat(87)

/** A server over a small real database: one attested review whose text tries to inject a script, a catalogue, store details. */
private class Fixture(sanctioned: Set<String> = emptySet()) {
    val db = Db(File.createTempFile("us-web", ".db").apply { deleteOnExit() }.path)
    init {
        db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sigOf('r'), sigOf('r'), REVIEWER, 1_700_000_000L,
            ReviewMemo.encode(ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.WORKS), 3, "<script>alert(1)</script> & fine")))
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", REVIEWER, System.currentTimeMillis())
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App <B>").put("u", "2026-09-01").put("c", "Tools")).toString())
        db.exec("INSERT INTO details (pkg, version, cert, publisher, website, email, notes, at) VALUES ('com.a.b', 42, 'ab', 'Pub', NULL, NULL, NULL, 0)")
    }
    val rpc = Rpc(listOf("http://127.0.0.1:9"))
    val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
    val views = Views(indexer, Boards(indexer, Board(db, rpc)), db)
    val billing = Billing(db, { null }, { false })
    val api = Api(views, billing, indexer, blockhash = { Base58.encode(ByteArray(32) { 7 }) }, sanctioned = { it in sanctioned }).also { it.web = Web(views, indexer) }
    /** Splits the query off the path the way the JDK server does before calling handle(). */
    fun get(url: String) = api.handle("GET", url.substringBefore('?'), url.substringAfter('?', "").split('&').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }, "", null, "9.9.9.9")
    fun post(path: String, body: JSONObject) = api.handle("POST", path, emptyMap(), body.toString(), null, "9.9.9.9")
}

class WebTest {
    @Test fun `pages escape what reviewers and the store wrote`() {
        val f = Fixture()
        val html = String(f.get("/app/com.a.b").raw!!)
        assertFalse("<script>alert(1)</script>" in html)
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt; &amp; fine" in html)
        assertTrue("App &lt;B&gt;" in html)
        assertTrue(f.get("/").contentType.startsWith("text/html"))
        assertEquals(404, f.get("/app/com.nothing").status)
        assertEquals(200, f.get("/watch").status); assertTrue("Day 0 of 8" in String(f.get("/watch").raw!!))
        assertEquals(200, f.get("/review?package=com.a.b").status)
    }

    @Test fun `badge says what the ledger says`() {
        val f = Fixture()
        val svg = String(f.get("/v1/badge/com.a.b.svg").raw!!)
        assertTrue("4.0 · 1 signed" in svg, svg)
        assertEquals("image/svg+xml; charset=utf-8", f.get("/v1/badge/com.a.b.svg").contentType)
        assertTrue("unknown app" in String(f.get("/v1/badge/com.zzz.svg").raw!!))
    }
}

class ReviewTxTest {
    private fun body(rating: Int = 5, tags: List<String> = listOf("works"), text: String? = null, pkg: String = "com.a.b", account: String = REVIEWER) =
        JSONObject().put("account", account).put("package", pkg).put("rating", rating).put("tags", JSONArray(tags)).apply { text?.let { put("text", it) } }

    @Test fun `builds the app's carrier transaction, unsigned, for the reviewer's wallet`() {
        val r = Fixture().post("/v1/tx/review", body(text = "good"))
        assertEquals(200, r.status, r.body.toString())
        val tx = Base64.getDecoder().decode(r.body.getString("transaction"))
        assertEquals(1, tx[0].toInt())                                          // one signature slot
        assertTrue(tx.copyOfRange(1, 65).all { it == 0.toByte() })             // left for the wallet
        val text = String(tx, Charsets.ISO_8859_1)
        assertTrue(r.body.getString("memo") in text); assertTrue("|42|" in r.body.getString("memo"))   // the store's version
        assertTrue(String(Base58.decode(Config.REGISTRY), Charsets.ISO_8859_1) in text)                 // the registry reference
        assertTrue(String(Base58.decode(REVIEWER), Charsets.ISO_8859_1) in text)                         // the fee payer
    }

    @Test fun `refuses what the app would refuse`() {
        val f = Fixture(sanctioned = setOf(REVIEWER))
        assertEquals(400, Fixture().post("/v1/tx/review", body(rating = 6)).status)
        assertEquals(400, Fixture().post("/v1/tx/review", body(tags = emptyList())).status)
        assertEquals(400, Fixture().post("/v1/tx/review", body(tags = listOf("works", "fast", "ads", "slow"))).status)
        assertEquals(400, Fixture().post("/v1/tx/review", body(text = "x".repeat(141))).status)
        assertEquals(400, Fixture().post("/v1/tx/review", body(pkg = "com.not.listed")).status)
        assertEquals(400, Fixture().post("/v1/tx/review", body(account = "nope")).status)
        assertEquals(403, f.post("/v1/tx/review", body()).status)
    }
}

class HooksTest {
    @Test fun `urls into private networks are refused`() {
        assertFailsWith<Hooks.Invalid> { Hooks.checkUrl("http://example.com/x", loopbackServer = false) }
        assertFailsWith<Hooks.Invalid> { Hooks.checkUrl("https://127.0.0.1/x", loopbackServer = false) }
        assertFailsWith<Hooks.Invalid> { Hooks.checkUrl("https://10.1.2.3/x", loopbackServer = false) }
        assertFailsWith<Hooks.Invalid> { Hooks.checkUrl("https://192.168.1.5/x", loopbackServer = false) }
        assertFailsWith<Hooks.Invalid> { Hooks.checkUrl("https://169.254.169.254/latest", loopbackServer = false) }
        Hooks.checkUrl("http://localhost:9999/hook", loopbackServer = true)              // testing on a loopback-only server
        assertFailsWith<Hooks.Invalid> { Hooks.checkUrl("http://10.1.2.3/x", loopbackServer = true) }
    }

    @Test fun `delivers signed events for watched apps and stops when the pass ends`() {
        val f = Fixture()
        val now = System.currentTimeMillis() / 1000
        f.db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('us_a', 'w', 10, ?, 0)", now + 3600)
        val sent = ArrayList<Triple<String, String, String>>()
        val hooks = Hooks(f.db, f.billing) { url, body, sig -> sent += Triple(url, body, sig); 200 }
        val h = hooks.create("us_a", "http://localhost:9999/hook", listOf("review"), listOf("com.a.b"))
        val review = LedgerEntry(sigOf('n'), REVIEWER, now, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.FAST), 42, null))
        val other = LedgerEntry(sigOf('o'), REVIEWER, now, ReviewMemo.Review("com.x.y", 5, listOf(ReviewMemo.Tag.FAST), 42, null))
        hooks.dispatch(listOf(review, other), emptyList(), emptyList())
        assertEquals(1, sent.size)
        val (_, body, signature) = sent.single()
        assertEquals(Hooks.sign(h.secret, body), signature)
        val ev = JSONObject(body).getJSONArray("events")
        assertEquals(1, ev.length()); assertEquals("com.a.b", ev.getJSONObject(0).getString("package"))
        // The pass ends: nothing more is sent.
        f.db.exec("UPDATE passes SET ends_at = ? WHERE key = 'us_a'", now - 1)
        hooks.dispatch(listOf(review), emptyList(), emptyList())
        assertEquals(1, sent.size)
    }
}
