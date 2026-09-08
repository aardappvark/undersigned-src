package com.undersigned.app.data

import android.content.Context
import android.content.SharedPreferences
import com.undersigned.protocol.SignerFacts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** On-device preferences. Nothing here leaves the device. */
object UserPreferences {
    private const val PREFS_NAME = "undersigned_prefs"
    private const val KEY_WALLET = "wallet_address"
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    private const val KEY_HIDDEN = "hidden_signatures"
    private const val KEY_FACTS = "signer_facts"
    private const val KEY_CATALOGUE_REFRESH = "catalogue_refreshed_at"
    private const val KEY_DEVICE_SNAPSHOT = "device_snapshot"
    private const val KEY_CHANGES = "device_changes"
    private const val KEY_FOLLOWS = "followed_packages"
    private const val KEY_STARRED = "starred_records"
    private const val KEY_ALERTS = "alerts_enabled"
    private const val KEY_LAST_NOTIFIED = "last_notified_signature"
    private const val KEY_DOMAINS = "skr_domains"
    private const val KEY_CLAIMS = "publisher_claims"
    private const val KEY_DIVERGENCE = "divergence_marks"
    private const val KEY_WAVES = "wave_marks"
    private const val FACTS_TTL_MS = 6L * 3600L * 1000L

    private var prefs: SharedPreferences? = null

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress
    private val _onboardingSeen = MutableStateFlow(false)
    val onboardingSeen: StateFlow<Boolean> = _onboardingSeen
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden
    private val _follows = MutableStateFlow<Set<String>>(emptySet())
    val follows: StateFlow<Set<String>> = _follows
    private val _starred = MutableStateFlow<Set<String>>(emptySet())
    val starred: StateFlow<Set<String>> = _starred
    private val _alertsEnabled = MutableStateFlow(false)
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _walletAddress.value = p.getString(KEY_WALLET, null)
        _onboardingSeen.value = p.getBoolean(KEY_ONBOARDING_SEEN, false)
        _hidden.value = p.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        _follows.value = p.getStringSet(KEY_FOLLOWS, emptySet()) ?: emptySet()
        _starred.value = p.getStringSet(KEY_STARRED, emptySet()) ?: emptySet()
        _alertsEnabled.value = p.getBoolean(KEY_ALERTS, false)
    }

    fun toggleStar(id: String) { val s = if (id in _starred.value) _starred.value - id else _starred.value + id; prefs?.edit()?.putStringSet(KEY_STARRED, s)?.apply(); _starred.value = s }
    fun follow(pkg: String) { val s = _follows.value + pkg; prefs?.edit()?.putStringSet(KEY_FOLLOWS, s)?.apply(); _follows.value = s }
    fun unfollow(pkg: String) { val s = _follows.value - pkg; prefs?.edit()?.putStringSet(KEY_FOLLOWS, s)?.apply(); _follows.value = s }
    fun setAlerts(v: Boolean) { prefs?.edit()?.putBoolean(KEY_ALERTS, v)?.apply(); _alertsEnabled.value = v }
    var lastNotifiedSignature: String?
        get() = prefs?.getString(KEY_LAST_NOTIFIED, null)
        set(v) { prefs?.edit()?.putString(KEY_LAST_NOTIFIED, v)?.apply() }

    fun setWallet(address: String?) { prefs?.edit()?.putString(KEY_WALLET, address)?.apply(); _walletAddress.value = address }
    fun setOnboardingSeen(v: Boolean) { prefs?.edit()?.putBoolean(KEY_ONBOARDING_SEEN, v)?.apply(); _onboardingSeen.value = v }

    fun hide(signature: String) { val s = _hidden.value + signature; prefs?.edit()?.putStringSet(KEY_HIDDEN, s)?.apply(); _hidden.value = s }
    fun unhideAll() { prefs?.edit()?.remove(KEY_HIDDEN)?.apply(); _hidden.value = emptySet() }

    /** Signer facts cache: {signer: {sgt, skr, at}} — immutable-ish data with a TTL. */
    fun cachedFacts(signer: String): SignerFacts? {
        val o = runCatching { JSONObject(prefs?.getString(KEY_FACTS, "{}") ?: "{}").optJSONObject(signer) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - o.optLong("at", 0L) > FACTS_TTL_MS) return null
        return SignerFacts(o.optBoolean("sgt", false), o.optDouble("skr", 0.0), o.optString("att", null)?.takeIf { it.isNotEmpty() && it != "null" } ?: if (o.optBoolean("sgt", false)) "seeker-genesis" else null)
    }
    fun cacheFacts(signer: String, f: SignerFacts) {
        val all = runCatching { JSONObject(prefs?.getString(KEY_FACTS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        all.put(signer, JSONObject().put("sgt", f.hasGenesisToken).put("skr", f.stakedSkr).put("att", f.attestation ?: "").put("at", System.currentTimeMillis()))
        prefs?.edit()?.putString(KEY_FACTS, all.toString())?.apply()
    }
    fun clearFacts() { prefs?.edit()?.remove(KEY_FACTS)?.apply() }

    /** True the first time this (rounded) divergence is seen for the package; suppresses repeat alerts. */
    fun markDivergence(pkg: String, signed: Double, store: Double): Boolean {
        val key = "$pkg|${"%.1f".format(java.util.Locale.US, signed)}|${"%.1f".format(java.util.Locale.US, store)}"
        val all = runCatching { JSONObject(prefs?.getString(KEY_DIVERGENCE, "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (all.has(key)) return false
        all.put(key, System.currentTimeMillis()); prefs?.edit()?.putString(KEY_DIVERGENCE, all.toString())?.apply(); return true
    }
    fun markWave(pkg: String, newestReviewId: String): Boolean {
        val all = runCatching { JSONObject(prefs?.getString(KEY_WAVES, "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (all.optString(pkg) == newestReviewId) return false
        all.put(pkg, newestReviewId); prefs?.edit()?.putString(KEY_WAVES, all.toString())?.apply(); return true
    }

    fun cachedClaim(pkg: String, wallet: String, ttl: Long): Boolean? = runCatching {
        val o = JSONObject(prefs?.getString(KEY_CLAIMS, "{}") ?: "{}").optJSONObject("$pkg|$wallet") ?: return null
        if (System.currentTimeMillis() - o.optLong("at") > ttl) null else o.optBoolean("ok")
    }.getOrNull()
    fun cacheClaim(pkg: String, wallet: String, ok: Boolean) {
        val all = runCatching { JSONObject(prefs?.getString(KEY_CLAIMS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        all.put("$pkg|$wallet", JSONObject().put("ok", ok).put("at", System.currentTimeMillis())); prefs?.edit()?.putString(KEY_CLAIMS, all.toString())?.apply()
    }

    fun cachedDomain(wallet: String): String? = runCatching { JSONObject(prefs?.getString(KEY_DOMAINS, "{}") ?: "{}").let { if (it.has(wallet)) it.optString(wallet) else null } }.getOrNull()
    fun cacheDomain(wallet: String, name: String) {
        val all = runCatching { JSONObject(prefs?.getString(KEY_DOMAINS, "{}") ?: "{}") }.getOrDefault(JSONObject()); all.put(wallet, name)
        prefs?.edit()?.putString(KEY_DOMAINS, all.toString())?.apply()
    }

    var catalogueRefreshedAt: Long
        get() = prefs?.getLong(KEY_CATALOGUE_REFRESH, 0L) ?: 0L
        set(v) { prefs?.edit()?.putLong(KEY_CATALOGUE_REFRESH, v)?.apply() }

    var deviceSnapshotJson: String?
        get() = prefs?.getString(KEY_DEVICE_SNAPSHOT, null)
        set(v) { prefs?.edit()?.putString(KEY_DEVICE_SNAPSHOT, v)?.apply() }
    var changesJson: String?
        get() = prefs?.getString(KEY_CHANGES, null)
        set(v) { prefs?.edit()?.putString(KEY_CHANGES, v)?.apply() }

    fun clearAll() { prefs?.edit()?.clear()?.apply(); _walletAddress.value = null; _hidden.value = emptySet(); _onboardingSeen.value = false; _follows.value = emptySet(); _alertsEnabled.value = false }
}
