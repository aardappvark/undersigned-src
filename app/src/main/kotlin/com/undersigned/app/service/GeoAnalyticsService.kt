package com.undersigned.app.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeoAnalyticsService — fire-and-forget anonymous event tracking for Undersigned.
 *
 * PRIVACY ARCHITECTURE:
 * - The app sends ONLY the event type (e.g. "app_open") to the Cloudflare Worker.
 * - The Worker derives country from the request IP via Cloudflare's edge (request.cf).
 * - The IP is NEVER stored — only aggregate counts by country/day. City is not recorded.
 * - Zero PII: no user id, no device id, no wallet address, no fingerprint.
 * - Compliant with GDPR (Recital 26), CCPA, LGPD, APPI, PIPA without consent.
 *
 * USAGE:
 *   GeoAnalyticsService.init(this, getString(R.string.analytics_api_key))  // UndersignedApp.onCreate
 *   UserPreferences.applyAnalyticsPreference()                       // BEFORE the first track()
 *   GeoAnalyticsService.track(GeoAnalyticsService.Events.APP_OPEN)
 *
 * All calls are fire-and-forget on a background coroutine. Failures are silently
 * ignored — analytics must never crash or block the app.
 *
 * BACKEND: Cloudflare Worker + D1, the post-2026-08-21 fleet pattern. Do NOT add a
 * Vercel backend — that path hit the Blob advanced-operations cap fleet-wide.
 * Live verification endpoint:
 *   https://undersigned-analytics.aardappvark.workers.dev/public-stats
 */
object GeoAnalyticsService {

    private const val TAG = "GeoAnalytics"

    private const val ENDPOINT = "https://undersigned-analytics.aardappvark.workers.dev/track"

    // App-level API key — injected from local.properties via R.string.analytics_api_key.
    private var apiKey: String = ""

    // Defaults to ON, but MainActivity applies the stored opt-out BEFORE any event can
    // fire. Never track between init() and applyAnalyticsPreference().
    private var enabled = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Allowed event types — must match ALLOWED_EVENTS in the Worker exactly. */
    object Events {
        const val APP_OPEN = "app_open"
        const val ONBOARDING_COMPLETE = "onboarding_complete"
        const val TERMS_ACCEPTED = "terms_accepted"
        const val WALLET_CONNECTED = "wallet_connected"
        const val WALLET_DISCONNECTED = "wallet_disconnected"
        const val SGT_VERIFIED = "sgt_verified"

        // Plural on purpose: `payments_sol` / `payments_skr` are the fleet dashboard's
        // revenue keys (fleet-stats-dashboard api/fleet.js PAYMENT_KEYS). Fired for every
        // treasury payment carrying the UNS memo (Pro licence, board listing, promotion),
        // so /public-stats reconciles against the treasury ledger. Never ship a build
        // sending these before the Worker that accepts them is deployed — an unknown
        // event_type is a 400 and the payment is lost.
        const val PAYMENT_SOL = "payments_sol"
        const val PAYMENT_SKR = "payments_skr"

        // Not a revenue key (a restore would re-count money already counted). Undersigned
        // reads Pro from the chain and has no restore flow; kept for fleet parity.
        const val PURCHASES_RESTORED = "purchases_restored"

        const val REVIEW_SIGNED = "review_signed"
        const val REVIEW_BOOSTED = "review_boosted"
        const val STORE_HANDOFF = "store_handoff"
        const val PUBLISHER_CLAIM_VERIFIED = "publisher_claim_verified"
        const val SETTINGS_OPENED = "settings_opened"
        const val UPGRADE_VIEWED = "upgrade_viewed"
        const val GEO_BLOCKED = "geo_blocked"
    }

    /** Initialize with the API key from string resources. Repeat calls are no-ops. */
    // Durable outbox. track() is fire-and-forget, so an event queued as the process is
    // being torn down simply evaporates. That is normally harmless -- one lost app_open
    // -- but it cost us a real payments_skr on 2026-08-27: the payment landed on chain,
    // Pro was granted, and the event never arrived, because the app had just returned
    // from the wallet and was backgrounded before the HTTP call finished.
    //
    // Payments are exactly when process death is most likely, so every event is written
    // to disk first and only removed once the server has accepted it. Whatever is still
    // pending is flushed on the next launch.
    private var outbox: android.content.SharedPreferences? = null
    private const val OUTBOX_KEY = "pending_events"

    fun init(context: android.content.Context, key: String) {
        if (apiKey.isEmpty()) apiKey = key
        if (outbox == null) {
            outbox = context.getSharedPreferences("undersigned_analytics_outbox", android.content.Context.MODE_PRIVATE)
        }
    }

    /** Retry anything that never made it. Call once at process start, after init. */
    fun flushPending() {
        val box = outbox ?: return
        val pending = box.getStringSet(OUTBOX_KEY, emptySet())?.toList() ?: return
        if (pending.isEmpty()) return
        scope.launch {
            for (entry in pending) {
                // entry is "<id>|<event_type>"
                val type = entry.substringAfter('|', "")
                if (type.isNotEmpty() && post(type)) remove(entry)
            }
        }
    }

    private fun remove(entry: String) {
        val box = outbox ?: return
        synchronized(this) {
            val cur = box.getStringSet(OUTBOX_KEY, emptySet())?.toMutableSet() ?: return
            cur.remove(entry)
            box.edit().putStringSet(OUTBOX_KEY, cur).commit()
        }
    }

    private fun enqueue(entry: String) {
        val box = outbox ?: return
        synchronized(this) {
            val cur = box.getStringSet(OUTBOX_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
            // Bounded, so a long offline spell cannot grow without limit.
            if (cur.size < 200) cur.add(entry)
            // commit(), not apply(): the whole point is that it survives being killed
            // in the next few milliseconds.
            box.edit().putStringSet(OUTBOX_KEY, cur).commit()
        }
    }

    /**
     * Track an event. Fire-and-forget — never blocks, never throws. The Worker derives
     * geo from the request IP server-side.
     */
    fun track(eventType: String) {
        if (!enabled) return
        if (apiKey.isEmpty()) return // not initialised — key not injected yet

        // Persist BEFORE attempting the network, so a kill between the two is recoverable.
        val entry = "${System.nanoTime()}|$eventType"
        enqueue(entry)
        scope.launch {
            if (post(eventType)) remove(entry)
        }
    }

    /** Returns true only when the server accepted the event. */
    private suspend fun post(eventType: String): Boolean {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(ENDPOINT)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-API-Key", apiKey)
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write("""{"event_type":"$eventType"}""")
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    Log.d(TAG, "Analytics event '$eventType' failed: HTTP $responseCode")
                }
                // A 400 means the Worker does not know this event name. Retrying forever
                // would never succeed, so treat it as handled and drop it.
                responseCode == 200 || responseCode == 400
            } catch (e: Exception) {
                // Silently fail — analytics must never crash the app. Left queued.
                Log.d(TAG, "Analytics event '$eventType' failed: ${e.message}")
                false
            } finally {
                connection?.disconnect()
            }
        }
    }

    /** Enable/disable tracking (user opt-out, or testing). */
    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    /** Current state, for the Settings toggle. */
    fun isEnabled(): Boolean = enabled
}
