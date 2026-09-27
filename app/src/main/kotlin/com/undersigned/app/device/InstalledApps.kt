package com.undersigned.app.device

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.undersigned.app.data.UserPreferences
import com.undersigned.protocol.SafetyFacts
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** A change this device observed between two looks at an installed store app. Stored locally, newest first. */
data class DeviceChange(val packageName: String, val at: Long, val versionCode: Long, val firstSeen: Boolean, val versionChanged: Boolean, val signerChanged: Boolean, val permsAdded: List<String>, val permsRemoved: List<String>)

/**
 * Facts the DEVICE can verify about installed apps, from PackageManager, never uploaded:
 * signing-certificate SHA-256, dangerous permissions, and their deltas against this device's
 * previous snapshot. Facts and changes only — the UI never turns them into a verdict.
 */
class InstalledApps(private val context: Context) {
    private val pm = context.packageManager

    fun installedPackages(): Set<String> = runCatching {
        pm.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    fun facts(packageName: String): SafetyFacts? = runCatching {
        val flags = PackageManager.GET_PERMISSIONS or (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)
        val pi = pm.getPackageInfo(packageName, flags)
        SafetyFacts(packageName, versionCodeOf(pi), signerSha256(pi), dangerousPermissions(pi))
    }.getOrNull()

    /** Snapshot every installed catalogue app, diff against the last snapshot, append changes, persist the new snapshot. */
    fun refreshSnapshot(cataloguePackages: Collection<String>): List<DeviceChange> {
        val installed = installedPackages()
        val now = System.currentTimeMillis()
        val prev = runCatching { JSONObject(UserPreferences.deviceSnapshotJson ?: "{}") }.getOrDefault(JSONObject())
        val next = JSONObject()
        val changes = ArrayList<DeviceChange>()
        for (pkg in cataloguePackages) {
            if (pkg !in installed) continue
            val f = facts(pkg) ?: continue
            next.put(pkg, JSONObject().put("vc", f.versionCode).put("sha", f.signerSha256).put("perms", JSONArray(f.dangerousPermissions)))
            val old = prev.optJSONObject(pkg)
            if (old == null) { changes.add(DeviceChange(pkg, now, f.versionCode, true, false, false, emptyList(), emptyList())); continue }
            val oldPerms = old.optJSONArray("perms")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val added = f.dangerousPermissions - oldPerms.toSet(); val removed = oldPerms - f.dangerousPermissions.toSet()
            val vcChanged = old.optLong("vc") != f.versionCode; val shaChanged = old.optString("sha") != f.signerSha256
            if (vcChanged || shaChanged || added.isNotEmpty() || removed.isNotEmpty())
                changes.add(DeviceChange(pkg, now, f.versionCode, false, vcChanged, shaChanged, added, removed))
        }
        UserPreferences.deviceSnapshotJson = next.toString()
        runCatching { observePresence(cataloguePackages, installed, now) }
        if (changes.isNotEmpty()) {
            val log = runCatching { JSONArray(UserPreferences.changesJson ?: "[]") }.getOrDefault(JSONArray())
            val merged = JSONArray()
            changes.forEach { c -> merged.put(JSONObject().put("p", c.packageName).put("at", c.at).put("vc", c.versionCode).put("first", c.firstSeen).put("vcc", c.versionChanged).put("sha", c.signerChanged).put("add", JSONArray(c.permsAdded)).put("rem", JSONArray(c.permsRemoved))) }
            for (i in 0 until minOf(log.length(), 200)) merged.put(log.get(i))
            UserPreferences.changesJson = merged.toString()
        }
        return changes
    }

    // ---- Install history (see InstallHistory) ----
    private fun loadPresence(): MutableMap<String, InstallHistory.Presence> = runCatching {
        val o = JSONObject(UserPreferences.presenceJson ?: "{}")
        o.keys().asSequence().associateWith { k -> val p = o.getJSONObject(k); val s = p.optJSONArray("s") ?: JSONArray()
            InstallHistory.Presence((0 until s.length()).map { i -> s.getJSONArray(i).let { it.getLong(0) to it.getLong(1) } }, if (p.has("c")) p.getLong("c") else null, p.optLong("l"), p.optInt("r")) }.toMutableMap()
    }.getOrDefault(HashMap())

    private fun savePresence(m: Map<String, InstallHistory.Presence>) {
        val o = JSONObject()
        m.forEach { (k, p) -> o.put(k, JSONObject().put("s", JSONArray(p.spans.map { JSONArray().put(it.first).put(it.second) })).put("l", p.lastSeen).put("r", p.reinstalls).apply { p.current?.let { put("c", it) } }) }
        UserPreferences.presenceJson = o.toString()
    }

    private fun observePresence(catalogue: Collection<String>, installed: Set<String>, now: Long) {
        val m = loadPresence()
        for (pkg in (catalogue.filter { it in installed } + m.keys).toSet()) {
            val fit = if (pkg in installed) firstInstallTime(pkg) else null
            InstallHistory.observe(m[pkg], fit, now)?.let { m[pkg] = it } ?: m.remove(pkg)
        }
        savePresence(m)
    }

    /** This phone's total installed time for [pkg] across reinstalls, and how many reinstalls it saw; null when never seen. */
    fun presence(pkg: String, now: Long = System.currentTimeMillis()): Pair<Long, Int>? {
        val prev = loadPresence()[pkg]
        val p = InstallHistory.observe(prev, firstInstallTime(pkg), now) ?: return null
        return InstallHistory.totalMs(p, now) to p.reinstalls
    }

    fun changeLog(): List<DeviceChange> = runCatching {
        val arr = JSONArray(UserPreferences.changesJson ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            DeviceChange(o.optString("p"), o.optLong("at"), o.optLong("vc"), o.optBoolean("first"), o.optBoolean("vcc"), o.optBoolean("sha"), list("add"), list("rem"))
        }
    }.getOrDefault(emptyList())

    /** The delta for one package = the most recent logged change for it, if any. */
    fun factsWithDelta(packageName: String): SafetyFacts? {
        val f = facts(packageName) ?: return null
        val c = changeLog().firstOrNull { it.packageName == packageName && !it.firstSeen } ?: return f
        return f.copy(delta = SafetyFacts.Delta(c.signerChanged, c.permsAdded, c.permsRemoved))
    }

    /** When this package was first installed on this phone (ms); reinstalling resets it. Null when not installed. */
    fun firstInstallTime(packageName: String): Long? = runCatching { pm.getPackageInfo(packageName, 0).firstInstallTime }.getOrNull()?.takeIf { it > 0 }

    fun appLabel(packageName: String): String? = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrNull()

    private fun versionCodeOf(pi: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()

    private fun signerSha256(pi: PackageInfo): String {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = pi.signingInfo ?: return ""
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else @Suppress("DEPRECATION") pi.signatures
        val first = sigs?.firstOrNull() ?: return ""
        return MessageDigest.getInstance("SHA-256").digest(first.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun dangerousPermissions(pi: PackageInfo): List<String> {
        val req = pi.requestedPermissions ?: return emptyList()
        return req.filter { p ->
            runCatching {
                val info = pm.getPermissionInfo(p, 0)
                val base = if (Build.VERSION.SDK_INT >= 28) info.protection else @Suppress("DEPRECATION") (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
                base == PermissionInfo.PROTECTION_DANGEROUS
            }.getOrDefault(false)
        }.map { it.removePrefix("android.permission.") }.sorted()
    }
}
