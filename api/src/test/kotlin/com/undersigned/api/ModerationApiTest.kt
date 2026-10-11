package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import java.io.File
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Abusive text is hidden automatically with the reason "abuse" (the rating still counts); a moderator's ban removes the wallet's records from every reader. */
class ModerationApiTest {
    private fun wallet() = Base58.encode(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.takeLast(32).toByteArray())
    private val troll = wallet(); private val kind = wallet(); private val mod = com.undersigned.protocol.Moderation.MODERATORS.first()
    private fun sig(c: Char) = c.toString().repeat(87)

    private fun setup(ban: Boolean): Views {
        val db = Db(File.createTempFile("us-mod", ".db").apply { deleteOnExit() }.path)
        fun rec(id: String, who: String, memo: ReviewMemo, t: Long) = db.exec("INSERT INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", id, id.substringBefore(':'), who, t, ReviewMemo.encode(memo))
        rec(sig('a'), troll, ReviewMemo.Review("com.a.b", 1, listOf(ReviewMemo.Tag.CRASHES), 3, "the dev is a fucking idiot"), 1_700_000_000L)
        rec(sig('b'), kind, ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.WORKS), 3, "works for me"), 1_700_000_100L)
        if (ban) rec(sig('m'), mod, ReviewMemo.Ban(troll, ReviewMemo.ModReason.ABUSE), 1_700_000_200L)
        for (w in listOf(troll, kind)) db.exec("INSERT INTO facts (wallet, sgt, staked, at) VALUES (?, 1, 0, ?)", w, System.currentTimeMillis())
        val rpc = Rpc(listOf("http://127.0.0.1:9"))
        val indexer = Indexer(db, rpc, Store(0)).also { it.rebuild() }
        return Views(indexer, Boards(indexer, Board(db, rpc), Prices(db)), db)
    }

    @Test fun `abusive text is hidden with the reason abuse while the rating counts`() {
        val v = setup(ban = false)
        val rv = v.reviews("com.a.b", 10, 0).getJSONArray("reviews"); val byWallet = (0 until rv.length()).map { rv.getJSONObject(it) }.associateBy { it.getString("signer") }
        assertTrue(byWallet[troll]!!.isNull("text")); assertEquals("abuse", byWallet[troll]!!.getString("hidden_by_moderator"))
        assertEquals(2, v.app("com.a.b")!!.getJSONObject("signed").getInt("counted_reviews"))
    }

    @Test fun `a moderator ban removes the wallet from every reader`() {
        val v = setup(ban = true)
        val rv = v.reviews("com.a.b", 10, 0).getJSONArray("reviews")
        assertEquals(listOf(kind), (0 until rv.length()).map { rv.getJSONObject(it).getString("signer") })
        assertEquals(1, v.app("com.a.b")!!.getJSONObject("signed").getInt("counted_reviews"))
        assertTrue(v.wallet(troll, false).getBoolean("banned"))
    }
}
