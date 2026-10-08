package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A tip end to end on the API: a proven publisher (by the store build), a tipper's tip record with its credit, the tipper's
 * review. The app JSON carries the totals, the tipped score and the recipient; the review says it tipped; the page shows the
 * heading and the pill; the signed score is the same with or without the tip.
 */
class TipsApiTest {
    private val dev = "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"
    private val fan = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val other = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"
    private fun sig(c: Char) = c.toString().repeat(87)

    private fun setup(withTip: Boolean, credited: Long = 10_000_000L): Pair<Views, Web> {
        val db = Db(File.createTempFile("us-tips", ".db").apply { deleteOnExit() }.path)
        fun rec(c: Char, who: String, memo: ReviewMemo, t: Long = 1_700_000_000L) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", sig(c), sig(c), who, t, ReviewMemo.encode(memo))
        db.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App").put("u", "2026-09-01").put("c", "Tools")).toString())
        db.exec("INSERT INTO details (pkg, version, cert, publisher, website, email, notes, at) VALUES ('com.a.b', 42, 'ab', 'Pub', NULL, NULL, NULL, 0)")
        db.exec("INSERT INTO apk_proofs (pkg, version, cert, declared, at) VALUES ('com.a.b', 42, 'ab', ?, 0)", dev)
        rec('c', dev, ReviewMemo.Claim("com.a.b"))
        rec('a', fan, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 42, "Fast and it just works on my Seeker"))
        rec('b', other, ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.CRASHES), 42, "Crashes every time I open the wallet"))
        for (w in listOf(fan, other)) db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", w, System.currentTimeMillis())
        if (withTip) {
            rec('t', fan, ReviewMemo.Tip("com.a.b", dev))
            db.exec("INSERT INTO tipcredit (sig, recipient, lamports, skr) VALUES (?, ?, ?, 0)", sig('t'), dev, credited)
        }
        val rpc = Rpc(listOf("http://127.0.0.1:9"))
        val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val views = Views(indexer, Boards(indexer, Board(db, rpc), Prices(db)), db)
        return views to Web(views, indexer)
    }

    @Test fun `a tip shows apart and never moves the signed score`() {
        val (v, web) = setup(withTip = true)
        val signed = v.app("com.a.b")!!.getJSONObject("signed")
        val tips = signed.getJSONObject("tips")
        assertEquals(dev, tips.getString("tip_to")); assertEquals(1, tips.getInt("tips")); assertEquals(10_000_000L, tips.getLong("lamports"))
        assertEquals(1, signed.getJSONObject("tipped").getInt("counted_reviews"))
        val (v0, _) = setup(withTip = false)
        assertEquals(v0.app("com.a.b")!!.getJSONObject("signed").getDouble("mean"), signed.getDouble("mean"), 1e-9)
        val reviews = v.reviews("com.a.b", 10, 0).getJSONArray("reviews")
        val byFan = (0 until reviews.length()).map { reviews.getJSONObject(it) }.first { it.getString("signer") == fan }
        assertTrue(byFan.getBoolean("tipped_developer"))
        val html = String(web.handle("/app/com.a.b", emptyMap()).raw!!)
        assertTrue("<h3>Tipped the developer · 1</h3>" in html && "<h3>Organic reviews · 1</h3>" in html && "Tips to the developer" in html)
    }

    @Test fun `a tip under the floor or to an unproven wallet counts nothing`() {
        val (v, _) = setup(withTip = true, credited = 999_999L)
        val signed = v.app("com.a.b")!!.getJSONObject("signed")
        assertEquals(0, signed.getJSONObject("tips").getInt("tips")); assertTrue(signed.isNull("tipped"))
        val (v2, web2) = setup(withTip = false)
        assertFalse(v2.app("com.a.b")!!.getJSONObject("signed").getJSONObject("tips").isNull("tip_to"))
        assertTrue("No tips yet." in String(web2.handle("/app/com.a.b", emptyMap()).raw!!))
    }
}
