package com.undersigned.app.solana

import android.content.Context
import android.util.Log
import org.json.JSONObject

/** One accepted attested-device credential: a soulbound Token-2022 collection identified by mint authority (+ metadata address). */
data class Attestation(val id: String, val label: String, val mintAuthority: String, val metadata: String?)

/**
 * The governed list of attestations that make a wallet "one voice". Bundled with the app; if the list's
 * `remoteUrl` is set (a URL Solana Mobile or an OEM programme controls), the remote copy replaces it after a
 * successful fetch. Adding an OEM device credential is a JSON entry, not an app change.
 */
object AttestationList {
    private var cached: List<Attestation>? = null
    fun load(context: Context): List<Attestation> {
        cached?.let { return it }
        val list = runCatching {
            val o = JSONObject(context.assets.open("attestations.json").bufferedReader().use { it.readText() })
            val arr = o.getJSONArray("entries")
            (0 until arr.length()).map { i -> val e = arr.getJSONObject(i); Attestation(e.getString("id"), e.getString("label"), e.getString("mintAuthority"), e.optString("metadata", null)) }
        }.getOrElse { Log.w("AttestationList", "bundled list unreadable: ${it.message}"); emptyList() }
        cached = list; return list
    }
    fun label(context: Context, id: String?): String? = id?.let { i -> load(context).firstOrNull { it.id == i }?.label }
}
