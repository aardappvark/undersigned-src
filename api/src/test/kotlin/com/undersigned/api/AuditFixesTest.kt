package com.undersigned.api

import com.undersigned.protocol.Apk
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun tmpDb() = Db(File.createTempFile("us-fix", ".db").apply { deleteOnExit() }.path)
private fun sigOf(c: Char) = c.toString().repeat(87)
private const val PAYER = "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD"

/** The shape mainnet returned for the owner's 1 SKR pass payment (jsonParsed, 29 Sep 2026), reduced to what the parser reads. */
private fun skrPaymentJson(ok: Boolean = true, extraKey: String? = null): JSONObject {
    val keys = JSONArray().put(JSONObject().put("pubkey", PAYER)).put(JSONObject().put("pubkey", "senderAta1111111111111111111111111111111111"))
        .put(JSONObject().put("pubkey", "GcV5XyhuaScv5Rp77RjpsJiGUaSFjtGdA8c4nUrsDFLk")).put(JSONObject().put("pubkey", Config.API_REFERENCE)).put(JSONObject().put("pubkey", Config.MEMO_PROGRAM))
    extraKey?.let { keys.put(JSONObject().put("pubkey", it)) }
    val ixs = JSONArray().put(JSONObject().put("program", "spl-memo").put("programId", Config.MEMO_PROGRAM).put("parsed", "UNS:1988ea7eb60c1e294626b6c88738ca5e"))
        .put(JSONObject().put("program", "spl-token").put("programId", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA").put("parsed", JSONObject().put("type", "transfer")))
    fun bal(idx: Int, mint: String, owner: String, amount: String) = JSONObject().put("accountIndex", idx).put("mint", mint).put("owner", owner).put("uiTokenAmount", JSONObject().put("amount", amount))
    val meta = JSONObject().put("err", if (ok) JSONObject.NULL else JSONObject().put("InstructionError", JSONArray())).put("fee", 5000)
        .put("preBalances", JSONArray().put(2_745_467_084L).put(2_039_280L).put(2_039_280L).put(0L).put(0L)).put("postBalances", JSONArray().put(2_745_462_084L).put(2_039_280L).put(2_039_280L).put(0L).put(0L))
        .put("preTokenBalances", JSONArray().put(bal(1, Config.SKR_MINT, PAYER, "1003199791")).put(bal(2, Config.SKR_MINT, Config.TREASURY, "7432410000")))
        .put("postTokenBalances", JSONArray().put(bal(1, Config.SKR_MINT, PAYER, "1002199791")).put(bal(2, Config.SKR_MINT, Config.TREASURY, "7433410000")))
    return JSONObject().put("blockTime", 1790604692L).put("meta", meta).put("transaction", JSONObject().put("message", JSONObject().put("accountKeys", keys).put("instructions", ixs)))
}

class MoneyPathTest {
    @Test fun `treasury credit is read from balances, not from the memo`() {
        val t = Rpc(listOf("http://127.0.0.1:9")).parseTx(sigOf('a'), skrPaymentJson(), Config.TREASURY)!!
        assertTrue(t.ok); assertEquals(PAYER, t.feePayer); assertEquals(5000L, t.fee)
        assertEquals(0L, t.lamportsIn)                                            // the treasury wallet itself was not credited
        assertEquals(mapOf(Config.SKR_MINT to 1_000_000L), t.tokensIn)            // exactly 1 SKR to its token account
        assertEquals(listOf("UNS:1988ea7eb60c1e294626b6c88738ca5e"), t.memos)
        assertTrue(Config.API_REFERENCE in t.accountKeys)
        // The same transaction read for another treasury credits nothing.
        assertEquals(emptyMap(), Rpc(listOf("http://127.0.0.1:9")).parseTx(sigOf('a'), skrPaymentJson(), PAYER)!!.tokensIn)
        assertFalse(Rpc(listOf("http://127.0.0.1:9")).parseTx(sigOf('a'), skrPaymentJson(ok = false), Config.TREASURY)!!.ok)
    }

    @Test fun `a payment naming both the registry and the pass reference buys nothing twice`() {
        val db = tmpDb()
        val tx = Rpc(listOf("http://127.0.0.1:9")).parseTx(sigOf('a'), skrPaymentJson(extraKey = Config.REGISTRY), Config.TREASURY)!!
        val b = Billing(db, { tx }, { false })
        val c = b.checkout()
        assertEquals(402, assertFailsWith<Billing.Refused> { b.redeem(sigOf('a'), null, secret = c.secret) }.status)
    }

    @Test fun `the ledger never turns a pass payment into a Pro licence, and gives up on a lost transaction`() {
        val db = tmpDb()
        val pass = Rpc(listOf("http://127.0.0.1:9")).parseTx(sigOf('p'), skrPaymentJson(), Config.TREASURY)!!.let { it.copy(tokensIn = mapOf(Config.SKR_MINT to Config.skrRaw(651.0))) }
        val rpc = object : Rpc(listOf("http://127.0.0.1:9")) {
            override fun signatures(address: String, limit: Int, before: String?, until: String?): List<SignatureRow> =
                if (before != null) emptyList() else listOf(SignatureRow(sigOf('p'), 1, false, "[3] UNS"), SignatureRow(sigOf('l'), 2, false, "[9] SRL1|clm|x"))
            override fun tx(signature: String, creditsTo: String?): TxInfo? = if (signature == sigOf('p')) pass else null   // 'l' is lost
        }
        val ix = Indexer(db, rpc, Store(0))
        repeat(3) { ix.syncLedger() }
        assertEquals(0, db.query("SELECT count(*) FROM records") { it.getInt(1) }.first())              // no Pro record for the pass
        assertEquals(1, db.query("SELECT count(*) FROM txmeta") { it.getInt(1) }.first())
        assertEquals("3", db.get("ledger_gaveup:${sigOf('l')}"))                                        // three failed rounds, then given up
        assertNotNull(db.get("ledger_head"))                                                            // and the head advances at last
    }
}

class ConfigAndMeteringTest {
    @Test fun `test prices only on a private loopback server`() {
        assertEquals(0.0001, Config.testPrice("0.0001", host = "127.0.0.1", public = false))
        assertNull(Config.testPrice("0.0001", host = "127.0.0.1", public = true))     // a tunnel forwards to loopback: still public
        assertNull(Config.testPrice("0.0001", host = "0.0.0.0", public = false))
        assertNull(Config.testPrice("abc", host = "127.0.0.1", public = false))
    }

    @Test fun `the client address is the last proxy hop, never the first`() {
        assertEquals("9.9.9.9", Api.clientIp("9.9.9.9", "1.2.3.4, 5.6.7.8", trustProxy = false))
        assertEquals("5.6.7.8", Api.clientIp("9.9.9.9", "1.2.3.4, 5.6.7.8", trustProxy = true))
        assertEquals("9.9.9.9", Api.clientIp("9.9.9.9", null, trustProxy = true))
    }

    @Test fun `limiter tables shrink once windows pass`() {
        var t = 0L; val r = RateLimiter { t }
        repeat(100) { r.take("c$it", 5, 50) }; assertEquals(200, r.size)
        t = 100_000; r.sweep(); assertEquals(100, r.size)      // minute windows gone, day windows stay
        t = 100_000_000; r.sweep(); assertEquals(0, r.size)
    }

    @Test fun `prices persist across fetches`() {
        val db = tmpDb(); val p = Prices(db)
        db.exec("INSERT INTO prices (mint, day, usd) VALUES (?, '2025-01-01', 200.0)", Prices.SOL)
        val pay = com.undersigned.protocol.Payments.Payment(sigOf('x'), PAYER, 1_735_700_000L, 1_000_000_000L, emptyMap())   // 1 SOL on 2025-01-01
        val h = mapOf(Prices.SOL to mapOf("2025-01-01" to 200.0))
        assertEquals(200.0, p.usdWhenPaid(pay, h))
        assertNull(p.usdWhenPaid(pay.copy(time = 1_600_000_000L), h))
        assertTrue(Prices.meaningful(pay)); assertFalse(Prices.meaningful(pay.copy(lamports = 1L)))
        assertFalse(Prices.meaningful(pay.copy(lamports = 0L, tokens = mapOf(Config.USDC_MINT to 1L))))
        assertTrue(Prices.meaningful(pay.copy(lamports = 0L, tokens = mapOf(Config.USDC_MINT to 100_000L))))
    }
}

class ParsingTest {
    @Test fun `SGT needs all three checks, as the app's seeker-verify does`() {
        fun mint(auth: String?, mpAuth: String?, mpAddr: String?, group: String?): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val b = java.nio.ByteBuffer.allocate(166).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            b.putInt(0, if (auth != null) 1 else 0); auth?.let { b.position(4); b.put(Base58.decode(it)) }; b.put(165, 1); out.write(b.array())
            fun ext(type: Int, a: String, c: String) { val e = java.nio.ByteBuffer.allocate(68).order(java.nio.ByteOrder.LITTLE_ENDIAN); e.putShort(type.toShort()); e.putShort(64); e.put(Base58.decode(a)); e.put(Base58.decode(c)); out.write(e.array()) }
            if (mpAuth != null && mpAddr != null) ext(18, mpAuth, mpAddr)
            if (group != null) ext(23, PAYER, group)
            return out.toByteArray()
        }
        val A = Sgt.MINT_AUTHORITY; val M = Sgt.METADATA_ADDRESS
        assertTrue(Sgt.isValid(mint(A, A, M, M)))
        assertFalse(Sgt.isValid(mint(A, null, null, null)))          // authority alone was enough before: not any more
        assertFalse(Sgt.isValid(mint(A, A, M, null)))
        assertFalse(Sgt.isValid(mint(A, A, PAYER, M)))
        assertFalse(Sgt.isValid(mint(PAYER, A, M, M)))
        assertFalse(Sgt.isValid(ByteArray(10)))
    }

    @Test fun `malformed binary XML is refused before it allocates`() {
        // A string pool claiming a 2^30-char UTF-16 string: must throw a plain exception, not allocate 2 GB.
        val b = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putShort(0, 3); b.putShort(2, 8); b.putInt(4, 64)                      // XML header
        b.putShort(8, 1); b.putShort(10, 28); b.putInt(12, 56)                  // string pool chunk
        b.putInt(16, 1); b.putInt(20, 0); b.putInt(24, 0); b.putInt(28, 32); b.putInt(32, 0)   // count 1, flags UTF-16, strings start at chunk+32
        b.putInt(36, 0)                                                         // offset of string 0
        b.putShort(40, 0xC000.toShort()); b.putShort(42, 0)                      // length 0x40000000 chars
        assertFailsWith<IllegalArgumentException> { Apk.metaData(b.array()) }
        assertEquals(emptyMap(), Apk.metaData(ByteArray(8)))                    // too short: nothing, no crash
        assertEquals(emptyMap(), Apk.metaData(ByteArray(200) { 0x7f }))         // garbage: nothing, no crash
    }

    @Test fun `webhook targets inside private and carrier networks are refused`() {
        assertTrue(Hooks.isPrivate(java.net.InetAddress.getByName("100.64.0.1")))
        assertTrue(Hooks.isPrivate(java.net.InetAddress.getByName("100.127.255.254")))
        assertFalse(Hooks.isPrivate(java.net.InetAddress.getByName("100.128.0.1")))
        assertTrue(Hooks.isPrivate(java.net.InetAddress.getByName("fd12::1")))
        assertFalse(Hooks.isPrivate(java.net.InetAddress.getByName("2606:4700::1111")))
    }
}

class OutboxTest {
    @Test fun `events queue in the outbox and a worker delivers with retries`() {
        val db = tmpDb(); val billing = Billing(db, { null }, { false })
        val now = 1_000_000L
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('us_a', 'w', 10, ?, 0)", now + 3600)
        var fail = true; val sent = ArrayList<String>()
        val hooks = Hooks(db, billing) { _, body, _ -> sent += body; if (fail) 500 else 200 }
        hooks.create("us_a", "http://localhost:9999/hook", listOf("review"), listOf("com.a.b"))
        val review = com.undersigned.protocol.LedgerEntry(sigOf('n'), PAYER, now, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.FAST), 42, null))
        assertEquals(1, hooks.dispatch(listOf(review), emptyList(), emptyList(), now))
        assertEquals(1, hooks.deliverDue(now)); assertEquals(1, sent.size)                          // first try failed: kept, backed off
        assertEquals(1, db.query("SELECT count(*) FROM hook_outbox") { it.getInt(1) }.first())
        assertEquals(0, hooks.deliverDue(now + 1))                                                    // not due yet
        fail = false
        assertEquals(1, hooks.deliverDue(now + 60)); assertEquals(2, sent.size)                       // second try succeeds
        assertEquals(0, db.query("SELECT count(*) FROM hook_outbox") { it.getInt(1) }.first())
        assertEquals(listOf(500, 200), db.query("SELECT status FROM hook_log ORDER BY at") { it.getInt(1) })   // every attempt is logged
    }

    @Test fun `a receiver that never answers is tried seven times over about a day, then dropped`() {
        val db = tmpDb(); val billing = Billing(db, { null }, { false })
        val now = 1_000_000L
        db.exec("INSERT INTO passes (key, wallet, requests_left, ends_at, created) VALUES ('us_a', 'w', 10, ?, 0)", now + 999_999)
        var tries = 0
        val hooks = Hooks(db, billing) { _, _, _ -> tries++; throw java.net.ConnectException("refused") }
        hooks.create("us_a", "http://localhost:9999/hook", listOf("review"), listOf("com.a.b"))
        hooks.dispatch(listOf(com.undersigned.protocol.LedgerEntry(sigOf('n'), PAYER, now, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.FAST), 42, null))), emptyList(), emptyList(), now)
        var t = now
        hooks.deliverDue(t)
        for (d in Hooks.RETRY_DELAYS) { t += d; hooks.deliverDue(t) }
        assertEquals(1 + Hooks.RETRY_DELAYS.size, tries)
        assertTrue(t - now in 80_000L..90_000L, "about a day: ${t - now}")
        assertEquals(0, db.query("SELECT count(*) FROM hook_outbox") { it.getInt(1) }.first())
    }
}
