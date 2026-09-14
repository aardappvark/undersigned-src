package com.undersigned.app.wallet

import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

/**
 * Turns a wallet-adapter failure into a sentence a person can act on. The adapter surfaces Java exceptions,
 * and the raw text reached the screen: on 15 Sep 2026 a sign-in that waited for a double-tap showed
 * "java.util.concurrent.TimeoutException: Timed out waiting for response with id=1".
 *
 * Messages the app itself raised on purpose (a mismatched account, a missing wallet) are kept, because they
 * already say what to do. Everything else maps to a cause the user can recognise.
 */
object WalletErrors {
    fun humanise(e: Throwable?, fallback: String = "The wallet couldn't complete the request. Try again."): String {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            when (t) {
                is TimeoutException -> return "The wallet didn't get your approval in time. Try again, and approve with the double-tap when the wallet asks."
                is CancellationException, is kotlinx.coroutines.CancellationException -> return "The wallet request was cancelled."
            }
            val m = t.message.orEmpty()
            if (m.contains("Timed out", ignoreCase = true)) return "The wallet didn't get your approval in time. Try again, and approve with the double-tap when the wallet asks."
            if (m.contains("declin", true) || m.contains("reject", true) || m.contains("not authorized", true) || m.contains("cancel", true))
                return "The request was declined in the wallet."
            t = t.cause; depth++
        }
        val own = e?.message.orEmpty()
        // Our own deliberate messages are plain sentences without a Java class prefix; keep those.
        return if (own.isNotBlank() && !own.contains("Exception") && !own.contains("java.") && own.length <= 200) own else fallback
    }
}
