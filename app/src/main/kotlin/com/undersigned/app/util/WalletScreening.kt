package com.undersigned.app.util

import android.util.Log
import com.undersigned.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sanctions screening of a wallet address, run before the App accepts a wallet and again before
 * any fee-bearing transaction. Two layers, ported from the fleet's GeoRestrictionService:
 *  1. an on-device copy of OFAC-designated Solana addresses (always checked, no network);
 *  2. TRM Labs' public sanctions screening endpoint (best effort; unreachable = not blocked, the
 *     local list already ran).
 * A listed address is refused outright: the treasury must never receive a fee from it.
 */
object WalletScreening {
    /** OFAC SDN-listed Solana addresses. Source: OFAC SDN list; refresh when the list changes. */
    private val SANCTIONED_WALLET_ADDRESSES = setOf(
        "FhVTBpMYYBbkHGnRSEFaM5dpxEBJFzE2GXBSjKTqumKR", // Lazarus Group (DPRK)
        "5jDLMJP5Hd4G5GR7BPpj8B9kcTCFoAXBPdLYzfFUxMKT", // Sinbad.io mixer (Nov 2023)
        "2pL1DTh2K8B3yrv6FUa1e8cfNYX9ib2pGkEXE7epFEZj", // DPRK-linked evasion
        "AxVBBDuHE8bgrJYLy5GSsLf58cGPEJkZiWKhCPLqcviX", // DPRK-linked evasion
    )

    suspend fun isSanctioned(address: String): Boolean {
        if (!AppConfig.Features.WALLET_SCREENING_ENABLED) return false
        if (address in SANCTIONED_WALLET_ADDRESSES) return true
        return trmLabs(address)
    }

    private suspend fun trmLabs(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://api.trmlabs.com/public/v1/sanctions/screening").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "Undersigned/1.0 (Android)")
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.outputStream.use { it.write("""[{"address":"$address"}]""".toByteArray()) }
            val code = c.responseCode
            // TRM answers this POST with HTTP 201, not 200 (measured 7 Sep 2026); accept any 2xx.
            val hit = if (code in 200..299) {
                val body = c.inputStream.bufferedReader().use { it.readText() }
                body.contains("\"isSanctioned\":true") || body.contains("\"isSanctioned\": true")
            } else { Log.w(TAG, "TRM Labs returned $code"); false }
            c.disconnect(); hit
        } catch (e: Exception) { Log.w(TAG, "TRM Labs unavailable: ${e.message?.take(60)}"); false }
    }

    private const val TAG = "WalletScreening"
}
