package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A promoted review on the API and website: its own labelled score and its own section placed AFTER the organic reviews
 * (never first), the signed score identical with or without the promotion, and an unpaid promotion honoured nowhere.
 */
class PromotedApiTest {
    private val member = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val other = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"
    private fun sig(c: Char) = c.toString().repeat(87)

    private fun setup(boost: Boolean, paidLamports: Long = 6_070_000L): Pair<Views, Web> {
        val db = Db(File.createTempFile("us-promo", ".db").apply { deleteOnExit() }.path)
        fun rec(c: Char, who: String, memo: ReviewMemo, t: Long) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sig(c), sig(c), who, t, ReviewMemo.encode(memo))
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App").put("u", "2026-09-01").put("c", "Tools")).toString())
        rec('p', member, ReviewMemo.Pro(12), 1_700_000_000L)
        rec('a', member, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 42, "Fast and it just works on my Seeker"), 1_700_000_100L)
        rec('b', other, ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.CRASHES), 42, "Crashes every time I open the wallet"), 1_700_000_100L)
        db.exec("INSERT INTO facts (wallet, sgt, staked, at, sol) VALUES (?, 1, 50000, ?, 12.5)", member, 1_760_000_000_000L)
        db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", other, System.currentTimeMillis())
        if (boost) {
            rec('x', member, ReviewMemo.Boost(sig('a')), 1_700_000_200L)
            db.exec("INSERT INTO txmeta (sig, fee, seeker, lamports, skr) VALUES (?, 5000, 1, ?, 0)", sig('x'), paidLamports)
        }
        val rpc = Rpc(listOf("http://127.0.0.1:9"))
        val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val views = Views(indexer, Boards(indexer, Board(db, rpc), Prices(db)), db)
        return views to Web(views, indexer)
    }

    @Test fun `a promoted review sits in its own section after organic reviews and never moves the signed score`() {
        val (v, web) = setup(boost = true)
        val signed = v.app("com.a.b")!!.getJSONObject("signed")
        assertEquals(1, signed.getJSONObject("promoted").getInt("counted_reviews"))
        assertEquals(setup(boost = false).first.app("com.a.b")!!.getJSONObject("signed").getDouble("mean"), signed.getDouble("mean"), 1e-9)
        val html = String(web.handle("/app/com.a.b", emptyMap()).raw!!)
        val organic = html.indexOf("<h3>Organic reviews · 1</h3>"); val promoted = html.indexOf("<h3>Promoted reviews · paid · 1</h3>")
        assertTrue(organic > 0 && promoted > organic, "organic $organic, promoted $promoted")
        assertTrue("Promoted · paid</span>" in html)
    }

    @Test fun `a review shows the stake as read at the time, dated by the read, and it never changes the weight`() {
        val (v, web) = setup(boost = false)
        val reviews = v.reviews("com.a.b", 10, 0).getJSONArray("reviews")
        val mine = (0 until reviews.length()).map { reviews.getJSONObject(it) }.first { it.getString("signer") == member }.getJSONObject("reviewer")
        val k = mine.getJSONObject("stake_at_review")
        assertEquals(50000.0, k.getDouble("skr"), 1e-9); assertEquals(12.5, k.getDouble("sol"), 1e-9); assertEquals(1_760_000_000L, k.getLong("read_at"))
        assertEquals(1.0, mine.getJSONObject("weight_parts").getDouble("device"), 1e-9)   // a big stake, the same device weight
        assertTrue("Staked 50,000 SKR · 12.50 SOL · read" in String(web.handle("/app/com.a.b", emptyMap()).raw!!))
    }

    @Test fun `an unpaid promotion is honoured nowhere`() {
        val (v, web) = setup(boost = true, paidLamports = 1_000L)
        assertTrue(v.app("com.a.b")!!.getJSONObject("signed").isNull("promoted"))
        assertTrue("Promoted reviews" !in String(web.handle("/app/com.a.b", emptyMap()).raw!!))
    }
}
