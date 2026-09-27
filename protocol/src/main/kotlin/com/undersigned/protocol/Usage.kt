package com.undersigned.protocol

/**
 * How long the reviewed app had been installed on the reviewer's phone when they signed, rounded to a bucket. It rides
 * as a second memo (`SRL1|use|<package>|<bucket>`) in the REVIEW's own transaction, so readers that predate it still
 * read the review (an unknown record type is ignored) and nothing links it to anything but that review.
 *
 * What it proves: the reviewer's wallet signed a statement that their phone reported this. A modified client can lie,
 * so readers show it as the reviewer's device report, never as proof of use, and no score or weight reads it.
 * First-install time resets on reinstall, so a season's uninstall-reinstall loop reads as a fresh install.
 */
enum class UseBucket(val wire: String, val minDays: Long) {
    UNDER_DAY("d0", 0), DAYS("d1", 1), WEEKS("w1", 7), MONTHS("m1", 30), LONG("m3", 90);

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun fromWire(s: String) = byWire[s]
        /** The bucket for an install at [firstInstallMs] seen at [nowMs]; a clock that runs backwards reads as under a day. */
        fun of(firstInstallMs: Long, nowMs: Long): UseBucket {
            val days = ((nowMs - firstInstallMs).coerceAtLeast(0L)) / 86_400_000L
            return entries.last { days >= it.minDays }
        }
    }
}

/**
 * Foreground time the reviewer's phone recorded for the reviewed app, rounded — the usage-access counter Android keeps
 * (measured 27 Sep 2026 on Android 16: it survived an uninstall and reinstall). Rides as `SRL1|tim|<package>|<bucket>`
 * in the review's own transaction, like [UseBucket]. Using an app costs time; installing it costs nothing, so this says
 * more than install age. Still the device's report: read by no score or weight.
 */
enum class UseTime(val wire: String, val minMinutes: Long) {
    UNDER_10M("t0", 0), UNDER_HOUR("t1", 10), HOURS("t2", 60), MANY_HOURS("t3", 300), DAYS_OF_USE("t4", 1200);

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun fromWire(s: String) = byWire[s]
        fun of(foregroundMs: Long): UseTime { val m = foregroundMs.coerceAtLeast(0L) / 60_000L; return entries.last { m >= it.minMinutes } }
    }
}

object Usage {
    /**
     * Review record id → the bucket its own transaction reported. A `use` record counts only beside a review of the
     * SAME package, in the SAME transaction, by the SAME signer; anywhere else it is ignored.
     */
    fun index(entries: List<LedgerEntry>): Map<String, UseBucket> {
        val uses = entries.filter { it.memo is ReviewMemo.Use }.groupBy { it.signature.substringBefore(':') }
        if (uses.isEmpty()) return emptyMap()
        val out = HashMap<String, UseBucket>()
        for (e in entries) {
            val r = e.memo as? ReviewMemo.Review ?: continue
            val u = uses[e.signature.substringBefore(':')]?.firstOrNull { it.signer == e.signer && (it.memo as ReviewMemo.Use).packageName == r.packageName } ?: continue
            out[e.signature] = (u.memo as ReviewMemo.Use).bucket
        }
        return out
    }

    /** Review record id → the foreground time its own transaction reported; same attachment rule as [index]. */
    fun time(entries: List<LedgerEntry>): Map<String, UseTime> {
        val tims = entries.filter { it.memo is ReviewMemo.Time }.groupBy { it.signature.substringBefore(':') }
        if (tims.isEmpty()) return emptyMap()
        val out = HashMap<String, UseTime>()
        for (e in entries) {
            val r = e.memo as? ReviewMemo.Review ?: continue
            val u = tims[e.signature.substringBefore(':')]?.firstOrNull { it.signer == e.signer && (it.memo as ReviewMemo.Time).packageName == r.packageName } ?: continue
            out[e.signature] = (u.memo as ReviewMemo.Time).time
        }
        return out
    }
}
