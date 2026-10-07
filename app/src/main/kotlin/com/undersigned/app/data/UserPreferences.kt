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
    private const val KEY_AUTH_TOKEN = "mwa_auth_token"  // MWA reauthorize token; not a key, and useless without the wallet
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    private const val KEY_HIDDEN = "hidden_signatures"
    private const val KEY_FACTS = "signer_facts"
    private const val KEY_MY_STORE = "my_store_reviews"   // {wallet, at, reviews:{pkg:{…}}} — the wallet's own store reviews, last scan
    private const val KEY_VERIFIED = "verified_publishers"   // package → wallets whose claim verified, last seen
    private const val KEY_CATALOGUE_REFRESH = "catalogue_refreshed_at"
    private const val KEY_DEVICE_SNAPSHOT = "device_snapshot"
    private const val KEY_CHANGES = "device_changes"
    private const val KEY_FOLLOWS = "followed_packages"
    private const val KEY_STARRED = "starred_records"
    private const val KEY_ALERTS = "alerts_enabled"
    private const val KEY_LAST_NOTIFIED = "last_notified_signature"
    private const val KEY_DOMAINS = "skr_domains_v2"   // v1 cached "no name" from the broken derivation
    private const val KEY_CLAIMS = "publisher_claims"
    private const val KEY_DIVERGENCE = "divergence_marks"
    private const val KEY_RATING_SNAPS = "rating_snaps"
    private const val KEY_BURST_MARKS = "burst_marks"
    private const val KEY_DENSE = "reading_dense"       // device-local reading density (design §6); never on-chain
    private const val KEY_HOWTO_SEEN = "howto_seen"
    private const val KEY_WATCH_HOURS = "watch_interval_hours"
    private const val KEY_GRANTERS = "pro_granters"          // wallets this device was told may share Pro with it
    private const val KEY_SEAT_LABELS = "pro_seat_labels"    // tag → the wallet it was granted to, kept locally so the list is readable
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
    private val _dense = MutableStateFlow(false)
    val dense: StateFlow<Boolean> = _dense
    private val _howToSeen = MutableStateFlow(false)
    val howToSeen: StateFlow<Boolean> = _howToSeen
    private val _granters = MutableStateFlow<Set<String>>(emptySet())
    val granters: StateFlow<Set<String>> = _granters

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _walletAddress.value = p.getString(KEY_WALLET, null)
        _onboardingSeen.value = p.getBoolean(KEY_ONBOARDING_SEEN, false)
        _hidden.value = p.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        _follows.value = p.getStringSet(KEY_FOLLOWS, emptySet()) ?: emptySet()
        _starred.value = p.getStringSet(KEY_STARRED, emptySet()) ?: emptySet()
        _alertsEnabled.value = p.getBoolean(KEY_ALERTS, false)
        _dense.value = p.getBoolean(KEY_DENSE, false)
        _howToSeen.value = p.getBoolean(KEY_HOWTO_SEEN, false)
        _granters.value = p.getStringSet(KEY_GRANTERS, emptySet()) ?: emptySet()
    }

    fun toggleStar(id: String) { val s = if (id in _starred.value) _starred.value - id else _starred.value + id; prefs?.edit()?.putStringSet(KEY_STARRED, s)?.apply(); _starred.value = s }
    fun follow(pkg: String) { val s = _follows.value + pkg; prefs?.edit()?.putStringSet(KEY_FOLLOWS, s)?.apply(); _follows.value = s }
    fun unfollow(pkg: String) { val s = _follows.value - pkg; prefs?.edit()?.putStringSet(KEY_FOLLOWS, s)?.apply(); _follows.value = s }
    /** The period the ledger watch was last scheduled with; a change means UPDATE rather than KEEP. */
    var watchIntervalHours: Long
        get() = prefs?.getLong(KEY_WATCH_HOURS, 0L) ?: 0L
        set(v) { prefs?.edit()?.putLong(KEY_WATCH_HOURS, v)?.apply() }
    fun addGranter(w: String) { val s = _granters.value + w; prefs?.edit()?.putStringSet(KEY_GRANTERS, s)?.apply(); _granters.value = s }
    fun removeGranter(w: String) { val s = _granters.value - w; prefs?.edit()?.putStringSet(KEY_GRANTERS, s)?.apply(); _granters.value = s }
    fun seatLabels(): Map<String, String> = runCatching { JSONObject(prefs?.getString(KEY_SEAT_LABELS, "{}") ?: "{}").let { o -> o.keys().asSequence().associateWith { o.optString(it) } } }.getOrDefault(emptyMap())
    fun labelSeat(tag: String, wallet: String) { val all = runCatching { JSONObject(prefs?.getString(KEY_SEAT_LABELS, "{}") ?: "{}") }.getOrDefault(JSONObject()); all.put(tag, wallet); prefs?.edit()?.putString(KEY_SEAT_LABELS, all.toString())?.apply() }
    fun setDense(v: Boolean) { prefs?.edit()?.putBoolean(KEY_DENSE, v)?.apply(); _dense.value = v }
    fun setHowToSeen(v: Boolean) { prefs?.edit()?.putBoolean(KEY_HOWTO_SEEN, v)?.apply(); _howToSeen.value = v }
    fun setAlerts(v: Boolean) { prefs?.edit()?.putBoolean(KEY_ALERTS, v)?.apply(); _alertsEnabled.value = v }
    var lastNotifiedSignature: String?
        get() = prefs?.getString(KEY_LAST_NOTIFIED, null)
        set(v) { prefs?.edit()?.putString(KEY_LAST_NOTIFIED, v)?.apply() }

    fun setWallet(address: String?) { prefs?.edit()?.putString(KEY_WALLET, address)?.apply(); _walletAddress.value = address; if (address == null) authToken = null }

    /**
     * The MWA authorization token. Without it the adapter runs a FULL authorize for every single action, so the
     * wallet shows its "Connect" sheet before every flag, vote and reply — measured on device, 21 Sep 2026, where
     * one flag needed Connect, then Approve, then the Seed Vault double-tap. With it the adapter reauthorizes
     * silently and the wallet goes straight to the transaction. Cleared whenever the wallet is signed out.
     */
    var authToken: String?
        get() = prefs?.getString(KEY_AUTH_TOKEN, null)
        set(v) { prefs?.edit()?.putString(KEY_AUTH_TOKEN, v)?.apply() }
    fun setOnboardingSeen(v: Boolean) { prefs?.edit()?.putBoolean(KEY_ONBOARDING_SEEN, v)?.apply(); _onboardingSeen.value = v }

    fun hide(signature: String) { val s = _hidden.value + signature; prefs?.edit()?.putStringSet(KEY_HIDDEN, s)?.apply(); _hidden.value = s }
    fun unhide(signature: String) { val s = _hidden.value - signature; prefs?.edit()?.putStringSet(KEY_HIDDEN, s)?.apply(); _hidden.value = s }
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
    /**
     * The last verified-publisher result, so the FIRST ranking after a launch already knows whose review of their own
     * app not to count. Without it every cold start ranked with an empty map, then re-ranked once the websites had been
     * checked: the Toolkit read "4.5 · 2 signed" and then "4.0 · 1 signed" seconds later (seen 24 Sep 2026). This is a
     * starting point only — verifyClaims re-checks every claim against the live website and overwrites it.
     */
    fun loadVerifiedPublishers(): Map<String, Set<String>> = runCatching {
        val o = JSONObject(prefs?.getString(KEY_VERIFIED, "{}") ?: "{}")
        o.keys().asSequence().associateWith { k -> val a = o.getJSONArray(k); (0 until a.length()).map { a.getString(it) }.toSet() }
    }.getOrDefault(emptyMap())
    fun saveVerifiedPublishers(m: Map<String, Set<String>>) {
        val o = JSONObject(); m.forEach { (k, ws) -> o.put(k, org.json.JSONArray(ws.toList())) }
        prefs?.edit()?.putString(KEY_VERIFIED, o.toString())?.apply()
    }
    /**
     * The signed-in wallet's own dApp Store reviews from its last scan. A scan reads every installed/followed app's
     * reviews and takes minutes, so without this "My reviews" was empty until the user scanned again. Kept for ONE
     * wallet only; a different wallet reads back nothing.
     */
    fun loadMyStoreReviews(wallet: String): Pair<Map<String, com.undersigned.app.data.StoreReview>, Long>? = runCatching {
        val o = JSONObject(prefs?.getString(KEY_MY_STORE, null) ?: return null)
        if (o.optString("wallet") != wallet) return null
        val r = o.getJSONObject("reviews")
        r.keys().asSequence().associateWith { k -> val j = r.getJSONObject(k)
            com.undersigned.app.data.StoreReview(j.optString("id"), j.optString("createdAt"), j.optInt("rating"), j.optString("text").takeIf { j.has("text") },
                wallet, j.optString("domain").takeIf { j.has("domain") }, j.optString("reply").takeIf { j.has("reply") }) } to o.optLong("at")
    }.getOrNull()
    fun saveMyStoreReviews(wallet: String, reviews: Map<String, com.undersigned.app.data.StoreReview>) {
        val r = JSONObject(); reviews.forEach { (pkg, x) -> r.put(pkg, JSONObject().put("id", x.id).put("createdAt", x.createdAt).put("rating", x.rating)
            .apply { x.text?.let { put("text", it) }; x.domain?.let { put("domain", it) }; x.publisherReply?.let { put("reply", it) } }) }
        prefs?.edit()?.putString(KEY_MY_STORE, JSONObject().put("wallet", wallet).put("at", System.currentTimeMillis()).put("reviews", r).toString())?.apply()
    }
    fun clearFacts() { prefs?.edit()?.remove(KEY_FACTS)?.apply() }

    /** True the first time this (rounded) divergence is seen for the package; suppresses repeat alerts. */
    fun markDivergence(pkg: String, signed: Double, store: Double): Boolean {
        val key = "$pkg|${"%.1f".format(java.util.Locale.US, signed)}|${"%.1f".format(java.util.Locale.US, store)}"
        val all = runCatching { JSONObject(prefs?.getString(KEY_DIVERGENCE, "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (all.has(key)) return false
        all.put(key, System.currentTimeMillis()); prefs?.edit()?.putString(KEY_DIVERGENCE, all.toString())?.apply(); return true
    }

    /**
     * The store's star counts for [pkg] as this device saw them, oldest first (see protocol RatingSnapshots). At most one
     * snapshot per 20 hours per app, 120 per app, 400 apps; the least recently updated app is dropped first.
     */
    @Synchronized fun recordRatingSnapshot(pkg: String, byStar: List<Int>, now: Long = System.currentTimeMillis() / 1000) {
        if (byStar.size != 5) return
        val all = runCatching { JSONObject(prefs?.getString(KEY_RATING_SNAPS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val arr = all.optJSONArray(pkg) ?: org.json.JSONArray()
        val lastT = if (arr.length() > 0) arr.optJSONArray(arr.length() - 1)?.optLong(0) ?: 0L else 0L
        if (now - lastT < 20 * 3600) return
        arr.put(org.json.JSONArray().put(now).apply { byStar.forEach { put(it) } })
        while (arr.length() > 120) arr.remove(0)
        all.put(pkg, arr)
        if (all.length() > 400) {
            val oldest = all.keys().asSequence().minByOrNull { k -> all.optJSONArray(k)?.let { a -> a.optJSONArray(a.length() - 1)?.optLong(0) } ?: 0L }
            oldest?.let { all.remove(it) }
        }
        prefs?.edit()?.putString(KEY_RATING_SNAPS, all.toString())?.apply()
    }
    fun ratingSnapshots(pkg: String): List<com.undersigned.protocol.RatingSnapshots.Snapshot> = runCatching {
        val arr = JSONObject(prefs?.getString(KEY_RATING_SNAPS, "{}") ?: "{}").optJSONArray(pkg) ?: return emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONArray(i)?.let { r -> com.undersigned.protocol.RatingSnapshots.Snapshot(r.optLong(0), (1..5).map { r.optInt(it) }) } }
    }.getOrDefault(emptyList())
    /**
     * True when this is a NEW burst for [key] (app, kind, source): one that starts after the last alerted burst ended (with
     * a 3-day gap). The same burst growing or shifting keeps the mark and only extends its end. Stored as the end second.
     */
    @Synchronized fun markBurst(key: String, start: Long, end: Long): Boolean {
        val all = runCatching { JSONObject(prefs?.getString(KEY_BURST_MARKS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val lastEnd = all.optLong(key, Long.MIN_VALUE)
        val fresh = lastEnd == Long.MIN_VALUE || start > lastEnd + 3 * 86_400L
        all.put(key, maxOf(end, if (lastEnd == Long.MIN_VALUE) end else lastEnd))
        prefs?.edit()?.putString(KEY_BURST_MARKS, all.toString())?.apply(); return fresh
    }

    /** First time (ms) this device found [wallet] in the proof file at [url]; recorded on first sight, returned after. */
    @Synchronized fun proofFirstSeen(url: String, wallet: String): Long {
        val all = runCatching { JSONObject(prefs?.getString("proof_first_seen", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val k = "$url|$wallet"
        if (all.has(k)) return all.getLong(k)
        val now = System.currentTimeMillis(); all.put(k, now); prefs?.edit()?.putString("proof_first_seen", all.toString())?.apply(); return now
    }
    /** What a store build (package + versionCode) was read to say: the wallets its manifest names and its signer certificates. Null = never read. */
    fun storeBuild(pkg: String, version: Long): com.undersigned.protocol.Apk.Facts? = runCatching {
        val o = JSONObject(prefs?.getString("store_builds", "{}") ?: "{}").optJSONObject("$pkg|$version") ?: return null
        fun set(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        com.undersigned.protocol.Apk.Facts(set("d"), set("c"))
    }.getOrNull()
    @Synchronized fun saveStoreBuild(pkg: String, version: Long, f: com.undersigned.protocol.Apk.Facts) {
        val all = runCatching { JSONObject(prefs?.getString("store_builds", "{}") ?: "{}") }.getOrDefault(JSONObject())
        all.put("$pkg|$version", JSONObject().put("d", org.json.JSONArray(f.declared.toList())).put("c", org.json.JSONArray(f.certSha256.toList())))
        prefs?.edit()?.putString("store_builds", all.toString())?.apply()
    }

    fun proofSeenAt(url: String, wallet: String): Long? = runCatching { JSONObject(prefs?.getString("proof_first_seen", "{}") ?: "{}").let { if (it.has("$url|$wallet")) it.getLong("$url|$wallet") else null } }.getOrNull()

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
    /** The one-time "hear when the scan is done" sheet (design F8) has been shown. */
    var scanRationaleShown: Boolean
        get() = prefs?.getBoolean("scan_rationale_shown", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("scan_rationale_shown", v)?.apply() }
    /** Packages whose proven-claim alert was already posted for this wallet. */
    var provenNotified: Set<String>
        get() = prefs?.getStringSet("proven_notified", emptySet())?.toSet() ?: emptySet()
        set(v) { prefs?.edit()?.putStringSet("proven_notified", v)?.apply() }
    /** The weekly "not reviewed on this phone" reminder (opt-in, off by default). */
    /** Where the next dApp-list refresh continues the store-search sweep (0..675). */
    var catalogueSearchCursor: Int
        get() = prefs?.getInt("catalogue_search_cursor", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("catalogue_search_cursor", v)?.apply() }
    /** Pro: refresh the dApp list in the background every Monday morning (on by default; acts only while Pro is active). */
    var catalogueAuto: Boolean
        get() = prefs?.getBoolean("catalogue_auto", true) ?: true
        set(v) { prefs?.edit()?.putBoolean("catalogue_auto", v)?.apply() }
    var reviewReminder: Boolean
        get() = prefs?.getBoolean("review_reminder", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("review_reminder", v)?.apply() }
    /** Store watch (opt-in): the daily store-wide star-count snapshot. */
    var storeWatch: Boolean
        get() = prefs?.getBoolean("store_watch", false) ?: false
        set(v) { prefs?.edit()?.putBoolean("store_watch", v)?.apply() }
    /** Install history per package (see device/InstallHistory): {pkg: {s: [[from, to]], c: current, l: lastSeen, r: reinstalls}}. */
    var presenceJson: String?
        get() = prefs?.getString("install_presence", null)
        set(v) { prefs?.edit()?.putString("install_presence", v)?.apply() }

    fun clearAll() { prefs?.edit()?.clear()?.apply(); _walletAddress.value = null; _hidden.value = emptySet(); _onboardingSeen.value = false; _follows.value = emptySet(); _alertsEnabled.value = false; _dense.value = false; _howToSeen.value = false; _granters.value = emptySet() }
}
