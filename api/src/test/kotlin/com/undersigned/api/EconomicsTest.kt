package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Cost against revenue per day, its alerts, and the shared daily budget for callers without a key. */
class EconomicsTest {
    private val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
    private fun db() = Db(File.createTempFile("us-econ", ".db").apply { deleteOnExit() }.path).also { Ai(it) }   // Ai creates its tables
    private val prices = mapOf(Prices.SOL to mapOf(today to 100.0), Config.SKR_MINT to mapOf(today to 0.02))

    @Test fun `cost by caller, API and app revenue, and margin`() {
        val db = db()
        db.exec("INSERT INTO ai_segment (day, segment, calls, in_tok, out_tok) VALUES (?, 'anon', 10, 30000, 2000)", today)    // $0.003 + $0.001
        db.exec("INSERT INTO redeemed (sig, key, wallet, rail, amount, at) VALUES ('s1', 'k', 'w', 'SOL', 60700000, ?)", System.currentTimeMillis() / 1000)   // 0.0607 SOL = $6.07
        db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES ('p1', 'p1', 'w', ?, ?)", System.currentTimeMillis() / 1000, ReviewMemo.encode(ReviewMemo.Pro(12)))
        db.exec("INSERT INTO txmeta (sig, fee, seeker, lamports, skr) VALUES ('p1', 5000, 0, 0, 369000000)")                     // 369 SKR = $7.38
        val r = Economics(db) { prices }.report(7)
        val t = r.getJSONObject("today")
        assertEquals(0.004, t.getDouble("ai_cost_usd")); assertEquals(6.07, t.getDouble("api_revenue_usd")); assertEquals(7.38, t.getDouble("app_revenue_usd"))
        assertEquals(13.446, t.getDouble("margin_usd")); assertEquals(10, t.getJSONObject("ai_by_caller").getJSONObject("anon").getInt("answers"))
        assertEquals(0, r.getJSONArray("alerts").length())
    }

    @Test fun `a costly day and a week of cost over revenue each raise an alert`() {
        val db = db()
        db.exec("INSERT INTO ai_segment (day, segment, calls, in_tok, out_tok) VALUES (?, 'pass', 2000, 6000000, 1200000)", today)   // $0.60 + $0.60
        val alerts = Economics(db) { prices }.report(7).getJSONArray("alerts")
        assertEquals(2, alerts.length()); assertTrue("today" in alerts.getString(0)); assertTrue("7 days" in alerts.getString(1))
    }

    @Test fun `callers without a key share one daily budget`() {
        val db = db()
        db.exec("INSERT INTO ai_segment (day, segment, calls) VALUES (?, 'anon', ?)", today, Config.AI_ANON_POOL_DAY)
        val ai = Ai(db, call = { _, _, _ -> "x" })
        Ai.who.set(Ai.Caller("ip:1.2.3.4", Config.AI_PER_IP_DAY, "anon"))
        try { assertFailsWith<Ai.AllowanceUsed> { ai.help("A fresh question?", "p", "en") } } finally { Ai.who.remove() }
        Ai.who.set(Ai.Caller("key:k", Config.AI_PER_PASS_DAY, "pass"))
        try { assertEquals("x", ai.help("A fresh question?", "p", "en")) } finally { Ai.who.remove() }      // a key has its own allowance
        assertEquals(1, ai.segmentCalls("pass"))
    }

    @Test fun `the economics page answers on this machine only`() {
        val db = db()
        val rpc = Rpc(listOf("http://127.0.0.1:9")); val ix = Indexer(db, rpc, Store(0))
        val api = Api(Views(ix, Boards(ix, Board(db, rpc), Prices(db)), db), Billing(db, { null }, { false }), ix).also { it.economics = Economics(db) { prices } }
        assertEquals(404, api.handle("GET", "/v1/admin/economics", emptyMap(), "", null, "8.8.8.8").status)
        assertEquals(200, api.handle("GET", "/v1/admin/economics", emptyMap(), "", null, "127.0.0.1").status)
    }
}
