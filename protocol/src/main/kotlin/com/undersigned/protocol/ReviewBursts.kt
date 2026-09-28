package com.undersigned.protocol

/**
 * Bursts of same-star reviews against an app's own usual pace: many 5★ (or 1★) reviews landing within a few days.
 * Paid-review campaigns look like this; so do a store feature, a launch or a big release, so the reader states the fact
 * and never calls it a campaign. Deterministic and pure, like [Ranking]: any reader with the same reviews finds the
 * same bursts.
 *
 * The usual pace is the MEDIAN day across everything read (days with none count as zero), not the weeks before the
 * window. A trailing baseline missed the one documented campaign we could test: CrushCoin offered in-game gold for a
 * 5★ review on 17 Mar 2026 and its store list shows 24 five-star reviews on 18 Mar, but the app had launched days
 * earlier, so there were no "weeks before" to compare with (measured 27 Sep 2026). A median also cannot be dragged up
 * by the campaign itself, or by an earlier one. Reviews read must span at least [MIN_HISTORY_DAYS].
 */
object ReviewBursts {
    data class Point(val time: Long, val rating: Int, val text: String? = null)
    enum class Kind(val stars: Int) { HIGH(5), LOW(1) }
    /**
     * [start]..[end] epoch seconds (first and last matching review). [expected] = matching reviews a usual 3-day window
     * holds (mean over the read history outside this burst) — for display; the test itself uses the median.
     */
    data class Burst(val kind: Kind, val start: Long, val end: Long, val count: Int, val total: Int, val expected: Double, val sameText: Int) {
        /** How far above its usual pace the burst ran; readers show the strongest first. */
        val intensity: Double get() = count / maxOf(expected * ((end - start) / WINDOW + 1), 1.0)
    }
    /** [from] = the oldest review read (the history available), [checked] = reviews read. */
    data class Findings(val bursts: List<Burst>, val checked: Int, val from: Long?)

    const val DAY = 86_400L
    const val WINDOW = 3 * DAY
    const val MIN_COUNT = 5
    /** Share of the window's reviews that must carry the burst's star count: a busy week of mixed reviews is not a burst. */
    const val MIN_SHARE = 0.8
    /** How far above the usual pace a window must run. */
    const val FACTOR = 4.0
    const val MIN_HISTORY_DAYS = 14L
    /** Daily star-count snapshots: a week of days is enough for a median of daily counts to mean something. */
    const val MIN_SNAPSHOT_DAYS = 7L

    fun find(points: List<Point>, minHistoryDays: Long = MIN_HISTORY_DAYS): Findings {
        val p = points.sortedBy { it.time }
        if (p.isEmpty()) return Findings(emptyList(), 0, null)
        val oldest = p.first().time; val newest = p.last().time
        if (newest - oldest < minHistoryDays * DAY) return Findings(emptyList(), p.size, oldest)
        val days = ((newest - oldest) / DAY + 1).toInt()
        val out = ArrayList<Burst>()
        for (kind in Kind.entries) {
            val perDay = IntArray(days); p.filter { it.rating == kind.stars }.forEach { perDay[((it.time - oldest) / DAY).toInt()]++ }
            val usual = maxOf(median(perDay) * 3.0, 1.0)
            var i = 0
            while (i < p.size) {
                val t = p[i].time
                if (p[i].rating != kind.stars) { i++; continue }
                val win = p.filter { it.time >= t && it.time < t + WINDOW }
                val c = win.count { it.rating == kind.stars }
                if (c < MIN_COUNT || c.toDouble() / win.size < MIN_SHARE || c < FACTOR * usual) { i++; continue }
                // Extend window by window while each next window passes the same test on its own, so a campaign that runs
                // a week is one burst — but the steady trickle after it is not. Extending to any matching review within
                // a window of the last one ran CrushCoin's 18 Mar campaign on to 7 May as one "burst" of 185 (27 Sep 2026).
                var last = win.last { it.rating == kind.stars }.time
                while (true) {
                    val next = p.firstOrNull { it.rating == kind.stars && it.time > last }?.time ?: break
                    if (next - last >= WINDOW) break
                    val w = p.filter { it.time >= next && it.time < next + WINDOW }
                    val n = w.count { it.rating == kind.stars }
                    if (n < MIN_COUNT || n.toDouble() / w.size < MIN_SHARE || n < FACTOR * usual) break
                    last = w.last { it.rating == kind.stars }.time
                }
                val span = p.filter { it.time in t..last }
                val matching = span.filter { it.rating == kind.stars }
                val outsideDays = (days - ((last - t) / DAY + 1).toInt()).coerceAtLeast(1)
                val outside = p.count { it.rating == kind.stars && it.time !in t..last }
                out.add(Burst(kind, t, last, matching.size, span.size, outside * 3.0 / outsideDays, sameText(matching)))
                i = p.indexOfFirst { it.time > last }.let { if (it < 0) p.size else it }
            }
        }
        return Findings(out.sortedByDescending { it.start }, p.size, oldest)
    }

    private fun median(a: IntArray): Double { val s = a.sorted(); return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0 }

    /** Reviews in the burst whose words (at least three) match another review's exactly — template text. */
    fun sameText(points: List<Point>): Int {
        val norm = points.mapNotNull { pt -> pt.text?.let { Similarity.normalise(it) }?.takeIf { it.size >= 3 }?.joinToString(" ") }
        return norm.groupingBy { it }.eachCount().values.filter { it > 1 }.sum()
    }
}

/**
 * Daily counts for a burst's small chart: 30 UTC days ending 3 days after the burst (or at the newest point), counting
 * the burst's star value; [from]..[to] are the burst's day indexes in [counts], clamped. Pure, for the UI.
 */
object BurstSpark {
    data class Spark(val counts: List<Int>, val from: Int, val to: Int)
    fun of(points: List<ReviewBursts.Point>, b: ReviewBursts.Burst, days: Int = 30): Spark {
        val D = ReviewBursts.DAY
        val newestDay = (points.maxOfOrNull { it.time } ?: b.end) / D
        val endDay = minOf(b.end / D + 3, maxOf(newestDay, b.end / D))
        val startDay = endDay - days + 1
        val counts = IntArray(days)
        points.forEach { p -> if (p.rating == b.kind.stars) { val i = (p.time / D - startDay).toInt(); if (i in 0 until days) counts[i]++ } }
        return Spark(counts.toList(), (b.start / D - startDay).toInt().coerceIn(0, days - 1), (b.end / D - startDay).toInt().coerceIn(0, days - 1))
    }

    /** Window-normalised multiple of the usual pace: the burst's count against the pace over the same number of days. */
    fun multiple(b: ReviewBursts.Burst): Double {
        val windowDays = ((b.end - b.start) / ReviewBursts.DAY + 1).toDouble()
        return if (b.expected <= 0) 0.0 else b.count / (b.expected * windowDays / 3.0)
    }
}
