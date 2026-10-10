package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The data plan and the publisher plan (owner, 10 Oct 2026): who can redeem, at what price, and what each key opens. */
class PlansTest {
    private class W { private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val address: String = Base58.encode(kp.public.encoded.takeLast(32).toByteArray())
        fun sign(b: ByteArray): String = Base58.encode(Signature.getInstance("Ed25519").run { initSign(kp.private); update(b); sign() }) }
    private fun sig(c: Char) = c.toString().repeat(87)
    private fun db() = Db(File.createTempFile("us-plans", ".db").apply { deleteOnExit() }.path)
    private fun tx(s: String, from: String, lamports: Long = 0, skr: Long = 0) =
        TxInfo(s, from, 1_000, true, listOf("UNS"), 5000, false, listOf(from, Config.TREASURY, Config.API_REFERENCE), Config.TREASURY, lamports, if (skr > 0) mapOf(Config.SKR_MINT to skr) else emptyMap())

    @Test fun `ten passes' worth redeems as a data plan, its own key`() {
        val payer = W(); val txs = mutableMapOf<String, TxInfo>()
        val b = Billing(db(), { txs[it] }, { false })
        fun redeem(s: String) = b.redeem(s, payer.sign(b.message(s).toByteArray()), now = 1_000_000)
        txs[sig('a')] = tx(sig('a'), payer.address, lamports = Config.dataFloorLamports)
        val d = redeem(sig('a')); assertEquals("data", b.kind(d.key)); assertEquals(Config.DATA_REQUESTS, d.requestsLeft)
        assertEquals(1_000_000 + Config.DATA_DAYS * 86_400, d.endsAt)
        txs[sig('b')] = tx(sig('b'), payer.address, lamports = Config.dataFloorLamports - 1)            // just under: an ordinary pass
        val p = redeem(sig('b')); assertEquals("pass", b.kind(p.key) ?: "pass"); assertTrue(p.key != d.key)
        txs[sig('c')] = tx(sig('c'), payer.address, skr = Config.dataFloorSkrRaw)                       // SKR, and a renewal stacks on the same key
        val d2 = redeem(sig('c')); assertTrue(d2.key != d.key); assertEquals("data", b.kind(d2.key)); assertEquals(2 * Config.DATA_REQUESTS, d2.requestsLeft); assertEquals(d.endsAt + Config.DATA_DAYS * 86_400, d2.endsAt)
    }

    @Test fun `a publisher plan is redeemed only by the app's proven publisher, by the month, stacking`() {
        val pub = W(); val stranger = W(); val txs = mutableMapOf<String, TxInfo>()
        val b = Billing(db(), { txs[it] }, { false })
        val isPub = { w: String, pkg: String -> w == pub.address && pkg == "com.a.b" }
        fun plan(s: String, by: W, pkg: String = "com.a.b") = b.redeemPlan(s, by.sign(b.message(s).toByteArray()), pkg, isPub, now = 1_000_000)
        txs[sig('s')] = tx(sig('s'), stranger.address, lamports = Config.planFloorLamports)
        assertEquals(403, assertFailsWith<Billing.Refused> { plan(sig('s'), stranger) }.status)          // not its publisher
        txs[sig('u')] = tx(sig('u'), pub.address, lamports = Config.planFloorLamports - 1)
        assertEquals(402, assertFailsWith<Billing.Refused> { plan(sig('u'), pub) }.status)               // under one month
        txs[sig('x')] = tx(sig('x'), pub.address, lamports = Config.planFloorLamports)
        assertEquals(403, assertFailsWith<Billing.Refused> { plan(sig('x'), pub, "com.other") }.status)  // someone else's app
        txs[sig('m')] = tx(sig('m'), pub.address, lamports = 3 * Config.planFloorLamports)               // three months in one payment
        val p = plan(sig('m'), pub); assertEquals(3, p.months); assertEquals(1_000_000 + 3 * Config.PLAN_DAYS * 86_400, p.endsAt)
        assertEquals("publisher", b.kind(p.key)); assertTrue(b.planActive("com.a.b", 1_000_001)); assertTrue(!b.planActive("com.c.d", 1_000_001))
        val again = plan(sig('m'), pub); assertTrue(again.key != p.key); assertEquals(p.endsAt, again.endsAt); assertEquals(null, b.pass(p.key))   // the same proof: a new key, nothing added
        txs[sig('r')] = tx(sig('r'), pub.address, skr = Config.planFloorSkrRaw)                         // a renewal starts when the term ends
        val r = plan(sig('r'), pub); assertEquals(p.endsAt + Config.PLAN_DAYS * 86_400, r.endsAt); assertEquals("publisher", b.kind(r.key))
        txs[sig('y')] = tx(sig('y'), pub.address, lamports = 40 * Config.planFloorLamports)              // at most twelve months a payment
        assertEquals(Config.PLAN_MAX_MONTHS, plan(sig('y'), pub).months)
        txs[sig('g')] = tx(sig('g'), pub.address, skr = 12 * Config.planFloorSkrSgtRaw)               // a Genesis holder: 12 months at the Genesis price is 12 months
        val b2 = Billing(db(), { txs[it] }, { true })
        assertEquals(12, b2.redeemPlan(sig('g'), pub.sign(b2.message(sig('g')).toByteArray()), "com.a.b", isPub, now = 1_000_000).months)
        txs[sig('z')] = tx(sig('z'), pub.address, lamports = Config.passFloorLamports)                   // redeemed as a pass first: not a plan too
        b.redeem(sig('z'), pub.sign(b.message(sig('z')).toByteArray()), now = 1_000_000)
        assertEquals(409, assertFailsWith<Billing.Refused> { plan(sig('z'), pub) }.status)
    }

    @Test fun `the report opens only for the app's publisher with a live plan, and exports only for a data key`() {
        val pub = W(); val db = db()
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App B").put("u", "2026-09-01").put("c", "Tools"))
            .put(JSONObject().put("p", "com.c.d").put("n", "App D").put("u", "2026-09-01").put("c", "Tools")).toString())
        val now = System.currentTimeMillis() / 1000
        // Proven by the store's own build: the build names the wallet and carries the certificate the store lists.
        db.exec("INSERT INTO details (pkg, version, cert, publisher, website, email, notes, at) VALUES ('com.a.b', 42, 'c3', 'B Ltd', NULL, NULL, NULL, ?)", now)
        db.exec("INSERT INTO apk_proofs (pkg, version, cert, declared, at) VALUES ('com.a.b', 42, 'c3', ?, ?)", pub.address, now)
        db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sig('c'), sig('c'), pub.address, now - 600, ReviewMemo.encode(ReviewMemo.Claim("com.a.b")))
        for ((i, d) in listOf("2026-10-01", "2026-10-08").withIndex()) for (p in listOf("com.a.b", "com.c.d"))
            db.exec("INSERT INTO ratings (pkg, day, s1, s2, s3, s4, s5) VALUES (?, ?, 1, 1, 2, 5, ?)", p, d, if (p == "com.a.b") 20 + i * 14 else 5)
        db.exec("INSERT INTO changes (pkg, at, kind, old, new) VALUES ('com.a.b', ?, 'certificate', 'c2', 'c3')", now - 3600)
        val txs = mutableMapOf<String, TxInfo>()
        val rpc = Rpc(listOf("http://127.0.0.1:9")); val ix = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        assertEquals(setOf(pub.address), ix.state.verified["com.a.b"])
        val views = Views(ix, Boards(ix, Board(db, rpc), Prices(db)), db, null)
        val billing = Billing(db, { txs[it] }, { false })
        val api = Api(views, billing, ix)
        fun call(m: String, path: String, key: String? = null, body: String = "") = api.handle(m, path, emptyMap(), body, key?.let { "Bearer $it" }, "7.7.7.7")

        assertEquals(402, call("GET", "/v1/publisher/com.a.b/report").status)                              // no key
        txs[sig('p')] = tx(sig('p'), pub.address, lamports = Config.passFloorLamports)
        val passKey = billing.redeem(sig('p'), pub.sign(billing.message(sig('p')).toByteArray())).key
        assertEquals(403, call("GET", "/v1/publisher/com.a.b/report", passKey).status)                     // a pass is not a plan
        assertEquals(403, call("GET", "/v1/export/ratings.csv", passKey).status)                           // nor a data plan
        assertEquals(false, call("GET", "/v1/publisher/com.a.b/plan").body.getBoolean("active"))

        txs[sig('q')] = tx(sig('q'), pub.address, lamports = Config.planFloorLamports)
        val redeemed = call("POST", "/v1/publisher/plan", body = JSONObject().put("signature", sig('q')).put("package", "com.a.b")
            .put("message_signature", pub.sign(billing.message(sig('q')).toByteArray())).toString())
        assertEquals(200, redeemed.status); val key = redeemed.body.getString("key")
        assertEquals(true, call("GET", "/v1/publisher/com.a.b/plan").body.getBoolean("active"))
        val rep = call("GET", "/v1/publisher/com.a.b/report", key)
        assertEquals(200, rep.status)
        assertEquals(14, rep.body.getJSONObject("store_rating_trend").getInt("ratings_added"))   // 20 -> 34 five-star ratings
        assertEquals("Tools", rep.body.getJSONObject("benchmark").getString("category")); assertEquals(2, rep.body.getJSONObject("benchmark").getInt("apps_rated"))
        assertEquals("certificate", rep.body.getJSONArray("changes").getJSONObject(0).getString("kind"))
        assertEquals(403, call("GET", "/v1/publisher/com.c.d/report", key).status)                         // not its app

        txs[sig('d')] = tx(sig('d'), pub.address, lamports = Config.dataFloorLamports)
        val dataKey = billing.redeem(sig('d'), pub.sign(billing.message(sig('d')).toByteArray())).key
        val csv = call("GET", "/v1/export/ratings.csv", dataKey)
        assertEquals(200, csv.status); assertEquals("text/csv; charset=utf-8", csv.contentType)
        val lines = String(csv.raw!!).trim().lines()
        assertEquals("package,day,stars_1,stars_2,stars_3,stars_4,stars_5", lines[0]); assertEquals(5, lines.size)
        assertEquals(404, call("GET", "/v1/export/nothing", dataKey).status)
        val pricing = call("GET", "/v1/pricing").body
        assertEquals(Config.DATA_SOL, pricing.getJSONObject("data_plan").getJSONObject("price").getDouble("sol"))
        assertEquals(Config.PLAN_SOL, pricing.getJSONObject("publisher_plan").getDouble("sol"))
    }

    @Test fun `keys written in clear are hashed on start, and webhooks follow a rotated key`() {
        val d = db(); val payer = W(); val txs = mutableMapOf<String, TxInfo>()
        d.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created, kind) VALUES ('us_old', 'w', 5, 9999999999, 0, 'pass')")
        val b = Billing(d, { txs[it] }, { false })
        assertEquals(0, d.query("SELECT count(*) FROM passes WHERE key LIKE 'us_%'") { it.getInt(1) }.first())   // nothing in clear
        assertEquals(5L, b.pass("us_old")!!.requestsLeft)                                                          // the old key still opens its pass
        val hooks = Hooks(d, b) { _, _, _ -> 200 }
        txs[sig('a')] = tx(sig('a'), payer.address, lamports = Config.passFloorLamports)
        val k1 = b.redeem(sig('a'), payer.sign(b.message(sig('a')).toByteArray()), now = 1_000_000).key
        hooks.create(k1, "http://localhost:9/h", listOf("review"), listOf("com.a.b"))
        val k2 = b.redeem(sig('a'), payer.sign(b.message(sig('a')).toByteArray()), now = 1_000_001).key   // re-redeem rotates
        assertEquals(1, hooks.list(k2).size); assertEquals(0, hooks.list(k1).size)
    }
}
