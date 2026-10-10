package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The relayer pays fees for quick reviews and nothing else. */
class RelayerTest {
    private fun keypair(): Pair<PrivateKey, ByteArray> = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().let { it.private to it.public.encoded.takeLast(32).toByteArray() }
    private val relay = keypair(); private val app = keypair()
    private val keyFile = File.createTempFile("relayer", ".key").apply { deleteOnExit(); writeBytes(relay.first.encoded) }
    private var sent = 0
    private val rpc = object : Rpc(listOf("http://127.0.0.1:9")) { override fun sendTransaction(base64: String): String { sent++; return "sig$sent" } }
    private fun relayer() = Relayer(Db(File.createTempFile("us-relay", ".db").apply { deleteOnExit() }.path), rpc, keyFile, Base58.encode(relay.second))

    private fun cu16(o: ByteArrayOutputStream, v: Int) { var x = v; while (true) { val b = x and 0x7f; x = x ushr 7; if (x == 0) { o.write(b); return }; o.write(b or 0x80) } }
    /** A quick-review transaction as the phone builds it, optionally bent to test a refusal. */
    private fun tx(memos: List<String>, feePayer: ByteArray = relay.second, lamports: Byte = 0, signWith: PrivateKey = app.first, extraProgram: Boolean = false, appKey: ByteArray = app.second): String {
        val keys = listOf(feePayer, appKey, Base58.decode(Config.MEMO_PROGRAM), ByteArray(32), Base58.decode(Config.REGISTRY)) + (if (extraProgram) listOf(ByteArray(32) { 7 }) else emptyList())
        val msg = ByteArrayOutputStream()
        msg.write(2); msg.write(1); msg.write(keys.size - 2)
        cu16(msg, keys.size); keys.forEach { msg.write(it) }; msg.write(ByteArray(32) { 1 })
        val ixs = memos.size + 1 + (if (extraProgram) 1 else 0); cu16(msg, ixs)
        memos.forEach { m -> msg.write(2); cu16(msg, 1); msg.write(1); val d = m.toByteArray(); cu16(msg, d.size); msg.write(d) }
        msg.write(3); cu16(msg, 3); msg.write(0); msg.write(0); msg.write(4); cu16(msg, 12); msg.write(byteArrayOf(2, 0, 0, 0, lamports, 0, 0, 0, 0, 0, 0, 0))
        if (extraProgram) { msg.write(5); cu16(msg, 0); cu16(msg, 1); msg.write(9) }
        val m = msg.toByteArray()
        val sig = Signature.getInstance("Ed25519").run { initSign(signWith); update(m); sign() }
        return Base64.getEncoder().encodeToString(ByteArrayOutputStream().apply { write(2); write(ByteArray(64)); write(sig); write(m) }.toByteArray())
    }
    private val review = ReviewMemo.encode(ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.WORKS), 3, "Quick and fine"))
    private val quick = ReviewMemo.encode(ReviewMemo.Quick)

    @Test fun relaysAQuickReviewAndCoSignsAsFeePayer() {
        val r = relayer()
        assertEquals("sig1", r.relay(tx(listOf(review, quick)), "1.1.1.1"))
        assertEquals(Relayer.FEE_LAMPORTS, r.feesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()))
        // Long-text pieces travel alone.
        assertEquals("sig2", r.relay(tx(listOf(ReviewMemo.encode(ReviewMemo.TextPart("0123456789abcdef", 0, "more words")))), "1.1.1.1"))
    }

    @Test fun refusesAnythingElse() {
        val r = relayer()
        fun refused(t: String) = assertFailsWith<Relayer.Refused> { r.relay(t, "2.2.2.2") }
        refused(tx(listOf(review, quick), feePayer = keypair().second))            // someone else pays
        refused(tx(listOf(review, quick), lamports = 1))                            // the carrier moves lamports
        refused(tx(listOf(review)))                                                 // no quick marker
        refused(tx(listOf(review, quick), signWith = keypair().first))              // forged app-key signature
        refused(tx(listOf(review, quick), extraProgram = true))                     // another program
        refused(tx(listOf(ReviewMemo.encode(ReviewMemo.Upvote("x".repeat(87))), quick)))   // not a quick-review record
        refused(tx(listOf(review, review, quick)))                                  // two reviews
        assertEquals(0, sent)
    }

    @Test fun threeQuickReviewsADayFromOnePhone() {
        val r = relayer()
        repeat(Relayer.PER_KEY_REVIEWS_DAY) { r.relay(tx(listOf(review, quick)), "3.3.3.3") }
        val e = assertFailsWith<Relayer.Refused> { r.relay(tx(listOf(review, quick)), "3.3.3.3") }
        assertEquals(429, e.status); assertTrue("a day" in e.message!!)
    }

    // The phone's own builder (protocol QuickTx) produces exactly what the relayer accepts.
    @Test fun thePhonesBuilderIsAccepted() {
        val msg = com.undersigned.protocol.QuickTx.message(relay.second, app.second, Base58.decode(Config.MEMO_PROGRAM), Base58.decode(Config.REGISTRY), listOf(review, quick), ByteArray(32) { 1 })
        val sig = Signature.getInstance("Ed25519").run { initSign(app.first); update(msg); sign() }
        assertEquals("sig1", relayer().relay(Base64.getEncoder().encodeToString(com.undersigned.protocol.QuickTx.transaction(msg, sig)), "4.4.4.4"))
    }

    @Test fun oneIpv6NetworkIsOneBucketAndAFailedSendReleasesItsSlot() {
        assertEquals("2001:db8:1:2::/64", Relayer.network("2001:db8:1:2:aaaa:bbbb:cccc:1"))
        assertEquals("2001:db8:0:0::/64", Relayer.network("2001:db8::1")); assertEquals(Relayer.network("2001:db8::1"), Relayer.network("2001:db8:0:0:ffff::9"))   // compressed forms, one network
        assertEquals(Relayer.network("2001:db8:1:2:aaaa:bbbb:cccc:1"), Relayer.network("2001:db8:1:2:ffff:0:0:9"))
        assertEquals("9.9.9.9", Relayer.network("9.9.9.9"))
        val r = relayer()
        val review = ReviewMemo.encode(ReviewMemo.Review("com.a.b", 4, listOf(ReviewMemo.Tag.WORKS), 3, null)); val quick = ReviewMemo.encode(ReviewMemo.Quick)
        // Different app keys from addresses across one /64 share the hourly network cap.
        repeat(Relayer.PER_IP_TX_HOUR) { i -> val k = keypair(); r.relay(tx(listOf(review, quick), signWith = k.first, appKey = k.second), "2001:db8:1:2::" + (i + 1)) }
        val k = keypair()
        assertEquals(429, assertFailsWith<Relayer.Refused> { r.relay(tx(listOf(review, quick), signWith = k.first, appKey = k.second), "2001:db8:1:2::ffff") }.status)
        // A send the network refuses leaves no count behind.
        val failing = Relayer(Db(File.createTempFile("us-relay2", ".db").apply { deleteOnExit() }.path), object : Rpc(listOf("http://127.0.0.1:9")) { override fun sendTransaction(base64: String): String = throw RpcException("down") }, keyFile, Base58.encode(relay.second))
        assertEquals(502, assertFailsWith<Relayer.Refused> { failing.relay(tx(listOf(review, quick)), "4.4.4.4") }.status)
        assertEquals(0L, failing.feesOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()))
    }
}
