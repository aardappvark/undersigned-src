package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun tempDb() = Db(File.createTempFile("us-api", ".db").apply { deleteOnExit() }.path)

/** A real Ed25519 wallet: base58 address and a signer over raw bytes. */
private class Wallet {
    private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val address: String = Base58.encode(kp.public.encoded.takeLast(32).toByteArray())
    fun sign(bytes: ByteArray): String = Base58.encode(Signature.getInstance("Ed25519").run { initSign(kp.private); update(bytes); sign() })
}

private fun sig(c: Char) = c.toString().repeat(87)

class ApiConfigParityTest {
    /** The app's own source is the authority: a Pro licence or a paid listing must mean the same thing to both readers. */
    @Test fun `money floors and addresses match the app`() {
        val src = File("../app/src/main/kotlin/com/undersigned/app/AppConfig.kt").readText()
        fun num(name: String) = Regex("const val $name = ([0-9.]+)").find(src)!!.groupValues[1].toDouble()
        fun str(name: String) = Regex("const val $name = \"([^\"]+)\"").find(src)!!.groupValues[1]
        assertEquals(num("PRO_SOL"), AppFloors.PRO_SOL); assertEquals(num("PRO_SKR"), AppFloors.PRO_SKR)
        val legacy = Regex("LEGACY_PRO_SKR: List<Double> = listOf\\(([0-9., ]*)\\)").find(src)!!.groupValues[1].split(',').map { it.trim().toDouble() }
        assertEquals(legacy, AppFloors.LEGACY_PRO_SKR)
        assertEquals(276_000_000L, AppFloors.proFloorSkrRaw)   // the lowest price ever charged
        fun list(name: String) = Regex("$name: List<Double> = (listOf\\(([0-9., ]*)\\)|emptyList\\(\\))").find(src)!!.groupValues[2].split(',').mapNotNull { it.trim().toDoubleOrNull() }
        assertEquals(list("LEGACY_LIST_SOL"), AppFloors.LEGACY_LIST_SOL); assertEquals(list("LEGACY_LIST_SKR"), AppFloors.LEGACY_LIST_SKR)
        assertEquals(num("LIST_SOL"), AppFloors.LIST_SOL); assertEquals(num("LIST_SKR"), AppFloors.LIST_SKR); assertEquals(num("LIST_SKR_SGT"), AppFloors.LIST_SKR_SGT)
        assertEquals(num("BOOST_SOL"), AppFloors.BOOST_SOL); assertEquals(num("BOOST_SKR"), AppFloors.BOOST_SKR)
        assertEquals(str("TREASURY"), Config.TREASURY); assertEquals(str("PAYMENT_MEMO"), Config.PAYMENT_MEMO)
        // The pass is the fleet price, like the listing fee.
        assertEquals(AppFloors.LIST_SOL, Config.PASS_SOL); assertEquals(AppFloors.LIST_SKR, Config.PASS_SKR); assertEquals(AppFloors.LIST_SKR_SGT, Config.PASS_SKR_SGT)
    }

    @Test fun `the API reference is a real address and not the registry`() {
        assertEquals(32, Base58.decode(Config.API_REFERENCE).size)
        assertTrue(Config.API_REFERENCE != Config.REGISTRY)
    }
}

class BillingTest {
    private val payer = Wallet()
    private fun tx(s: String, lamports: Long = Config.passFloorLamports, skr: Long = 0, ref: Boolean = true, memo: String = "UNS", ok: Boolean = true, from: String = payer.address) =
        TxInfo(s, from, 1_000, ok, listOf(memo), 5000, false, listOfNotNull(from, Config.TREASURY, if (ref) Config.API_REFERENCE else null), Config.TREASURY, lamports, if (skr > 0) mapOf(Config.SKR_MINT to skr) else emptyMap())

    @Test fun `signatures verify raw and in the CLI envelope, and nothing else`() {
        val m = "Undersigned API key for ${sig('a')}"
        assertTrue(Billing.verifySignature(payer.address, m, payer.sign(m.toByteArray())))
        assertTrue(Billing.verifySignature(payer.address, m, payer.sign(Billing.offchainV0(m.toByteArray()))))
        assertFalse(Billing.verifySignature(payer.address, m + "x", payer.sign(m.toByteArray())))
        assertFalse(Billing.verifySignature(Wallet().address, m, payer.sign(m.toByteArray())))
        assertFalse(Billing.verifySignature(payer.address, m, "not-a-signature"))
    }

    @Test fun `redeem once, by the payer, at the price, with the reference`() {
        val txs = mutableMapOf<String, TxInfo>()
        var sgt = false
        val b = Billing(tempDb(), { txs[it] }, { sgt })
        fun redeem(s: String, by: Wallet = payer) = b.redeem(s, by.sign(b.message(s).toByteArray()), now = 1_000_000)

        txs[sig('a')] = tx(sig('a'))
        val p = redeem(sig('a'))
        assertEquals(Config.PASS_REQUESTS, p.requestsLeft); assertEquals(1_000_000 + Config.PASS_DAYS * 86_400, p.endsAt)
        val again = redeem(sig('a'))                                                                       // the payer proves it again: the SAME key back, nothing added
        assertEquals(p.key, again.key); assertEquals(Config.PASS_REQUESTS, again.requestsLeft)

        txs[sig('b')] = tx(sig('b')); assertEquals(403, assertFailsWith<Billing.Refused> { redeem(sig('b'), Wallet()) }.status)   // not the payer
        txs[sig('c')] = tx(sig('c'), ref = false); assertEquals(402, assertFailsWith<Billing.Refused> { redeem(sig('c')) }.status)  // no reference
        txs[sig('d')] = tx(sig('d'), lamports = Config.passFloorLamports - 1); assertEquals(402, assertFailsWith<Billing.Refused> { redeem(sig('d')) }.status)
        txs[sig('e')] = tx(sig('e'), ok = false); assertEquals(402, assertFailsWith<Billing.Refused> { redeem(sig('e')) }.status)
        txs[sig('f')] = tx(sig('f'), memo = "XYZ"); assertEquals(402, assertFailsWith<Billing.Refused> { redeem(sig('f')) }.status)
        assertEquals(404, assertFailsWith<Billing.Refused> { redeem(sig('g')) }.status)                      // not on-chain yet

        // The SGT price counts only for a Genesis holder.
        txs[sig('h')] = tx(sig('h'), lamports = 0, skr = Config.passFloorSkrSgtRaw)
        assertEquals(402, assertFailsWith<Billing.Refused> { redeem(sig('h')) }.status)
        sgt = true
        val topped = redeem(sig('h'))
        assertEquals(p.key, topped.key)                                                                        // same pass, topped up
        assertEquals(2 * Config.PASS_REQUESTS, topped.requestsLeft)
        assertEquals(p.endsAt + Config.PASS_DAYS * 86_400, topped.endsAt)
    }

    @Test fun `checkout secret redeems its own payment and nothing else`() {
        val txs = mutableMapOf<String, TxInfo>()
        val b = Billing(tempDb(), { txs[it] }, { false })
        val c = b.checkout()
        assertTrue(c.memo.startsWith("UNS:") && c.memo.length == 4 + 32)
        txs[sig('a')] = tx(sig('a'), memo = c.memo)
        assertEquals(403, assertFailsWith<Billing.Refused> { b.redeem(sig('a'), null, now = 1_000_000, secret = b.checkout().secret) }.status)   // another secret
        assertEquals(400, assertFailsWith<Billing.Refused> { b.redeem(sig('a'), null, now = 1_000_000) }.status)                                   // no proof at all
        val p = b.redeem(sig('a'), null, now = 1_000_000, secret = c.secret)
        assertEquals(payer.address, p.wallet)
        assertEquals(p.key, b.redeem(sig('a'), null, now = 1_000_000, secret = c.secret).key)                // a lost key is recoverable with the same secret
        assertEquals(403, assertFailsWith<Billing.Refused> { b.redeem(sig('a'), null, now = 1_000_000, secret = "someone-else") }.status)
        // A plain "UNS" payment has no commitment: a secret cannot redeem it, only the payer's signature can.
        txs[sig('b')] = tx(sig('b'))
        assertEquals(403, assertFailsWith<Billing.Refused> { b.redeem(sig('b'), null, now = 1_000_000, secret = c.secret) }.status)
    }

    @Test fun `a pass is spent one request at a time and stops at zero or its end`() {
        val db = tempDb(); val b = Billing(db, { null }, { false })
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('k', 'w', 2, 2000, 0)")
        assertTrue(b.consume("k", now = 1000)); assertTrue(b.consume("k", now = 1000)); assertFalse(b.consume("k", now = 1000))
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('old', 'w', 5, 500, 0)")
        assertFalse(b.consume("old", now = 1000))
        assertFalse(b.consume("nope", now = 1000))
    }

    @Test fun `rate limiter windows`() {
        var t = 0L; val r = RateLimiter { t }
        repeat(3) { assertNull(r.take("a", 3, null)) }
        assertEquals("minute", r.take("a", 3, null))
        t = 60_000; assertNull(r.take("a", 3, null))
        val d = RateLimiter { t }; repeat(2) { assertNull(d.take("b", 100, 2)) }; assertEquals("day", d.take("b", 100, 2))
    }
}

class ViewsAndHttpTest {
    private val treasury = Config.TREASURY
    private val reviewer = "8oLtrvMnKLxFfG4PSa5ANxW1Ncvy1h2BCYfZ9mRBHT8F"
    private val stranger = "FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF"

    private fun setup(): Triple<Db, Indexer, Api> {
        val db = tempDb()
        fun rec(id: String, signer: String, t: Long, m: ReviewMemo) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", id, id.substringBefore(':'), signer, t, ReviewMemo.encode(m))
        rec(sig('r'), reviewer, 1_700_000_000, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, "fine"))
        rec(sig('s'), stranger, 1_700_000_100, ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.CRASHES), 3, null))
        rec(sig('t'), treasury, 1_700_000_200, ReviewMemo.Treasury("com.a.b", "UNS"))       // our treasury: free
        rec(sig('u'), stranger, 1_700_000_300, ReviewMemo.Treasury("com.x.y", "XY"))        // unpaid: lists nothing
        // Facts cached fresh, so no RPC runs: the reviewer holds a Genesis Token, the stranger does not.
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", reviewer, System.currentTimeMillis())
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 0, 0, ?)", stranger, System.currentTimeMillis())
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 0, 0, ?)", treasury, System.currentTimeMillis())
        val rpc = Rpc(listOf("http://127.0.0.1:9"))   // unreachable: the test must never touch the network
        val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val boards = Boards(indexer, Board(db, rpc), Prices(db))
        val billing = Billing(db, { null }, { false })
        return Triple(db, indexer, Api(Views(indexer, boards, db), billing, indexer))
    }

    @Test fun `scores count attested reviewers only, and only paid listings list`() {
        val (_, indexer, _) = setup()
        val s = indexer.state
        assertEquals(1, s.scores["com.a.b"]!!.countedReviews)
        assertEquals(listOf("com.a.b"), s.claims.map { it.packageName })
    }

    @Test fun `http gates`() {
        val (db, _, api) = setup()
        fun get(path: String, auth: String? = null, ip: String = "1.1.1.1") = api.handle("GET", path, emptyMap(), "", auth, ip)
        assertEquals(200, get("/v1/stats").status)
        val app = get("/v1/apps/com.a.b").body
        assertEquals(1, app.getJSONObject("signed").getInt("counted_reviews"))
        assertEquals(2, get("/v1/apps/com.a.b/reviews").body.getJSONArray("reviews").length())
        assertEquals(404, get("/v1/apps/com.nothing.here").status)
        // Paid endpoint without a pass: 402 with the price and a Solana Pay URL carrying the API reference.
        val r = get("/v1/records")
        assertEquals(402, r.status)
        assertTrue(r.body.getJSONObject("pricing").getJSONObject("solana_pay").getString("sol").contains("reference=${Config.API_REFERENCE}"))
        // With a pass: served and metered; a spent pass is refused, never served as free.
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('us_k', 'w', 1, ?, 0)", System.currentTimeMillis() / 1000 + 3600)
        assertEquals(200, get("/v1/records", "Bearer us_k").status)
        assertEquals(402, get("/v1/records", "Bearer us_k").status)
        assertEquals(402, get("/v1/apps/com.a.b/reviews", "Bearer us_k").status)
        assertEquals(402, get("/v1/stats", "Bearer us_made_up", ip = "7.7.7.7").status)             // unknown key: refused, not served free
        // /v1/keys/me and unknown keys spend the IP budget: 30 a minute, then 429.
        val me = (1..31).map { get("/v1/keys/me", "Bearer us_made_up", ip = "8.8.8.8").status }
        assertEquals(401, me[0]); assertEquals(429, me[30])
        // Free tier: 30 a minute per IP.
        val codes = (1..31).map { get("/v1/stats", ip = "2.2.2.2").status }
        assertEquals(200, codes[29]); assertEquals(429, codes[30])
        assertEquals(404, get("/v1/nope").status)
        val spec = get("/v1/openapi.json").body
        assertNotNull(spec.getJSONObject("paths").optJSONObject("/v1/apps/{pkg}/bursts"))
        assertEquals("paid", spec.getJSONObject("paths").getJSONObject("/v1/records").getJSONObject("get").getString("x-tier"))
    }

    @Test fun `redeem endpoint refuses bad input without touching the chain`() {
        val (_, _, api) = setup()
        assertEquals(400, api.handle("POST", "/v1/keys", emptyMap(), "not json", null, "3.3.3.3").status)
        assertEquals(400, api.handle("POST", "/v1/keys", emptyMap(), JSONObject().put("signature", "x").toString(), null, "3.3.3.3").status)
        assertEquals(400, api.handle("POST", "/v1/keys", emptyMap(), JSONObject().put("signature", "bad").put("message_signature", "y").toString(), null, "3.3.3.3").status)
    }
}
