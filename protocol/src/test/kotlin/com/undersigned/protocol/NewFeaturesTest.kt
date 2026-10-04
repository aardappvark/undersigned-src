package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewBurstsTest {
    private val D = ReviewBursts.DAY
    private val t0 = 1_760_000_000L
    private fun steady(days: Int, everyDays: Int, rating: Int = 5, from: Long = t0) = (0 until days step everyDays).map { ReviewBursts.Point(from + it * D, rating) }

    @Test fun steadyPaceIsNotABurst() {
        // One 5★ a day for 60 days: every 3-day window holds 3, the pace predicts 3.
        assertTrue(ReviewBursts.find(steady(60, 1)).bursts.isEmpty())
    }

    @Test fun spikeAgainstAQuietBaselineIsFound() {
        val base = steady(40, 7, rating = 4)                        // one 4★ a week
        val spikeAt = t0 + 41 * D
        val spike = (0 until 10).map { ReviewBursts.Point(spikeAt + it * 3600L * 4, 5) }   // ten 5★ in 40 hours
        val f = ReviewBursts.find(base + spike)
        assertEquals(1, f.bursts.size)
        val b = f.bursts.single()
        assertEquals(ReviewBursts.Kind.HIGH, b.kind); assertEquals(10, b.count); assertEquals(spikeAt, b.start)
        assertEquals(spikeAt + 9 * 3600L * 4, b.end)
    }

    @Test fun launchCampaignIsFound() {
        // CrushCoin, 17–18 Mar 2026: the campaign ran in launch week, with no history before it. Quiet month after.
        val launch = (0 until 24).map { ReviewBursts.Point(t0 + it * 1800L, 5) }
        val after = steady(40, 3, rating = 5, from = t0 + 4 * D)
        val b = ReviewBursts.find(launch + after).bursts.single()
        assertEquals(24, b.count); assertEquals(t0, b.start)
    }

    @Test fun noVerdictWithoutEnoughHistory() {
        // Launch week, or a popular app whose read reviews span only days: nothing to measure against.
        val base = steady(5, 1, rating = 4)
        val spike = (0 until 10).map { ReviewBursts.Point(t0 + 6 * D + it * 3600L, 5) }
        assertTrue(ReviewBursts.find(base + spike).bursts.isEmpty())
    }

    @Test fun oneStarBurstsAreFoundToo() {
        val base = steady(30, 3, rating = 4)
        val bomb = (0 until 6).map { ReviewBursts.Point(t0 + 31 * D + it * 600L, 1) }
        val b = ReviewBursts.find(base + bomb).bursts.single()
        assertEquals(ReviewBursts.Kind.LOW, b.kind); assertEquals(6, b.count)
    }

    @Test fun mixedBusyWeekIsNotABurst() {
        val base = steady(30, 7, rating = 4)
        val busy = (0 until 10).map { ReviewBursts.Point(t0 + 31 * D + it * 3600L, if (it % 2 == 0) 5 else 2) }   // 5 fives among 10
        assertTrue(ReviewBursts.find(base + busy).bursts.isEmpty())
    }

    @Test fun thresholdBoundaries() {
        val base = steady(30, 30, rating = 3)                      // quiet: pace predicts the floor of 1
        fun spike(n: Int) = (0 until n).map { ReviewBursts.Point(t0 + 31 * D + it * 60L, 5) }
        assertTrue(ReviewBursts.find(base + spike(ReviewBursts.MIN_COUNT - 1)).bursts.isEmpty())
        assertEquals(ReviewBursts.MIN_COUNT, ReviewBursts.find(base + spike(ReviewBursts.MIN_COUNT)).bursts.single().count)
    }

    @Test fun busyAppNeedsFourTimesItsPace() {
        // Two 5★ a day for 30 days predicts 6 per window: 20 in a window is a burst, 12 is not.
        val base = (0 until 60).map { ReviewBursts.Point(t0 + it * D / 2, 5) }
        val after = t0 + 30 * D + D / 4
        assertTrue(ReviewBursts.find(base + (0 until 12).map { ReviewBursts.Point(after + it * 60L, 5) }).bursts.isEmpty())
        assertEquals(1, ReviewBursts.find(base + (0 until 30).map { ReviewBursts.Point(after + it * 60L, 5) }).bursts.size)
    }

    @Test fun oneCampaignIsOneBurstEvenAcrossWindows() {
        val base = steady(30, 10, rating = 4)
        val campaign = (0 until 20).map { ReviewBursts.Point(t0 + 31 * D + it * D / 3, 5) }   // 20 over ~6.3 days, steady
        val b = ReviewBursts.find(base + campaign).bursts
        // One burst, not two; a campaign's last partial window may fall below the bar and stay out.
        assertEquals(1, b.size); assertTrue(b.single().count >= 18, "${b.single().count}")
    }

    @Test fun twoCampaignsAWeekApartAreTwoBursts() {
        val base = steady(30, 10, rating = 4)
        val first = (0 until 30).map { ReviewBursts.Point(t0 + 31 * D + it * 60L, 5) }   // big enough to predict 3 per window if counted
        val second = (0 until 10).map { ReviewBursts.Point(t0 + 40 * D + it * 60L, 5) }
        // The first must not become the "pace" that hides the second.
        assertEquals(listOf(10, 30), ReviewBursts.find(base + first + second).bursts.map { it.count })
    }

    @Test fun theTrickleAfterACampaignIsNotPartOfIt() {
        val launch = (0 until 24).map { ReviewBursts.Point(t0 + it * 1800L, 5) }                 // day 0
        val trickle = (0 until 25).map { ReviewBursts.Point(t0 + D + it * 2 * D, 5) }            // then one every 2 days
        val b = ReviewBursts.find(launch + trickle).bursts.single()
        // The first window may hold one trickle review; the other 24 stay out.
        assertTrue(b.count <= 25, "${b.count}"); assertTrue(b.end < t0 + 3 * D)
    }

    @Test fun templateTextIsCounted() {
        val base = steady(30, 10, rating = 4)
        val spike = listOf("Great app love it", "great app, LOVE it!", "Great app love it", "ok", "Different words entirely here", "Nice")
            .mapIndexed { i, s -> ReviewBursts.Point(t0 + 31 * D + i * 60L, 5, s) }
        assertEquals(3, ReviewBursts.find(base + spike).bursts.single().sameText)
    }

    @Test fun orderOfInputDoesNotMatter() {
        val base = steady(40, 7, rating = 4); val spike = (0 until 10).map { ReviewBursts.Point(t0 + 41 * D + it * 60L, 5) }
        assertEquals(ReviewBursts.find(base + spike), ReviewBursts.find((base + spike).shuffled(java.util.Random(7))))
    }
}

class UsageTest {
    private val sig = "5".repeat(88)
    private val me = "Wallet1111111111111111111111111111111111111"

    @Test fun bucketsRoundDown() {
        val day = 86_400_000L; val now = 100 * day
        assertEquals(UseBucket.UNDER_DAY, UseBucket.of(now - day + 1, now))
        assertEquals(UseBucket.DAYS, UseBucket.of(now - day, now))
        assertEquals(UseBucket.DAYS, UseBucket.of(now - 6 * day, now))
        assertEquals(UseBucket.WEEKS, UseBucket.of(now - 7 * day, now))
        assertEquals(UseBucket.MONTHS, UseBucket.of(now - 30 * day, now))
        assertEquals(UseBucket.LONG, UseBucket.of(now - 90 * day, now))
        assertEquals(UseBucket.UNDER_DAY, UseBucket.of(now + day, now))   // clock went backwards
    }

    @Test fun wireRoundTripsAndRejectsJunk() {
        val m = ReviewMemo.Use("com.example.app", UseBucket.WEEKS)
        assertEquals("SRL1|use|com.example.app|w1", ReviewMemo.encode(m))
        assertEquals(m, ReviewMemo.decode("SRL1|use|com.example.app|w1"))
        assertNull(ReviewMemo.decode("SRL1|use|com.example.app|w9"))
        assertNull(ReviewMemo.decode("SRL1|use|com.example.app"))
        assertNull(ReviewMemo.decode("SRL1|use|not a package|w1"))
    }

    @Test fun attachesOnlyToTheSameTransactionPackageAndSigner() {
        val review = LedgerEntry(sig, me, 1, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, null))
        val use = LedgerEntry("$sig:1", me, 1, ReviewMemo.Use("com.a.b", UseBucket.MONTHS))
        assertEquals(mapOf(sig to UseBucket.MONTHS), Usage.index(listOf(review, use)))
        // Another app's use record in the same transaction, another signer, or another transaction: ignored.
        assertTrue(Usage.index(listOf(review, use.copy(memo = ReviewMemo.Use("com.other.app", UseBucket.LONG)))).isEmpty())
        assertTrue(Usage.index(listOf(review, use.copy(signer = "Other111111111111111111111111111111111111111"))).isEmpty())
        assertTrue(Usage.index(listOf(review, use.copy(signature = "4".repeat(88) + ":1"))).isEmpty())
    }

    @Test fun oldReadersStillSeeTheReview() {
        // The decoder the shipped app runs rejects the unknown type and keeps going; the review decodes on its own.
        assertTrue(ReviewMemo.decode("SRL1|rev|com.a.b|5|works|3|") is ReviewMemo.Review)
    }
}

class PaymentsTest {
    private val treasury = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
    private fun sig(c: Char) = c.toString().repeat(88)

    @Test fun memoMatching() {
        assertTrue(Payments.memoMatches("[3] UNS", "UNS"))
        assertTrue(Payments.memoMatches("UNS", "UNS"))
        assertTrue(Payments.memoMatches("[11] UNS:order-7", "UNS"))
        assertTrue(Payments.memoMatches("[4] abcd; [3] UNS", "UNS"))
        assertFalse(Payments.memoMatches("[4] UNSX", "UNS"))
        assertFalse(Payments.memoMatches("[3] DRX", "UNS"))
        assertFalse(Payments.memoMatches("[12] SRL1|rev|com.a.b|5|works|3|", "UNS"))
        assertFalse(Payments.memoMatches("[3] uns", "UNS"))   // codes are exact
        assertFalse(Payments.memoMatches(null, "UNS"))
    }

    @Test fun websiteLines() {
        val body = "8oLtrvMnKLxFfG4PSa5ANxW1Ncvy1h2BCYfZ9mRBHT8F\ntreasury: $treasury\nTREASURY=HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD\n# treasury: notanaddress\ntreasury: $treasury extra"
        assertEquals(setOf(treasury, "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD"), Payments.listedTreasuries(body))
        // A bare wallet line (the publisher-claim format) does not name a treasury.
        assertTrue(Payments.listedTreasuries("$treasury\n").isEmpty())
    }

    @Test fun dustBuysNeitherAPayerNorAPill() {
        val dust = Payments.Payment(sig('a'), "PayerA11111111111111111111111111111111111111", 10, 0, mapOf(Payments.USDC_MINT to 1L))
        val real = Payments.Payment(sig('b'), "PayerB11111111111111111111111111111111111111", 20, 0, mapOf(Payments.USDC_MINT to 100_000L))
        val lam = Payments.Payment(sig('c'), "PayerC11111111111111111111111111111111111111", 30, 999_999, emptyMap())
        assertFalse(Payments.meaningful(dust)); assertTrue(Payments.meaningful(real)); assertFalse(Payments.meaningful(lam))
        assertTrue(Payments.meaningful(lam.copy(lamports = 1_000_000)))
        val t = Payments.tally(listOf(dust, real, lam), treasury) { true }
        assertEquals(3, t.payments)                    // every unit still counts in the totals
        assertEquals(1, t.payers); assertEquals(1, t.attestedPayers)
        assertEquals(999_999L, t.lamports)
    }

    @Test fun listingCountsOnlyOncePaid() {
        val unpaid = LedgerEntry(sig('a'), treasury, 10, ReviewMemo.Treasury("com.a.b", "UNS"))
        val paidRec = LedgerEntry(sig('b') + ":1", treasury, 20, ReviewMemo.Treasury("com.a.b", "UNS"))
        val x = LedgerEntry(sig('c'), treasury, 30, ReviewMemo.Untreasury("com.a.b"))
        val relist = LedgerEntry(sig('d'), treasury, 40, ReviewMemo.Treasury("com.a.b", "UN2"))
        val paid: (LedgerEntry) -> Boolean = { it.signature == sig('b') + ":1" }
        assertTrue(Payments.claims(listOf(unpaid), paid).isEmpty())                     // a trs nobody paid for lists nothing
        assertEquals("UNS", Payments.claims(listOf(unpaid, paidRec), paid).single().code)
        assertTrue(Payments.claims(listOf(unpaid, paidRec, x), paid).isEmpty())         // unlisting still wins
        assertEquals("UN2", Payments.claims(listOf(unpaid, paidRec, x, relist), paid).single().code) // relisting is free once paid
        // Another treasury's payment never pays for this one.
        val other = LedgerEntry(sig('e'), "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD", 50, ReviewMemo.Treasury("com.a.b", "HV"))
        assertEquals(setOf(treasury), Payments.claims(listOf(paidRec, other), paid).map { it.treasury }.toSet())
    }

    @Test fun claimsLatestWinsPerTreasuryAndPackage() {
        val a = LedgerEntry(sig('a'), treasury, 10, ReviewMemo.Treasury("com.a.b", "UNS"))
        val x = LedgerEntry(sig('b'), treasury, 20, ReviewMemo.Untreasury("com.a.b"))
        val again = LedgerEntry(sig('c'), treasury, 30, ReviewMemo.Treasury("com.a.b", "UN2"))
        assertEquals(listOf(Payments.Claim("com.a.b", treasury, "UNS", 10, sig('a'))), Payments.claims(listOf(a)))
        assertTrue(Payments.claims(listOf(a, x)).isEmpty())
        assertEquals("UN2", Payments.claims(listOf(again, x, a)).single().code)
        assertNull(ReviewMemo.decode("SRL1|trs|com.a.b|x"))            // code too short
        assertNull(ReviewMemo.decode("SRL1|trs|com.a.b|UN S"))
        assertEquals(ReviewMemo.Treasury("com.a.b", "UNS"), ReviewMemo.decode(ReviewMemo.encode(ReviewMemo.Treasury("com.a.b", "UNS"))))
        assertEquals(ReviewMemo.Untreasury("com.a.b"), ReviewMemo.decode("SRL1|trx|com.a.b"))
    }

    @Test fun codesAndReferences() {
        assertTrue(Payments.validCode("UNS")); assertFalse(Payments.isReference("UNS"))
        assertTrue(Payments.isReference("BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs")); assertTrue(Payments.validCode("BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs"))
        assertFalse(Payments.validCode("0OIl" + "a".repeat(30)))               // not base58, too long for a code
        assertEquals(ReviewMemo.Treasury("com.a.b", "BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs"), ReviewMemo.decode("SRL1|trs|com.a.b|BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs"))
    }

    @Test fun publisherWalletsAreCountedApart() {
        val p = listOf(
            Payments.Payment(sig('a'), "PayerA11111111111111111111111111111111111111", 1_758_000_000L, 60_700_000, emptyMap()),
            Payments.Payment(sig('b'), "OwnerW11111111111111111111111111111111111111", 1_758_000_000L, 60_700_000, emptyMap()))
        val t = Payments.tally(p, treasury, own = setOf("OwnerW11111111111111111111111111111111111111")) { false }
        assertEquals(1, t.payments); assertEquals(1, t.ownPayments); assertEquals(60_700_000L, t.lamports)
        val body = "# publisher\n8oLtrvMnk7bLzjYzWQdo66kJsvkG4CYL1QKoeKbBHT8F\ntreasury: $treasury\nnot an address"
        assertEquals(setOf("8oLtrvMnk7bLzjYzWQdo66kJsvkG4CYL1QKoeKbBHT8F", treasury), Payments.listedWallets(body))
    }

    @Test fun tallyCountsPayersMonthsAndIgnoresSelfAndEmpty() {
        val sep = 1_758_000_000L   // 2025-09-16 UTC
        val oct = 1_760_000_000L   // 2025-10-09 UTC
        val p = listOf(
            Payments.Payment(sig('a'), "PayerA11111111111111111111111111111111111111", sep, 60_700_000, emptyMap()),
            Payments.Payment(sig('b'), "PayerA11111111111111111111111111111111111111", oct, 0, mapOf("SKR" to 276_000_000L)),
            Payments.Payment(sig('c'), "PayerB11111111111111111111111111111111111111", oct, 60_700_000, emptyMap()),
            Payments.Payment(sig('c'), "PayerB11111111111111111111111111111111111111", oct, 60_700_000, emptyMap()),   // duplicate row
            Payments.Payment(sig('d'), treasury, oct, 5_000_000_000, emptyMap()),                                         // treasury topping itself up
            Payments.Payment(sig('e'), "PayerC11111111111111111111111111111111111111", oct, 0, emptyMap()),               // credited nothing
        )
        val t = Payments.tally(p, treasury) { it.startsWith("PayerA") }
        assertEquals(3, t.payments); assertEquals(2, t.payers); assertEquals(1, t.attestedPayers)
        assertEquals(121_400_000L, t.lamports); assertEquals(mapOf("SKR" to 276_000_000L), t.tokens)
        assertEquals(listOf("2025-09", "2025-10"), t.months.map { it.key })
        assertEquals(2, t.months.last().payments)
    }
}

class RatingSnapshotsTest {
    private val D = ReviewBursts.DAY
    private val t0 = 1_760_000_000L
    private fun snap(day: Int, five: Int, one: Int = 0, four: Int = 0) = RatingSnapshots.Snapshot(t0 + day * D, listOf(one, 0, 0, four, five))

    @Test fun dailySnapshotsOfASteadyAppShowNoBurst() {
        val snaps = (0..40).map { snap(it, five = it * 2, four = it) }
        assertTrue(ReviewBursts.find(RatingSnapshots.toPoints(snaps)).bursts.isEmpty())
    }

    @Test fun aJumpInFiveStarCountsIsABurst() {
        val snaps = (0..30).map { snap(it, five = it) } + snap(31, five = 30 + 40) + (32..40).map { snap(it, five = 70 + (it - 31)) }
        val b = ReviewBursts.find(RatingSnapshots.toPoints(snaps)).bursts.single()
        assertEquals(ReviewBursts.Kind.HIGH, b.kind); assertTrue(b.count >= 40, "${b.count}")
    }

    @Test fun aLongGapIsSpreadNotStacked() {
        // Steady two a day, but the phone looked only on day 0, 20 and 40: 40 ratings per gap, which stacked on one day
        // would read as two bursts. Spread across the gap they are the steady pace they were.
        val snaps = listOf(snap(0, five = 0), snap(20, five = 40), snap(40, five = 80))
        assertTrue(ReviewBursts.find(RatingSnapshots.toPoints(snaps)).bursts.isEmpty())
    }

    @Test fun fallingCountsAddNothingAndBadRowsAreSkipped() {
        val snaps = listOf(snap(0, five = 10), snap(1, five = 8), RatingSnapshots.Snapshot(t0 + 2 * D, listOf(1, 2)))
        assertTrue(RatingSnapshots.toPoints(snaps).isEmpty())
    }

    @Test fun aWeekOfDailySnapshotsIsEnoughWhenAsked() {
        val snaps = (0..8).map { snap(it, five = it) } + snap(9, five = 8 + 30)   // points start at the first gap (day 1)
        val pts = RatingSnapshots.toPoints(snaps)
        assertTrue(ReviewBursts.find(pts).bursts.isEmpty())                                            // 14-day default: too short
        assertEquals(1, ReviewBursts.find(pts, ReviewBursts.MIN_SNAPSHOT_DAYS).bursts.size)
    }

    @Test fun pointsAreCapped() {
        val snaps = listOf(snap(0, five = 0), snap(1, five = 10_000_000))
        assertEquals(RatingSnapshots.MAX_POINTS_PER_GAP, RatingSnapshots.toPoints(snaps).size)
    }
}

class UseTimeTest {
    private val sig = "5".repeat(88)
    private val me = "Wallet1111111111111111111111111111111111111"

    @Test fun bucketsRoundDown() {
        val m = 60_000L
        assertEquals(UseTime.UNDER_10M, UseTime.of(9 * m + 59_999))
        assertEquals(UseTime.UNDER_HOUR, UseTime.of(10 * m))
        assertEquals(UseTime.HOURS, UseTime.of(60 * m))
        assertEquals(UseTime.MANY_HOURS, UseTime.of(300 * m))
        assertEquals(UseTime.DAYS_OF_USE, UseTime.of(1200 * m))
        assertEquals(UseTime.UNDER_10M, UseTime.of(-5))
    }

    @Test fun wireAndAttachment() {
        assertEquals("SRL1|tim|com.a.b|t2", ReviewMemo.encode(ReviewMemo.Time("com.a.b", UseTime.HOURS)))
        assertNull(ReviewMemo.decode("SRL1|tim|com.a.b|t9"))
        val review = LedgerEntry(sig, me, 1, ReviewMemo.Review("com.a.b", 5, listOf(ReviewMemo.Tag.WORKS), 3, null))
        val tim = LedgerEntry("$sig:2", me, 1, ReviewMemo.Time("com.a.b", UseTime.MANY_HOURS))
        assertEquals(mapOf(sig to UseTime.MANY_HOURS), Usage.time(listOf(review, tim)))
        assertTrue(Usage.time(listOf(review, tim.copy(signer = "Other111111111111111111111111111111111111111"))).isEmpty())
        assertTrue(Usage.time(listOf(review, tim.copy(memo = ReviewMemo.Time("com.x.y", UseTime.HOURS)))).isEmpty())
    }
}

class StoreNotesTest {
    private val pub = "Publisher11111111111111111111111111111111111"
    private val other = "Other111111111111111111111111111111111111111"
    private val rid = "3cf12330-0533-441e-92f8-fc8ab0707c45"
    private fun note(sig: Char, signer: String, t: Long, text: String = "Fixed in v185, thanks") = LedgerEntry(sig.toString().repeat(88), signer, t, ReviewMemo.StoreNote("com.a.b", rid, text))

    @Test fun wire() {
        val m = ReviewMemo.StoreNote("com.a.b", rid, "Fixed in v185, thanks")
        assertEquals(m, ReviewMemo.decode(ReviewMemo.encode(m)))
        assertNull(ReviewMemo.decode("SRL1|snt|com.a.b|NOT-A-UUID|Zm9v"))
        assertNull(ReviewMemo.decode("SRL1|snt|com.a.b|3CF12330-0533-441E-92F8-FC8AB0707C45|Zm9v"))   // uppercase is not canonical
        assertNull(ReviewMemo.decode("SRL1|snt|com.a.b|$rid|"))
        assertTrue(ReviewMemo.encode(ReviewMemo.StoreNote("a." + "b".repeat(98), rid, "ж".repeat(140))).toByteArray().size <= ReviewMemo.MAX_MEMO_BYTES)
    }

    @Test fun onlyVerifiedPublishersNewestWinsWithdrawnGone() {
        val vp = mapOf("com.a.b" to setOf(pub))
        val a = note('a', pub, 10, "first"); val b = note('b', pub, 20, "second"); val x = note('c', other, 30, "not the publisher")
        assertEquals("second", (StoreNotes.current(listOf(a, b, x), vp)["com.a.b" to rid]!!.memo as ReviewMemo.StoreNote).text)
        assertTrue(StoreNotes.current(listOf(x), vp).isEmpty())
        val rvk = LedgerEntry("d".repeat(88), pub, 40, ReviewMemo.Revocation(b.signature))
        assertEquals("first", (StoreNotes.current(listOf(a, b, rvk), vp)["com.a.b" to rid]!!.memo as ReviewMemo.StoreNote).text)
        assertTrue(StoreNotes.current(listOf(a, b), emptyMap()).isEmpty())
    }

    @Test fun anotherAppsPublisherCannotNoteThisAppsReview() {
        // The publisher of com.x.y names a review of com.a.b: it is filed under com.x.y and never replaces com.a.b's note.
        val vp = mapOf("com.a.b" to setOf(pub), "com.x.y" to setOf(other))
        val real = note('a', pub, 10, "the real note")
        val intruder = LedgerEntry("e".repeat(88), other, 99, ReviewMemo.StoreNote("com.x.y", rid, "not your app"))
        val m = StoreNotes.current(listOf(real, intruder), vp)
        assertEquals("the real note", (m["com.a.b" to rid]!!.memo as ReviewMemo.StoreNote).text)
    }
}

class BurstSparkTest {
    private val D = ReviewBursts.DAY
    @Test fun crushCoinReadsAsElevenTimesNotTwentyEight() {
        // 69 five-star over 17-24 Mar (8 days) against a usual 2.4 per 3 days: 69 / (2.4 * 8 / 3) = 10.8, shown as x11.
        val b = ReviewBursts.Burst(ReviewBursts.Kind.HIGH, 0L, 7 * D, 69, 75, 2.4, 0)
        assertEquals(10.78, BurstSpark.multiple(b), 0.01)
    }
    @Test fun sparkWindowSitsInsideThirtyDays() {
        val pts = (0 until 40).map { ReviewBursts.Point(it * D, 5) } + (0 until 10).map { ReviewBursts.Point(35 * D + it, 5) }
        val b = ReviewBursts.Burst(ReviewBursts.Kind.HIGH, 35 * D, 35 * D + 9, 10, 10, 1.0, 0)
        val s = BurstSpark.of(pts, b)
        assertEquals(30, s.counts.size); assertEquals(s.from, s.to); assertEquals(11, s.counts[s.from])
    }
}
