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
    val views = Views(indexer, Boards(indexer, Board(db, rpc), Prices(db)), db)
    val billing = Billing(db, { null }, { false })
    val api = Api(views, billing, indexer, blockhash = { Base58.encode(ByteArray(32) { 7 }) }, sanctioned = { it in sanctioned }).also { it.web = Web(views, indexer) }
    /** Splits the query off the path the way the JDK server does before calling handle(). */
    fun get(url: String) = api.handle("GET", url.substringBefore('?'), url.substringAfter('?', "").split('&').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }, "", null, "9.9.9.9")
    fun post(path: String, body: JSONObject) = api.handle("POST", path, emptyMap(), body.toString(), null, "9.9.9.9")
    fun get(url: String, lang: String) = api.handle("GET", url.substringBefore('?'), emptyMap(), "", null, "9.9.9.9", lang)
    fun postQ(path: String, query: Map<String, String>, body: JSONObject) = api.handle("POST", path, query, body.toString(), null, "9.9.9.9")
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
        hooks.dispatch(listOf(review, other), emptyList(), emptyList(), now); hooks.deliverDue(now)
        assertEquals(1, sent.size)
        val (_, body, signature) = sent.single()
        assertEquals(Hooks.sign(h.secret, body), signature)
        val ev = JSONObject(body).getJSONArray("events")
        assertEquals(1, ev.length()); assertEquals("com.a.b", ev.getJSONObject(0).getString("package"))
        // The pass ends: nothing more is sent.
        f.db.exec("UPDATE passes SET ends_at = ? WHERE key = 'us_a'", now - 1)
        hooks.dispatch(listOf(review), emptyList(), emptyList(), now); hooks.deliverDue(now)
        assertEquals(1, sent.size)
    }
}

class GapFixesTest {
    private val STRANGER = "FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF"

    @Test fun `the operator's hide list removes the text this site serves, never the record`() {
        val f = Fixture()
        f.indexer.hide(sigOf('r'), "private information"); f.indexer.rebuild()
        val r = f.get("/v1/apps/com.a.b/reviews").body.getJSONArray("reviews").getJSONObject(0)
        assertTrue(r.isNull("text")); assertTrue(r.getBoolean("hidden_by_operator")); assertEquals(4, r.getInt("rating"))   // the facts stay
        val html = String(f.get("/app/com.a.b").raw!!)
        assertTrue("Text not shown on this site" in html); assertFalse("alert(1)" in html)
        assertTrue(f.indexer.unhide(sigOf('r'))); f.indexer.rebuild()
        assertFalse(f.get("/v1/apps/com.a.b/reviews").body.getJSONArray("reviews").getJSONObject(0).isNull("text"))
    }

    @Test fun `reviews from uncounted wallets are folded away on the page`() {
        val f = Fixture()
        f.db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sigOf('u'), sigOf('u'), STRANGER, 1_700_000_500L,
            ReviewMemo.encode(ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.CRASHES), 3, "uncounted words")))
        f.db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 0, 0, ?)", STRANGER, System.currentTimeMillis()); f.indexer.rebuild()
        val html = String(f.get("/app/com.a.b").raw!!)
        val fold = html.indexOf("<details")
        assertTrue(fold > 0 && "1 review(s) from wallets without an attested device" in html)
        assertTrue(html.indexOf("uncounted words") > fold)                       // inside the fold
        assertTrue(html.indexOf("&amp; fine") in 1 until fold)                   // the counted review is above it
    }

    @Test fun `helpful votes, replies and flags are built for the reader's wallet, and refused when malformed`() {
        val f = Fixture()
        fun b(kind: String, target: String = sigOf('r'), extra: JSONObject.() -> Unit = {}) = JSONObject().put("account", STRANGER).put("kind", kind).put("target", target).apply(extra)
        val up = f.post("/v1/tx/record", b("up")); assertEquals(200, up.status, up.body.toString()); assertTrue(up.body.getString("memo").startsWith("SRL1|up|"))
        assertTrue("|rep|" in f.post("/v1/tx/record", b("reply") { put("text", "thanks") }).body.getString("memo"))
        assertTrue(f.post("/v1/tx/record", b("flag") { put("reason", "spam") }).body.getString("memo").endsWith("|spam"))
        assertEquals(400, f.post("/v1/tx/record", b("reply")).status)                                  // no text
        assertEquals(400, f.post("/v1/tx/record", b("flag") { put("reason", "because") }).status)
        assertEquals(400, f.post("/v1/tx/record", b("up", sigOf('z'))).status)                          // not a record this server holds
        assertEquals(400, f.post("/v1/tx/record", b("delete")).status)
        assertEquals(200, f.get("/act?kind=up&target=${sigOf('r')}").status); assertEquals(404, f.get("/act?kind=nope&target=${sigOf('r')}").status)
    }

    @Test fun `a phone wallet can fetch the review as a Solana Pay transaction request`() {
        val f = Fixture()
        assertEquals("Undersigned", f.get("/v1/pay/review").body.getString("label"))
        val r = f.postQ("/v1/pay/review", mapOf("package" to "com.a.b", "rating" to "5", "tags" to "works,fast"), JSONObject().put("account", STRANGER))
        assertEquals(200, r.status, r.body.toString()); assertTrue(r.body.has("transaction") && r.body.has("message")); assertFalse(r.body.has("memo"))
        assertEquals(400, f.postQ("/v1/pay/review", mapOf("package" to "com.a.b", "rating" to "9", "tags" to "works"), JSONObject().put("account", STRANGER)).status)
    }

    @Test fun `pages label tags in the reader's language, from the app's own translations`() {
        assertEquals("de", I18n.pick("de-DE,de;q=0.9,en;q=0.8")); assertEquals("pt-rBR", I18n.pick("pt-PT")); assertEquals("zh-rTW", I18n.pick("zh-TW")); assertEquals("in", I18n.pick("id-ID"))
        assertEquals("en", I18n.pick("xx-YY")); assertEquals("en", I18n.pick(null))
        assertEquals("Works", I18n.tag("en", "works"))
        val de = I18n.tag("de", "works"); assertTrue(de.isNotEmpty() && de != "Works", de)
        assertEquals(I18n.LOCALES.size, I18n.LOCALES.count { I18n.tag(it, "crashes").isNotEmpty() && I18n.use(it, "w1") != "w1" })   // every locale's file is bundled
        val html = String(Fixture().get("/app/com.a.b", "de-DE").raw!!)
        assertTrue("<html lang=de>" in html); assertTrue(de in html)
    }

    @Test fun `icon, publisher checker, feed and the new alert types`() {
        val f = Fixture()
        assertEquals("image/svg+xml; charset=utf-8", f.get("/icon.svg").contentType)
        assertTrue("Store version 42" in String(f.get("/publishers?package=com.a.b").raw!!))
        val feed = f.get("/app/com.a.b/feed.xml"); assertTrue(feed.contentType.startsWith("application/atom+xml")); assertTrue("<entry>" in String(feed.raw!!))
        assertTrue("/report" in String(f.get("/").raw!!)); assertEquals(200, f.get("/report").status); assertTrue("<urlset" in String(f.get("/sitemap.xml").raw!!))
        val now = System.currentTimeMillis() / 1000
        f.db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('us_h', 'w', 10, ?, 0)", now + 3600)
        val sent = ArrayList<String>(); val hooks = Hooks(f.db, f.billing) { _, body, _ -> sent += body; 200 }
        hooks.create("us_h", "http://localhost:9999/h", listOf("flag", "claim"), listOf("com.a.b"))
        val review = f.indexer.state.entries.first()
        hooks.dispatch(listOf(LedgerEntry(sigOf('f'), STRANGER, now, ReviewMemo.Flag(review.signature, ReviewMemo.FlagReason.SPAM)), LedgerEntry(sigOf('c'), STRANGER, now, ReviewMemo.Claim("com.a.b"))), emptyList(), emptyList(), now) { id -> f.indexer.state.entries.firstOrNull { it.signature == id } }
        hooks.deliverDue(now)
        assertEquals(setOf("flag", "claim"), JSONObject(sent.single()).getJSONArray("events").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("type") }.toSet() })
    }
}
