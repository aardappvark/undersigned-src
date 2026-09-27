package com.undersigned.protocol

/**
 * The store's star COUNTS over time, as this device saw them. Star-only ratings never reach the review list — one game
 * showed 3,679 ratings while its newest 50 listed reviews spanned 203 days (27 Sep 2026) — so a campaign of bare
 * ratings is invisible to the review-list burst check. Snapshots of the counts turn into rating "points" that
 * [ReviewBursts] can read.
 *
 * Ratings that arrived between two snapshots are spread evenly across the gap. Stacking them on the later snapshot
 * would turn a phone that was not opened for ten days into a ten-day pile-up on one day: a burst made by the sampling,
 * not by the raters. A count that falls (a rating withdrawn or removed) adds nothing.
 */
object RatingSnapshots {
    data class Snapshot(val time: Long, val byStar: List<Int>)

    /** At most this many points per gap, so a first snapshot pair on a huge app cannot allocate millions of points. */
    const val MAX_POINTS_PER_GAP = 5_000

    fun toPoints(snapshots: List<Snapshot>): List<ReviewBursts.Point> {
        val s = snapshots.filter { it.byStar.size == 5 }.sortedBy { it.time }
        val out = ArrayList<ReviewBursts.Point>()
        for (k in 1 until s.size) {
            val a = s[k - 1]; val b = s[k]
            val gap = b.time - a.time
            if (gap <= 0) continue
            val deltas = (1..5).map { star -> star to (b.byStar[star - 1] - a.byStar[star - 1]).coerceAtLeast(0) }
            val total = deltas.sumOf { it.second }.coerceAtMost(MAX_POINTS_PER_GAP)
            if (total == 0) continue
            // Interleave the stars so each slice of the gap carries the same mix, then space the points evenly.
            val stars = ArrayList<Int>(total)
            val left = deltas.associate { it.first to it.second }.toMutableMap()
            while (stars.size < total) for (star in 1..5) { val n = left[star] ?: 0; if (n > 0 && stars.size < total) { stars.add(star); left[star] = n - 1 } }
            stars.forEachIndexed { i, star -> out.add(ReviewBursts.Point(a.time + gap * (i + 1) / total, star)) }
        }
        return out
    }
}
