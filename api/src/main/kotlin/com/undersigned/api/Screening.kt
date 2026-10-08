package com.undersigned.api

import java.net.HttpURLConnection
import java.net.URL

/**
 * Sanctions screening, the app's WalletScreening: the on-device OFAC list, then TRM Labs' public endpoint (best effort;
 * unreachable = not blocked, the local list already ran). Run before building any transaction for a wallet.
 */
object Screening {
    private val SANCTIONED = setOf(
        "FhVTBpMYYBbkHGnRSEFaM5dpxEBJFzE2GXBSjKTqumKR", "5jDLMJP5Hd4G5GR7BPpj8B9kcTCFoAXBPdLYzfFUxMKT",
        "2pL1DTh2K8B3yrv6FUa1e8cfNYX9ib2pGkEXE7epFEZj", "AxVBBDuHE8bgrJYLy5GSsLf58cGPEJkZiWKhCPLqcviX",
    )
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Boolean>>()

    /** For payments: null when TRM could not answer (the caller refuses; fail closed). */
    fun screenForPayment(address: String): Boolean? {
        if (address in SANCTIONED) return true
        cache[address]?.takeIf { System.currentTimeMillis() - it.first < 24 * 3600_000L }?.let { return it.second }
        isSanctioned(address)
        return cache[address]?.second
    }

    fun isSanctioned(address: String): Boolean {
        if (address in SANCTIONED) return true
        cache[address]?.takeIf { System.currentTimeMillis() - it.first < 24 * 3600_000L }?.let { return it.second }
        val hit = runCatching {
            val c = URL("https://api.trmlabs.com/public/v1/sanctions/screening").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "Undersigned-API/1.0")
                c.connectTimeout = 5000; c.readTimeout = 5000
                c.outputStream.use { it.write("""[{"address":"$address"}]""".toByteArray()) }
                // TRM answers with HTTP 201, not 200 (measured 7 Sep 2026): any 2xx is an answer.
                if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() }.replace(" ", "").contains("\"isSanctioned\":true") else null
            } finally { c.disconnect() }
        }.getOrNull()
        hit?.let { cache[address] = System.currentTimeMillis() to it }
        return hit == true
    }
}
