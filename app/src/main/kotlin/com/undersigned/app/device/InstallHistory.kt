package com.undersigned.app.device

/**
 * How long a store app has been on this phone IN TOTAL, across reinstalls. Android keeps no record of an app's past
 * installs: its first-install time resets on a fresh install, so a long-time user who reinstalled reads as new. This
 * log is Undersigned's own, built from the installed-apps snapshot it already takes (on open, and on each background
 * run): each observation extends the current installed period or closes it.
 *
 * Deliberately conservative: an uninstall is only noticed at the next observation, and the period is closed at the
 * LAST time it was seen installed, never later. It knows nothing from before Undersigned was installed, except the
 * current install's own first-install time. Never uploaded; only a rounded bucket can go into a review.
 */
object InstallHistory {
    /** [spans] closed installed periods (ms); [current] the first-install time of the install present now, or null; [reinstalls] installs after the first. */
    data class Presence(val spans: List<Pair<Long, Long>>, val current: Long?, val lastSeen: Long, val reinstalls: Int)

    /** One observation at [now]: [firstInstall] is the package manager's first-install time, or null when not installed. */
    fun observe(prev: Presence?, firstInstall: Long?, now: Long): Presence? {
        if (firstInstall == null) {
            prev ?: return null
            val cur = prev.current ?: return prev
            return prev.copy(spans = prev.spans + (cur to prev.lastSeen), current = null)
        }
        if (prev == null) return Presence(emptyList(), firstInstall, now, 0)
        if (prev.current == firstInstall) return prev.copy(lastSeen = now)
        // A new install. Close the old period if it was still open (reinstalled between two looks), and never let the
        // new one start before the last recorded period ended, so no time is counted twice.
        val spans = if (prev.current != null) prev.spans + (prev.current to prev.lastSeen) else prev.spans
        val start = maxOf(firstInstall, spans.maxOfOrNull { it.second } ?: Long.MIN_VALUE)
        return Presence(spans, start, now, prev.reinstalls + 1)
    }

    fun totalMs(p: Presence, now: Long): Long = p.spans.sumOf { (a, b) -> (b - a).coerceAtLeast(0L) } + (p.current?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
}
