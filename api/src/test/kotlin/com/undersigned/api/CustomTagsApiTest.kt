package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import java.io.File
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A Pro reviewer's own tags show on the review and on the app; the same record from a wallet without Pro shows nowhere. */
class CustomTagsApiTest {
    private fun wallet() = Base58.encode(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.takeLast(32).toByteArray())
    private val member = wallet(); private val other = wallet()
    private fun sig(c: Char) = c.toString().repeat(87)

    private fun setup(): Pair<Views, Web> {
        val db = Db(File.createTempFile("us-ctg", ".db").apply { deleteOnExit() }.path)
        fun rec(id: String, who: String, memo: ReviewMemo, t: Long) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", id, id.substringBefore(':'), who, t, ReviewMemo.encode(memo))
        rec(sig('p'), member, ReviewMemo.Pro(12), 1_700_000_000L)
        rec(sig('a'), member, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, null), 1_700_100_000L)
        rec(sig('a') + ":1", member, ReviewMemo.CustomTags("com.a.b", listOf("offline-maps", "dark-mode")), 1_700_100_000L)
        rec(sig('b'), other, ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.FAST), 3, null), 1_700_100_100L)
        rec(sig('b') + ":1", other, ReviewMemo.CustomTags("com.a.b", listOf("nice-ui")), 1_700_100_100L)
        for (w in listOf(member, other)) db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", w, System.currentTimeMillis())
        val rpc = Rpc(listOf("http://127.0.0.1:9"))
        val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        val views = Views(indexer, Boards(indexer, Board(db, rpc), Prices(db)), db)
        return views to Web(views, indexer)
    }

    @Test fun `own tags show for the Pro reviewer only, on the review and on the app`() {
        val (v, web) = setup()
        val reviews = v.reviews("com.a.b", 10, 0).getJSONArray("reviews")
        val byWallet = (0 until reviews.length()).map { reviews.getJSONObject(it) }.associateBy { it.getString("signer") }
        assertEquals(listOf("offline-maps", "dark-mode"), byWallet[member]!!.getJSONArray("custom_tags").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertTrue(byWallet[other]!!.isNull("custom_tags"), "no Pro at the time: the record is ignored")
        val agg = v.app("com.a.b")!!.getJSONObject("signed").getJSONObject("custom_tags")
        assertEquals(1, agg.getInt("offline-maps")); assertEquals(1, agg.getInt("dark-mode")); assertTrue(!agg.has("nice-ui"))
        val html = String(web.handle("/app/com.a.b", emptyMap()).raw!!)
        assertTrue("offline-maps" in html && "nice-ui" !in html)
    }
}
