package com.undersigned.protocol

import com.undersigned.protocol.ReviewMemo.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ProtocolTest {
    private val sig = "5" + "A".repeat(86)

    @Test fun `review round-trips with unicode text`() {
        val r = ReviewMemo.Review("com.example.app", 4, listOf(Tag.WORKS, Tag.SEEKER_NATIVE), 12, "Fast, no ads. Cấu hình OK ✓")
        val wire = ReviewMemo.encode(r)
        assertTrue(wire.startsWith("SRL1|rev|com.example.app|4|works,seeker-native|12|"))
        assertEquals(r, ReviewMemo.decode(wire))
    }

    @Test fun `review without text round-trips`() {
        val r = ReviewMemo.Review("com.example.app", 1, listOf(Tag.SUSPECTED_SCAM), 3, null)
        assertEquals(r, ReviewMemo.decode(ReviewMemo.encode(r)))
    }

    @Test fun `revocation round-trips`() {
        assertEquals(ReviewMemo.Revocation(sig), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Revocation(sig))))
    }

    @Test fun `reply round-trips and is ignored by ranking`() {
        val r = ReviewMemo.Reply(sig, "Thanks — fixed in v13 ✓")
        assertEquals(r, ReviewMemo.decode(ReviewMemo.encode(r)))
        assertNull(ReviewMemo.decode("SRL1|rep|$sig|"))
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(ReviewMemo.Reply(sig, "  ")) }
        val entries = listOf(rev("1" + "A".repeat(86), "w", 1, "com.x", 4), LedgerEntry("2" + "A".repeat(86), "z", 2, r))
        val s = Ranking.score(entries) { SignerFacts(true, 0.0) }.single()
        assertEquals(1, s.countedReviews); assertEquals(4.0, s.weightedMean, 1e-9)
    }

    @Test fun `garbage and hostile input decode to null, never throw`() {
        listOf("", "SRL1", "SRL1|rev", "SRL1|rev|com.a|6|works|1|", "SRL1|rev|com.a|3|nonsense|1|",
               "SRL1|rev|com.a|3|works,works|1|", "SRL1|rev|../etc|3|works|1|", "SRL2|rev|com.a|3|works|1|",
               "SRL1|rvk|notasig", "SRL1|rev|com.a|3|works|1|" + "A".repeat(600), "SRL1|rev|com.a|3|works|0|")
            .forEach { assertNull(ReviewMemo.decode(it), "should reject: $it") }
    }

    @Test fun `encoder enforces limits`() {
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(ReviewMemo.Review("com.a", 0, listOf(Tag.WORKS), 1, null)) }
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(ReviewMemo.Review("com.a", 3, listOf(), 1, null)) }
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(ReviewMemo.Review("com.a", 3, listOf(Tag.WORKS), 1, "x".repeat(141))) }
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(ReviewMemo.Review("com.a", 3, listOf(Tag.WORKS), 1, "a|b")) }
    }

    @Test fun `max-size review fits the memo cap, oversize text is refused both ways`() {
        val longPkg = "com." + "a".repeat(94) + ".b"                       // 100 chars, the cap
        val worstTags = listOf(Tag.EXCESS_PERMISSIONS, Tag.MISLEADING_LISTING, Tag.WALLET_FAILS)
        val ascii = ReviewMemo.Review(longPkg, 5, worstTags, Long.MAX_VALUE, "x".repeat(140))
        assertTrue(ReviewMemo.encode(ascii).toByteArray().size <= ReviewMemo.MAX_MEMO_BYTES)
        val cjk = ReviewMemo.Review(longPkg, 5, worstTags, Long.MAX_VALUE, "✓".repeat(93))   // 279 bytes
        assertTrue(ReviewMemo.encode(cjk).toByteArray().size <= ReviewMemo.MAX_MEMO_BYTES)
        assertEquals(cjk, ReviewMemo.decode(ReviewMemo.encode(cjk)))
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(cjk.copy(text = "✓".repeat(94))) } // 282 bytes
        assertFailsWith<IllegalArgumentException> { ReviewMemo.encode(ascii.copy(packageName = longPkg + "c")) }
        // a hand-crafted memo carrying 140 chars of 4-byte emoji must be rejected by the decoder, not just the encoder
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("😀".repeat(140).toByteArray())
        assertNull(ReviewMemo.decode("SRL1|rev|com.a|3|works|1|$b64"))
    }

    private fun rev(sig: String, signer: String, t: Long, pkg: String, rating: Int) =
        LedgerEntry(sig, signer, t, ReviewMemo.Review(pkg, rating, listOf(Tag.WORKS), 1, null))

    @Test fun `no genesis token means zero weight, whale weight is capped`() {
        assertEquals(0.0, Ranking.weight(SignerFacts(false, 1_000_000.0)))
        assertEquals(1.0, Ranking.weight(SignerFacts(true, 0.0)))
        assertEquals(Ranking.MAX_WEIGHT, Ranking.weight(SignerFacts(true, 1_000_000.0)))
        assertTrue(Ranking.weight(SignerFacts(true, 1_000.0)) in 1.6..1.8)
    }

    @Test fun `revocation only counts from the same wallet, newest review per wallet wins, sybils ignored`() {
        val s1 = "1" + "A".repeat(86); val s2 = "2" + "A".repeat(86); val s3 = "3" + "A".repeat(86); val s4 = "4" + "A".repeat(86)
        val entries = listOf(
            rev(s1, "walletA", 100, "com.x", 5),
            rev(s2, "walletA", 200, "com.x", 2),                       // newer, replaces s1
            rev(s3, "walletB", 150, "com.x", 5),
            LedgerEntry(s4, "walletC", 300, ReviewMemo.Revocation(s3)), // C cannot revoke B's review
            rev("5" + "A".repeat(86), "sybil", 400, "com.x", 5),      // no genesis token
        )
        val facts = { s: String -> SignerFacts(hasGenesisToken = s != "sybil", stakedSkr = 0.0) }
        val score = Ranking.score(entries, facts).single()
        assertEquals(2, score.countedReviews)
        // prior 3.0×5 + (2 + 5) = 22 / 7
        assertEquals(22.0 / 7.0, score.weightedRating, 1e-9)
        // now B revokes their own review
        val withRvk = entries + LedgerEntry("6" + "A".repeat(86), "walletB", 500, ReviewMemo.Revocation(s3))
        assertEquals(1, Ranking.score(withRvk, facts).single().countedReviews)
    }
}
