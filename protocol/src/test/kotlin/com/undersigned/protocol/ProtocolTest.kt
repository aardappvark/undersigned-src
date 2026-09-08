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

    @Test fun `revocation round-trips, batched ids too`() {
        assertEquals(ReviewMemo.Revocation(sig), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Revocation(sig))))
        val batched = ReviewMemo.recordId(sig, 3)
        assertEquals("$sig:3", batched)
        assertEquals(ReviewMemo.Revocation(batched), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Revocation(batched))))
        assertNull(ReviewMemo.decode("SRL1|rvk|$sig:0"))
        assertEquals(sig, ReviewMemo.recordId(sig, 0))
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

    @Test fun `claim round-trips and is ignored by ranking`() {
        val c = ReviewMemo.Claim("com.example.app")
        assertEquals(c, ReviewMemo.decode(ReviewMemo.encode(c)))
        assertNull(ReviewMemo.decode("SRL1|clm|not a package"))
        val entries = listOf(rev("1" + "A".repeat(86), "w", 1, "com.x", 2), LedgerEntry("2" + "A".repeat(86), "pub", 2, c))
        assertEquals(1, Ranking.score(entries) { SignerFacts(true, 0.0) }.single().countedReviews)
    }

    @Test fun `standing counts replies received and agreement`() {
        val a = "1" + "A".repeat(86); val b = "2" + "A".repeat(86); val c = "3" + "A".repeat(86)
        val entries = listOf(
            LedgerEntry(a, "alice", 10, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS, ReviewMemo.Tag.FAST), 1, null)),
            LedgerEntry(b, "bob", 20, ReviewMemo.Review("com.x", 4, listOf(ReviewMemo.Tag.WORKS), 1, null)),
            LedgerEntry(c, "bob", 30, ReviewMemo.Reply(a, "agree")),
        )
        val st = Reputation.standing(entries) { SignerFacts(true, 0.0) }
        assertEquals(1, st["alice"]!!.repliesReceived); assertEquals(0.5, st["alice"]!!.agreement); assertEquals(1, st["bob"]!!.repliesGiven)
        val v = Reputation.byVersion(entries, "com.x") { SignerFacts(true, 0.0) }
        assertEquals(1, v.size); assertEquals(4.5, v[0].mean, 1e-9)
    }

    @Test fun `flags reach consensus, hide the record from ranking, and feed credibility`() {
        val r1 = "1" + "A".repeat(86); val f1 = "2" + "A".repeat(86); val f2 = "3" + "A".repeat(86); val f3 = "4" + "A".repeat(86)
        val flag = ReviewMemo.Flag(r1, ReviewMemo.FlagReason.SPAM)
        assertEquals(flag, ReviewMemo.decode(ReviewMemo.encode(flag)))
        val entries = listOf(
            LedgerEntry(r1, "spammer", 10, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS), 1, null)),
            LedgerEntry(f1, "a", 20, flag), LedgerEntry(f2, "b", 21, flag), LedgerEntry(f3, "spammer", 22, flag),   // self-flag ignored
        )
        val facts = { _: String -> SignerFacts(true, 0.0) }
        assertEquals(setOf(r1), Reputation.hiddenByConsensus(entries, facts))
        assertTrue(Ranking.score(entries, facts).isEmpty())
        val st = Reputation.standing(entries, facts)
        assertEquals(1, st["a"]!!.flagsUpheld); assertEquals(60, st["a"]!!.credibility)
        assertEquals(1, st["spammer"]!!.recordsHidden); assertEquals(0, st["spammer"]!!.credibility)
        // one flag alone is not consensus
        assertTrue(Reputation.hiddenByConsensus(entries.take(2), facts).isEmpty())
    }

    @Test fun `publisher responsiveness buckets by median hours`() {
        val r = "1" + "A".repeat(86); val p = "2" + "A".repeat(86)
        val entries = listOf(
            LedgerEntry(r, "u", 0, ReviewMemo.Review("com.x", 3, listOf(ReviewMemo.Tag.SLOW), 1, null)),
            LedgerEntry(p, "pub", 5 * 3600, ReviewMemo.Reply(r, "on it")),
        )
        val resp = Reputation.responsiveness(entries, "com.x", setOf("pub"))
        assertEquals(1, resp.answered); assertEquals("hours", resp.bucket); assertEquals(5.0, resp.medianHours!!, 1e-9)
        assertEquals("never", Reputation.responsiveness(entries, "com.x", emptySet()).bucket)
    }

    @Test fun `upvotes weigh once per wallet, disputes double the hiding bar, similarity labels but never hides`() {
        val r1 = "1" + "A".repeat(86); val r2 = "2" + "A".repeat(86)
        val up = ReviewMemo.Upvote(r1); assertEquals(up, ReviewMemo.decode(ReviewMemo.encode(up)))
        val dsp = ReviewMemo.Dispute(r1, "This is my honest review, see my other 20."); assertEquals(dsp, ReviewMemo.decode(ReviewMemo.encode(dsp)))
        val note = "The wallet connection fails every time I open the app after an update"
        val entries = listOf(
            LedgerEntry(r1, "alice", 10, ReviewMemo.Review("com.x", 2, listOf(ReviewMemo.Tag.WALLET_FAILS), 1, note)),
            LedgerEntry(r2, "alice", 11, ReviewMemo.Review("com.y", 2, listOf(ReviewMemo.Tag.WALLET_FAILS), 1, note)),
            LedgerEntry("3" + "A".repeat(86), "bob", 20, ReviewMemo.Upvote(r1)),
            LedgerEntry("4" + "A".repeat(86), "bob", 21, ReviewMemo.Upvote(r1)),      // second vote by the same wallet does not count twice
            LedgerEntry("5" + "A".repeat(86), "alice", 22, ReviewMemo.Upvote(r1)),    // self-vote ignored
            LedgerEntry("6" + "A".repeat(86), "carol", 30, ReviewMemo.Flag(r1, ReviewMemo.FlagReason.SPAM)),
            LedgerEntry("7" + "A".repeat(86), "dave", 31, ReviewMemo.Flag(r1, ReviewMemo.FlagReason.SPAM)),
            LedgerEntry("8" + "A".repeat(86), "alice", 40, dsp),
        )
        val facts = { _: String -> SignerFacts(true, 0.0) }
        assertEquals(1.0, Reputation.helpfulWeights(entries, facts)[r1])
        assertTrue(Reputation.hiddenByConsensus(entries, facts).isEmpty(), "two flags no longer hide a disputed record")
        assertEquals(setOf(r1), Reputation.hiddenByConsensus(entries.filterNot { it.memo is ReviewMemo.Dispute }, facts))
        val f = Similarity.findings(entries)
        assertTrue(f[r1]!!.copiedAcrossApps && f[r2]!!.copiedAcrossApps)
        assertTrue(Similarity.similarToExisting("the wallet connection fails every time i open the app after an update!!", entries))
        assertTrue(!Similarity.similarToExisting("Works great", entries))
        assertTrue(Ranking.score(entries, facts).size == 2, "similarity never removes a review from ranking")
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
