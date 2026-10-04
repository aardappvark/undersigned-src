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
        assertEquals(setOf(r1), Reputation.hiddenByConsensus(entries, facts = facts))
        assertTrue(Ranking.score(entries, facts = facts).isEmpty())
        val st = Reputation.standing(entries, facts = facts)
        assertEquals(1, st["a"]!!.flagsUpheld); assertEquals(60, st["a"]!!.credibility)
        assertEquals(1, st["spammer"]!!.recordsHidden); assertEquals(0, st["spammer"]!!.credibility)
        // one flag alone is not consensus
        assertTrue(Reputation.hiddenByConsensus(entries.take(2), facts = facts).isEmpty())
    }

    @Test fun `responsiveness counts reviewers, not records`() {
        val r1 = "1" + "A".repeat(86); val r2 = "3" + "A".repeat(86); val p = "2" + "A".repeat(86); val own = "4" + "A".repeat(86)
        val entries = listOf(
            LedgerEntry(r1, "u", 0, ReviewMemo.Review("com.x", 3, listOf(ReviewMemo.Tag.SLOW), 1, null)),
            LedgerEntry(r2, "u", 100 * 3600, ReviewMemo.Review("com.x", 4, listOf(ReviewMemo.Tag.FAST), 2, null)),   // the same reviewer edits
            LedgerEntry(own, "pub", 50, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS), 2, null)),         // the publisher's own review
        )
        val none = Reputation.responsiveness(entries, "com.x", setOf("pub"))
        assertEquals(0, none.answered); assertEquals(1, none.total)                 // was "0 of 2" (and 3 with the publisher's own)
        // A reply to the earlier review still answers that reviewer; the gap runs from the review that was answered.
        val answered = Reputation.responsiveness(entries + LedgerEntry(p, "pub", 5 * 3600, ReviewMemo.Reply(r1, "on it")), "com.x", setOf("pub"))
        assertEquals(1, answered.answered); assertEquals(1, answered.total); assertEquals(5.0, answered.medianHours!!, 1e-9)
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
        assertEquals(1.0, Reputation.helpfulWeights(entries, facts = facts)[r1])
        assertTrue(Reputation.hiddenByConsensus(entries, facts = facts).isEmpty(), "two flags no longer hide a disputed record")
        assertEquals(setOf(r1), Reputation.hiddenByConsensus(entries.filterNot { it.memo is ReviewMemo.Dispute }, facts = facts))
        val f = Similarity.findings(entries)
        assertTrue(f[r1]!!.copiedAcrossApps && f[r2]!!.copiedAcrossApps)
        assertTrue(Similarity.similarToExisting("the wallet connection fails every time i open the app after an update!!", entries))
        assertTrue(!Similarity.similarToExisting("Works great", entries))
        assertTrue(Ranking.score(entries, facts = facts).size == 2, "similarity never removes a review from ranking")
    }

    // The case the other hiding tests cannot reach: they use 1.0-weight wallets, so weight and head-count move
    // together. A wallet at the 3x staking cap clears HIDE_WEIGHT on its own; only MIN_FLAGGERS stops it. This is
    // the shape exercised on mainnet 21 Sep 2026 (3uzMbkkk…: vitalik.skr, 3.0x, flagging the publisher's review),
    // which on device showed a "3.0 flag weight" pill and left the review visible.
    @Test fun `one max-weight wallet cannot hide a record alone, and a dispute needs three heads not just weight`() {
        val r = "1" + "A".repeat(86)
        val review = LedgerEntry(r, "author", 10, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS), 1, null))
        val heavy = { _: String -> SignerFacts(true, 1_000_000.0) }
        assertEquals(3.0, Ranking.weight(heavy("any")), 1e-9, "the fixture must actually sit at the cap")
        val whale = LedgerEntry("2" + "A".repeat(86), "whale", 20, ReviewMemo.Flag(r, ReviewMemo.FlagReason.OFF_TOPIC))
        assertEquals(3.0, Reputation.flagWeights(listOf(review, whale), facts = heavy)[r]!!, 1e-9)
        assertTrue(Reputation.flagWeights(listOf(review, whale), facts = heavy)[r]!! >= Reputation.HIDE_WEIGHT, "weight alone would hide it")
        assertTrue(Reputation.hiddenByConsensus(listOf(review, whale), facts = heavy).isEmpty(), "one wallet is not consensus")

        val second = LedgerEntry("3" + "A".repeat(86), "second", 21, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, whale, second), facts = heavy))

        // Disputed: weight 6.0 clears the doubled bar (4.0), but two heads are below DISPUTED_MIN_FLAGGERS.
        val dispute = LedgerEntry("4" + "A".repeat(86), "author", 30, ReviewMemo.Dispute(r, "Honest review."))
        assertTrue(Reputation.hiddenByConsensus(listOf(review, whale, second, dispute), facts = heavy).isEmpty(), "two heavy wallets cannot outvote a dispute")
        val third = LedgerEntry("5" + "A".repeat(86), "third", 31, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, whale, second, dispute, third), facts = heavy))

        // A flagger who also marks it "looks genuine" cancels themselves out: net zero heads, net zero weight.
        val selfClear = LedgerEntry("6" + "A".repeat(86), "whale", 40, ReviewMemo.Clear(r))
        assertTrue(Reputation.flagWeights(listOf(review, whale, selfClear), facts = heavy)[r] == null)
    }

    // Found on device 24 Sep 2026: "Update my review" pre-fills your own text, and both the compose-time nudge and the
    // public label treated your earlier version of the SAME review as someone else's note.
    @Test fun `revising your own review is not a copy, reusing it on another app still is`() {
        val note = "Bulk uninstall and reinstall from one screen, great concept and I will test further"
        val v1 = LedgerEntry("1" + "A".repeat(86), "alice", 10, ReviewMemo.Review("com.x", 4, listOf(ReviewMemo.Tag.WORKS), 1, note))
        val v2 = LedgerEntry("2" + "A".repeat(86), "alice", 20, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS), 2, note + " now"))
        assertTrue(Similarity.findings(listOf(v1, v2)).isEmpty(), "a revision must not be labelled similar to its own earlier version")
        assertTrue(!Similarity.similarToExisting(note, listOf(v1), author = "alice", packageName = "com.x"), "no nudge when updating your own review")

        // The same author pasting that note onto a DIFFERENT app is still a copy, labelled and nudged.
        val other = LedgerEntry("3" + "A".repeat(86), "alice", 30, ReviewMemo.Review("com.y", 4, listOf(ReviewMemo.Tag.WORKS), 1, note))
        assertTrue(Similarity.findings(listOf(v1, other))[other.signature]!!.copiedAcrossApps)
        assertTrue(Similarity.similarToExisting(note, listOf(v1), author = "alice", packageName = "com.y"))
        // And someone else writing it about the same app is still similar.
        val bob = LedgerEntry("4" + "A".repeat(86), "bob", 40, ReviewMemo.Review("com.x", 4, listOf(ReviewMemo.Tag.WORKS), 1, note))
        assertTrue(Similarity.findings(listOf(v1, bob)).containsKey(bob.signature))
        assertTrue(Similarity.similarToExisting(note, listOf(v1), author = "bob", packageName = "com.x"))
    }

    @Test fun `flags can be withdrawn, clears offset flags, publisher flags on their own app are ignored`() {
        val r = "1" + "A".repeat(86)
        val review = LedgerEntry(r, "u", 10, ReviewMemo.Review("com.x", 1, listOf(ReviewMemo.Tag.CRASHES), 1, null))
        val flagA = LedgerEntry("2" + "A".repeat(86), "a", 20, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        val flagB = LedgerEntry("3" + "A".repeat(86), "b", 21, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        val facts = { _: String -> SignerFacts(true, 0.0) }
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, flagA, flagB), facts = facts))
        // a withdraws → 1.0 net → visible again
        val unflag = LedgerEntry("4" + "A".repeat(86), "a", 30, ReviewMemo.Unflag(r))
        assertTrue(Reputation.hiddenByConsensus(listOf(review, flagA, flagB, unflag), facts = facts).isEmpty())
        // a re-flags later (latest wins) → hidden; then c clears → 1.0 net → visible
        val reflag = LedgerEntry("5" + "A".repeat(86), "a", 40, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, flagA, flagB, unflag, reflag), facts = facts))
        val clear = LedgerEntry("6" + "A".repeat(86), "c", 50, ReviewMemo.Clear(r))
        assertTrue(Reputation.hiddenByConsensus(listOf(review, flagA, flagB, unflag, reflag, clear), facts = facts).isEmpty())
        // the verified publisher of com.x flagging a 1-star review of com.x counts for nothing
        val pubFlag = LedgerEntry("7" + "A".repeat(86), "pub", 60, ReviewMemo.Flag(r, ReviewMemo.FlagReason.MISLEADING))
        assertTrue(Reputation.hiddenByConsensus(listOf(review, flagB, pubFlag), mapOf("com.x" to setOf("pub")), facts = facts).isEmpty())
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, flagB, pubFlag), facts = facts))
        // vote withdrawal
        val up = LedgerEntry("8" + "A".repeat(86), "d", 70, ReviewMemo.Upvote(r)); val unv = LedgerEntry("9" + "A".repeat(86), "d", 71, ReviewMemo.Unvote(r))
        assertEquals(1.0, Reputation.helpfulWeights(listOf(review, up), facts = facts)[r]); assertNull(Reputation.helpfulWeights(listOf(review, up, unv), facts = facts)[r])
        assertEquals(ReviewMemo.Clear(r), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Clear(r))))
    }

    @Test fun `a verified publisher's review of their own app does not score`() {
        val own = "1" + "A".repeat(86); val other = "2" + "A".repeat(86)
        val entries = listOf(
            LedgerEntry(own, "pub", 10, ReviewMemo.Review("com.x", 5, listOf(ReviewMemo.Tag.WORKS), 1, null)),
            LedgerEntry(other, "user", 11, ReviewMemo.Review("com.x", 3, listOf(ReviewMemo.Tag.SLOW), 1, null)),
        )
        val facts = { _: String -> SignerFacts(true, 0.0) }
        val vp = mapOf("com.x" to setOf("pub"))
        val without = Ranking.score(entries, facts = facts).single()
        assertEquals(2, without.countedReviews); assertEquals(4.0, without.weightedMean, 1e-9)
        val with = Ranking.score(entries, vp, facts).single()
        assertEquals(1, with.countedReviews, "the publisher's own review is excluded")
        assertEquals(3.0, with.weightedMean, 1e-9)
        assertEquals(0, Reputation.standing(entries, vp, facts)["pub"]!!.countedReviews)
        assertEquals(1, Reputation.byVersion(entries, "com.x", vp, facts).single().count)
        // a publisher reviewing a DIFFERENT app still counts
        assertTrue(Ranking.isSelfPublished(entries[0], vp))
        assertTrue(!Ranking.isSelfPublished(entries[0], mapOf("com.y" to setOf("pub"))))
    }

    @Test fun `one maximum-stake wallet cannot hide a record alone`() {
        val r = "1" + "A".repeat(86)
        val review = LedgerEntry(r, "author", 10, ReviewMemo.Review("com.x", 2, listOf(ReviewMemo.Tag.CRASHES), 1, null))
        val whaleFlag = LedgerEntry("2" + "A".repeat(86), "whale", 20, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        val facts = { w: String -> SignerFacts(true, if (w == "whale") 1_000_000.0 else 0.0) }
        assertEquals(3.0, Reputation.flagWeights(listOf(review, whaleFlag), facts = facts)[r])   // weight is over the bar...
        assertTrue(Reputation.hiddenByConsensus(listOf(review, whaleFlag), facts = facts).isEmpty(), "...but one wallet is not consensus")
        val second = LedgerEntry("3" + "A".repeat(86), "b", 21, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, whaleFlag, second), facts = facts))
        // after a dispute, two flaggers are not enough even at weight 4.0
        val dispute = LedgerEntry("4" + "A".repeat(86), "author", 30, ReviewMemo.Dispute(r, "honest review"))
        assertTrue(Reputation.hiddenByConsensus(listOf(review, whaleFlag, second, dispute), facts = facts).isEmpty())
        val third = LedgerEntry("5" + "A".repeat(86), "c", 31, ReviewMemo.Flag(r, ReviewMemo.FlagReason.SPAM))
        assertEquals(setOf(r), Reputation.hiddenByConsensus(listOf(review, whaleFlag, second, dispute, third), facts = facts))
        // an unattested flagger adds no person
        val unattested = { w: String -> SignerFacts(w != "b", 0.0) }
        assertTrue(Reputation.hiddenByConsensus(listOf(review, whaleFlag, second), facts = unattested).isEmpty())
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
        val score = Ranking.score(entries, facts = facts).single()
        assertEquals(2, score.countedReviews)
        // prior 3.0×5 + (2 + 5) = 22 / 7
        assertEquals(22.0 / 7.0, score.weightedRating, 1e-9)
        // now B revokes their own review
        val withRvk = entries + LedgerEntry("6" + "A".repeat(86), "walletB", 500, ReviewMemo.Revocation(s3))
        assertEquals(1, Ranking.score(withRvk, facts = facts).single().countedReviews)
    }

    @Test fun `a licence bought under the old shape is still read`() {
        assertEquals(ReviewMemo.Pro(12), ReviewMemo.decode("SRL1|pro|12"))
        assertNull(ReviewMemo.decode("SRL1|pro|0"))
        assertNull(ReviewMemo.decode("SRL1|pro|121"))
        assertNull(ReviewMemo.decode("SRL1|pro|12|extra"))
        assertNull(ReviewMemo.decode("SRL1|pro|twelve"))
    }

    @Test fun `every record kind survives the round trip the on-device cache makes`() {
        // LedgerCache stores each entry as its wire text and reads it back. A kind the codec refuses to write
        // is a kind the cache drops — and, before the guard moved, a crash on launch for anyone holding one.
        val sig = "5XTu3S73JVaDV2JQRJUmRWaja3geEUKeegnN7eHHKvEZa1p6jG1LnBWnJ1Np47ZbCeBztMHb8W2GcbVDc3pcj38B"
        val all = listOf(
            ReviewMemo.Review("com.example.app", 4, listOf(ReviewMemo.Tag.WORKS), 7L, "note"),
            ReviewMemo.Revocation(sig), ReviewMemo.Reply(sig, "answer"), ReviewMemo.Claim("com.example.app"),
            ReviewMemo.Flag(sig, ReviewMemo.FlagReason.SPAM), ReviewMemo.Upvote(sig), ReviewMemo.Unvote(sig),
            ReviewMemo.Unflag(sig), ReviewMemo.Clear(sig), ReviewMemo.Dispute(sig, "why"),
            ReviewMemo.Pro(12), ReviewMemo.Share("a".repeat(32)), ReviewMemo.Unshare("b".repeat(32)),
        )
        all.forEach { assertEquals(it, ReviewMemo.decode(ReviewMemo.encode(it)), "round trip failed for $it") }
    }

    @Test fun `a pro record moves no score and no standing`() {
        val f = { _: String -> SignerFacts(true, 0.0) }
        val review = LedgerEntry("sig1", "walletA", 1000L, ReviewMemo.Review("com.example.app", 5, listOf(ReviewMemo.Tag.WORKS), 1L, null))
        val licence = LedgerEntry("sig2", "walletB", 1001L, ReviewMemo.Pro(12))
        val with = Ranking.score(listOf(review, licence), facts = f).single()
        val without = Ranking.score(listOf(review), facts = f).single()
        assertEquals(without, with)
        assertEquals(Reputation.standing(listOf(review), emptyMap(), f).keys, Reputation.standing(listOf(review, licence), emptyMap(), f).keys)
    }

    @Test fun `share records round-trip and only well-formed tags decode`() {
        val tag = ProSharing.tag("walletA", "walletB")
        assertEquals(32, tag.length)
        assertEquals(ReviewMemo.Share(tag), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Share(tag))))
        assertEquals(ReviewMemo.Unshare(tag), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Unshare(tag))))
        assertNull(ReviewMemo.decode("SRL1|shr|nothex"))
        assertNull(ReviewMemo.decode("SRL1|shr|" + "a".repeat(31)))
        assertTrue(ProSharing.tag("walletA", "walletB") != ProSharing.tag("walletB", "walletA"))
    }

    @Test fun `seats replay in signing order, newest displaces oldest, revoke frees`() {
        fun g(t: String, at: Long) = ProSharing.Record(t, at, false)
        fun x(t: String, at: Long) = ProSharing.Record(t, at, true)
        assertEquals(setOf("a", "b"), ProSharing.activeSeats(listOf(g("a", 1), g("b", 2))))
        assertEquals("a", ProSharing.wouldDisplace(listOf(g("a", 1), g("b", 2))))
        assertEquals(setOf("b", "c"), ProSharing.activeSeats(listOf(g("a", 1), g("b", 2), g("c", 3))))
        assertEquals(setOf("a", "b"), ProSharing.activeSeats(listOf(g("a", 1), g("b", 2), g("a", 3))))   // already seated: nothing moves
        assertEquals(setOf("b"), ProSharing.activeSeats(listOf(g("a", 1), g("b", 2), x("a", 3))))
        assertEquals(setOf("c", "a"), ProSharing.activeSeats(listOf(g("a", 1), g("b", 2), g("c", 3), g("a", 4))))   // a displaced wallet re-claims, and the longest-seated (b) gives way
        assertEquals(emptySet<String>(), ProSharing.activeSeats(listOf(g("a", 5), x("a", 5))))   // same instant: not held
    }
}
