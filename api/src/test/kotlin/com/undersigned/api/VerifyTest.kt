package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `api verify` must catch a tampered dataset, not only agree with an empty one (judge, 11 Oct 2026: "it compares 0 with 0").
 * A ledger with one counted review is scored by the protocol module; the live dataset is then altered one way at a time.
 */
class VerifyTest {
    private fun wallet() = Base58.encode(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.takeLast(32).toByteArray())
    private val reviewer = wallet(); private val stranger = wallet(); private val treasury = wallet()
    private fun sig(c: Char) = c.toString().repeat(87)
    private val now = System.currentTimeMillis() / 1000

    /** The chain read and the live dataset built from the same ledger: one attested 5★ review, one unattested 1★. */
    private fun fixture(): Pair<ChainRead, JSONObject> {
        val db = Db(File.createTempFile("us-verify", ".db").apply { deleteOnExit() }.path)
        fun rec(id: String, signer: String, t: Long, m: ReviewMemo) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", id, id.substringBefore(':'), signer, t, ReviewMemo.encode(m))
        rec(sig('r'), reviewer, now - 3 * 86_400, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, "fine"))
        rec(sig('s'), stranger, now - 2 * 86_400, ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.CRASHES), 3, null))
        rec(sig('t'), treasury, now - 86_400, ReviewMemo.Treasury("com.a.b", "UNS"))
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", reviewer, System.currentTimeMillis())
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 0, 0, ?)", stranger, System.currentTimeMillis())
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 0, 0, ?)", treasury, System.currentTimeMillis())
        val rpc = Rpc(listOf("http://127.0.0.1:9"))
        val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val st = indexer.state
        val chain = ChainRead(st.scores.values.associate { it.packageName to Triple(it.countedReviews, Views.r2(it.weightedMean), Views.r2(it.weightedRating)) }, st.entries.associate { it.signature to it.blockTime })
        val live = Views(indexer, Boards(indexer, Board(db, rpc), Prices(db)), db).dataset()
        return chain to live
    }

    private fun app(live: JSONObject, pkg: String): JSONObject = live.getJSONArray("apps").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("package") == pkg } }

    @Test fun `an untouched dataset verifies, and the run says how much it compared`() {
        val (chain, live) = fixture()
        assertEquals(1, chain.scores["com.a.b"]!!.first, "the attested review is counted; the unattested one is not")
        val r = verifyDiff(chain, live, now)
        assertTrue(r.ok, r.diffs.toString())
        assertEquals(1, r.scoredChain); assertEquals(1, r.scoredLive); assertEquals(3, r.recordsChain); assertEquals(3, r.recordsLive); assertEquals(0, r.pending)
        assertEquals("OK: 1 scored apps compared, 3 records matched", r.summary())
    }

    @Test fun `a tampered mean is a difference`() {
        val (chain, live) = fixture()
        app(live, "com.a.b").put("mean", 4.9)
        val r = verifyDiff(chain, live, now)
        assertEquals(1, r.diffs.size, r.diffs.toString()); assertTrue(r.diffs[0].startsWith("com.a.b:"))
    }

    @Test fun `a tampered count is a difference even when the mean agrees`() {
        val (chain, live) = fixture()
        app(live, "com.a.b").put("counted_reviews", 2)
        assertFalse(verifyDiff(chain, live, now).ok)
    }

    @Test fun `a score the chain read does not have is a difference`() {
        val (chain, live) = fixture()
        live.getJSONArray("apps").put(JSONObject().put("package", "com.ghost").put("counted_reviews", 1).put("mean", 5.0))
        assertTrue(verifyDiff(chain, live, now).diffs.any { "com.ghost" in it })
    }

    @Test fun `a record the chain does not have is a difference whatever its age`() {
        val (chain, live) = fixture()
        live.getJSONArray("records").put(JSONObject().put("id", sig('z')).put("time", now))
        val r = verifyDiff(chain, live, now)
        assertEquals(1, r.diffs.size, r.diffs.toString()); assertTrue("not on the chain" in r.diffs[0])
    }

    @Test fun `a chain record the live index lacks is pending while young and a difference once old`() {
        val (chain, live) = fixture()
        val recs = live.getJSONArray("records")
        val keep = (0 until recs.length()).map { recs.getJSONObject(it) }.filter { it.getString("id") != sig('t') }
        live.put("records", org.json.JSONArray(keep))
        val young = verifyDiff(chain.copy(ids = chain.ids + (sig('t') to now - 60)), live, now)
        assertTrue(young.ok, young.diffs.toString()); assertEquals(1, young.pending); assertTrue("not indexed yet" in young.summary())
        val old = verifyDiff(chain, live, now)   // the treasury record is a day old
        assertEquals(1, old.diffs.size, old.diffs.toString()); assertTrue("not in the live dataset" in old.diffs[0])
    }
}
