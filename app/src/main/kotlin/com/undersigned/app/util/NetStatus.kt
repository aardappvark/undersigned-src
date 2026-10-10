package com.undersigned.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the last network fetch failed for want of a connection (1.1.5, offline walk 11 Oct 2026: a Seeker with no network
 * read "none" everywhere and was never told it was offline). Set by a fetch that fails with a network error (no route, no DNS,
 * refused, timed out); cleared by the next fetch that gets an answer. An HTTP error is an answer: the network is there.
 */
object NetStatus {
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    /** A server answered (any status). */
    fun ok() { if (_offline.value) _offline.value = false }

    /** A fetch failed: marks offline only when the cause is the network itself. */
    fun failed(t: Throwable?) { if (t != null && isNetworkError(t)) _offline.value = true }

    fun isNetworkError(t: Throwable): Boolean {
        var c: Throwable? = t; var depth = 0
        while (c != null && depth++ < 8) { if (c is java.io.IOException) return true; c = c.cause }
        return false
    }
}
