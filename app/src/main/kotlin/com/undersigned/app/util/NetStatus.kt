package com.undersigned.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the last network fetch failed for want of a connection (1.1.5, offline walk 11 Oct 2026: a Seeker with no network
 * read "none" everywhere and was never told it was offline). Set by a fetch that fails with a network error (no route, no DNS,
 * refused, timed out); cleared by the next fetch that gets an answer. An HTTP error is an answer: the network is there.
 *
 * 1.1.6: also cleared, and [reconnects] bumped, when the system reports a default network with VALIDATED internet. Measured on
 * the emulator (11 Oct 2026): "Try again" tapped as Wi-Fi came back failed with UnknownHostException 77 ms BEFORE the system
 * switched to the new default network; nothing tried again after that, and the "No connection" flag stayed set with the
 * network up, so it took a second tap. Screens now retry a network failure once after 1.5 s ([retryOnce]) and re-run a
 * failed read when [reconnects] changes.
 */
object NetStatus {
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    private val _reconnects = MutableStateFlow(0)
    /** Bumped each time a (new) default network with validated internet appears. Key a failed read's retry on it. */
    val reconnects: StateFlow<Int> = _reconnects
    @Volatile private var validated: Network? = null

    /** A server answered (any status). */
    fun ok() { if (_offline.value) _offline.value = false }

    /** A fetch failed: marks offline only when the cause is the network itself. */
    fun failed(t: Throwable?) { if (t != null && isNetworkError(t)) _offline.value = true }

    fun isNetworkError(t: Throwable): Boolean {
        var c: Throwable? = t; var depth = 0
        while (c != null && depth++ < 8) { if (c is java.io.IOException) return true; c = c.cause }
        return false
    }

    /** Listens for the default network (once, from Application.onCreate). */
    fun watch(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && validated != network) {
                        validated = network; _offline.value = false; _reconnects.value = _reconnects.value + 1
                    }
                }
                override fun onLost(network: Network) { if (validated == network) validated = null }
            })
        }
    }

    /** Runs [block]; when it fails for want of a network (a retry fired as the connection came back), waits [pauseMs] and runs it once more. */
    suspend fun <T> retryOnce(pauseMs: Long = 1_500, block: suspend () -> Result<T>): Result<T> {
        val first = block()
        if (first.exceptionOrNull()?.let(::isNetworkError) != true) return first
        delay(pauseMs)
        return block()
    }
}
