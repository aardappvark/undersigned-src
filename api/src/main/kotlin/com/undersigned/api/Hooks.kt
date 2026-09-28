package com.undersigned.api

import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.RatingSnapshots
import com.undersigned.protocol.ReviewBursts
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Alerts for pass holders, pushed as webhooks: a signed review, a reply, a publisher's store note, a burst, a version
 * or certificate change on the apps a hook watches. Each POST carries `X-Undersigned-Signature: sha256=<hex HMAC of the
 * body with the hook's secret>`, so the receiver can check it came from here. A hook belongs to a pass and stops when
 * the pass ends. Delivery is at-least-once with three tries; events carry ids so a receiver can drop repeats.
 */
class Hooks(private val db: Db, private val billing: Billing, private val post: (String, String, String) -> Int = ::httpPost) {
    data class Hook(val id: String, val key: String, val url: String, val secret: String, val events: Set<String>, val packages: Set<String>)
    class Invalid(message: String) : Exception(message)

    init {
        db.exec("CREATE TABLE IF NOT EXISTS hooks (id TEXT PRIMARY KEY, key TEXT NOT NULL, url TEXT NOT NULL, secret TEXT NOT NULL, events TEXT NOT NULL, packages TEXT NOT NULL, created INTEGER NOT NULL)")
        db.exec("CREATE TABLE IF NOT EXISTS hook_log (hook TEXT NOT NULL, event TEXT NOT NULL, status INTEGER, at INTEGER NOT NULL)")
    }

    fun create(key: String, url: String, events: Collection<String>, packages: Collection<String>): Hook {
        val ev = events.map { it.trim() }.filter { it.isNotEmpty() }.toSet().ifEmpty { EVENTS }
        if (!EVENTS.containsAll(ev)) throw Invalid("events must be among ${EVENTS.joinToString()}")
        if (packages.isEmpty() || packages.size > 50) throw Invalid("name 1 to 50 packages to watch")
        checkUrl(url)
        if (list(key).size >= 10) throw Invalid("at most 10 hooks per pass")
        val h = Hook("hk_" + Base58.encode(ByteArray(12).also { SecureRandom().nextBytes(it) }), key, url, Base58.encode(ByteArray(24).also { SecureRandom().nextBytes(it) }), ev, packages.toSet())
        db.exec("INSERT INTO hooks (id, key, url, secret, events, packages, created) VALUES (?, ?, ?, ?, ?, ?, ?)", h.id, key, url, h.secret, ev.joinToString(","), h.packages.joinToString(","), System.currentTimeMillis() / 1000)
        return h
    }

    fun list(key: String): List<Hook> = db.query("SELECT id, key, url, secret, events, packages FROM hooks WHERE key = ?", key) { row(it) }
    fun delete(key: String, id: String): Boolean = db.exec("DELETE FROM hooks WHERE key = ? AND id = ?", key, id) == 1
    private fun row(rs: java.sql.ResultSet) = Hook(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5).split(',').toSet(), rs.getString(6).split(',').toSet())

    /**
     * Builds the events that happened since the last run and delivers them. [newRecords] are records the indexer added;
     * [changes] are store changes since [since]; bursts are rating bursts whose window ends after the last run.
     */
    fun dispatch(newRecords: List<LedgerEntry>, changes: List<JSONObject>, bursts: List<Pair<String, ReviewBursts.Burst>>, byId: (String) -> LedgerEntry? = { id -> newRecords.firstOrNull { it.signature == id } }) {
        val events = ArrayList<Pair<String, JSONObject>>()   // package → event
        for (e in newRecords) {
            val (type, pkg) = when (val m = e.memo) {
                is ReviewMemo.Review -> "review" to m.packageName
                is ReviewMemo.StoreNote -> "store_note" to m.packageName
                is ReviewMemo.Reply -> "reply" to (byId(m.reviewSignature)?.memo as? ReviewMemo.Review)?.packageName
                else -> continue
            }
            pkg ?: continue
            events += pkg to JSONObject().put("id", "rec:${e.signature}").put("type", type).put("package", pkg).put("record", e.signature).put("signer", e.signer).put("time", e.blockTime)
        }
        for (c in changes) events += c.getString("package") to JSONObject().put("id", "chg:${c.getString("package")}:${c.getString("kind")}:${c.optString("new")}")
            .put("type", c.getString("kind")).put("package", c.getString("package")).put("old", c.opt("old")).put("new", c.opt("new")).put("time", c.optLong("time"))
        for ((pkg, b) in bursts) events += pkg to JSONObject().put("id", "bst:$pkg:${b.kind}:${b.start}").put("type", "burst").put("package", pkg).put("kind", b.kind.name.lowercase())
            .put("start", b.start).put("end", b.end).put("count", b.count).put("expected", Math.round(b.expected * 100) / 100.0)
        if (events.isEmpty()) return
        val hooks = db.query("SELECT id, key, url, secret, events, packages FROM hooks") { row(it) }
        for (h in hooks) {
            val p = billing.pass(h.key)
            if (p == null || p.endsAt <= System.currentTimeMillis() / 1000) continue      // an ended pass stops its hooks
            val mine = events.filter { (pkg, ev) -> pkg in h.packages && ev.getString("type") in h.events }.map { it.second }
            if (mine.isEmpty()) continue
            // Checked again at delivery: a name that resolved publicly when the hook was made may not any more.
            if (runCatching { checkUrl(h.url) }.isFailure) { db.exec("INSERT INTO hook_log (hook, event, status, at) VALUES (?, ?, ?, ?)", h.id, "refused-url", -1, System.currentTimeMillis() / 1000); continue }
            val body = JSONObject().put("hook", h.id).put("events", JSONArray(mine)).put("sent_at", System.currentTimeMillis() / 1000).toString()
            var status = 0
            for (attempt in 0 until 3) { status = runCatching { post(h.url, body, sign(h.secret, body)) }.getOrDefault(0); if (status in 200..299) break; Thread.sleep(1000L shl attempt) }
            db.exec("INSERT INTO hook_log (hook, event, status, at) VALUES (?, ?, ?, ?)", h.id, mine.joinToString(",") { it.getString("id") }.take(500), status, System.currentTimeMillis() / 1000)
        }
    }

    /**
     * One round after the indexer rebuilt: [newRecords] from it, store changes since the last round, and rating bursts not
     * sent before (their ids are remembered, so a burst that keeps growing is announced once).
     */
    fun tick(indexer: Indexer, newRecords: List<LedgerEntry>) {
        val last = db.get("hooks_last")?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)
        val now = System.currentTimeMillis() / 1000
        val changes = db.query("SELECT pkg, at, kind, old, new FROM changes WHERE at > ?", last) { rs ->
            JSONObject().put("package", rs.getString(1)).put("time", rs.getLong(2)).put("kind", rs.getString(3)).put("old", rs.getString(4)).put("new", rs.getString(5)) }
        val sent = db.get("hooks_bursts")?.split('\n')?.toMutableSet() ?: mutableSetOf()
        val bursts = freshBursts(indexer, now - 3 * ReviewBursts.DAY).filter { (pkg, b) -> "$pkg:${b.kind}:${b.start}" !in sent }
        val byId = indexer.state.entries.associateBy { it.signature }
        dispatch(newRecords, changes, bursts) { byId[it] }
        sent += bursts.map { (pkg, b) -> "$pkg:${b.kind}:${b.start}" }
        db.put("hooks_bursts", sent.toList().takeLast(5000).joinToString("\n"))
        db.put("hooks_last", now.toString())
    }

    companion object {
        val EVENTS = setOf("review", "reply", "store_note", "burst", "version", "certificate")

        fun sign(secret: String, body: String): String = "sha256=" + Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")); doFinal(body.toByteArray()) }.joinToString("") { "%02x".format(it) }

        /**
         * https only, and never an address inside a private network (a webhook must not become a way to reach this
         * server's neighbours). While the server itself is loopback-only, http to localhost is allowed for testing.
         */
        fun checkUrl(url: String, loopbackServer: Boolean = Config.HOST == "127.0.0.1") {
            val u = runCatching { URI(url) }.getOrNull() ?: throw Invalid("not a URL")
            val host = u.host ?: throw Invalid("the URL has no host")
            val local = host == "localhost" || host == "127.0.0.1" || host == "::1"
            if (loopbackServer && local && (u.scheme == "http" || u.scheme == "https")) return
            if (u.scheme != "https") throw Invalid("webhooks must use https")
            val addrs = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { throw Invalid("the host does not resolve") }
            if (addrs.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc) })
                throw Invalid("webhooks cannot point into a private network")
        }

        fun httpPost(url: String, body: String, signature: String): Int {
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.instanceFollowRedirects = false; c.connectTimeout = 5000; c.readTimeout = 8000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "Undersigned-Hooks/1"); c.setRequestProperty("X-Undersigned-Signature", signature)
                c.outputStream.use { it.write(body.toByteArray()) }
                return c.responseCode
            } finally { c.disconnect() }
        }

        /** Rating bursts whose window ended after [since] (epoch seconds), for the burst event. */
        fun freshBursts(indexer: Indexer, since: Long): List<Pair<String, ReviewBursts.Burst>> = indexer.ratingPackages().flatMap { pkg ->
            ReviewBursts.find(RatingSnapshots.toPoints(indexer.snapshots(pkg)), ReviewBursts.MIN_SNAPSHOT_DAYS).bursts.filter { it.end > since }.map { pkg to it }
        }
    }
}
