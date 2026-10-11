package com.undersigned.api

import com.undersigned.protocol.LedgerEntry
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
 * or certificate change on the apps a hook watches. Each POST carries `X-Undersigned-Signature-V2: t=<unix seconds>,v1=<hex
 * HMAC-SHA256 of "<t>.<body>" with the hook's secret>` (reject a t more than 5 minutes old: a replay), plus the original
 * `X-Undersigned-Signature: sha256=<hex HMAC of the body>` for receivers built before 9 Oct 2026. A hook belongs to a pass
 * and stops when the pass ends. Events go to an outbox and a worker delivers them: six tries over about 24 hours
 * ([RETRY_DELAYS]; a receiver that is down, refuses or times out is tried again), so a dead receiver never holds up
 * indexing and a restart mid-delivery loses nothing. Events carry ids so a receiver can drop repeats. Every attempt is
 * logged for the hook's owner (`GET /v1/hooks/{id}/deliveries`); `POST /v1/hooks/{id}/test` sends a signed ping now.
 */
class Hooks(private val db: Db, private val billing: Billing, private val post: (String, String, Map<String, String>) -> Int = ::httpPost) {
    data class Hook(val id: String, val key: String, val url: String, val secret: String, val events: Set<String>, val packages: Set<String>)
    class Invalid(message: String) : Exception(message)

    init {
        db.exec("CREATE TABLE IF NOT EXISTS hooks (id TEXT PRIMARY KEY, key TEXT NOT NULL, url TEXT NOT NULL, secret TEXT NOT NULL, events TEXT NOT NULL, packages TEXT NOT NULL, created INTEGER NOT NULL)")
        db.exec("CREATE TABLE IF NOT EXISTS hook_log (hook TEXT NOT NULL, event TEXT NOT NULL, status INTEGER, at INTEGER NOT NULL)")
        db.exec("CREATE TABLE IF NOT EXISTS hook_outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, hook TEXT NOT NULL, body TEXT NOT NULL, attempts INTEGER NOT NULL, next_at INTEGER NOT NULL, created INTEGER NOT NULL)")
    }

    fun create(key: String, url: String, events: Collection<String>, packages: Collection<String>): Hook = createByHash(Billing.hashKey(key), url, events, packages)
    private fun createByHash(key: String, url: String, events: Collection<String>, packages: Collection<String>): Hook {
        val ev = events.map { it.trim() }.filter { it.isNotEmpty() }.toSet().ifEmpty { EVENTS }
        if (!EVENTS.containsAll(ev)) throw Invalid("events must be among ${EVENTS.joinToString()}")
        if (packages.isEmpty() || packages.size > 50) throw Invalid("name 1 to 50 packages to watch")
        checkUrl(url)
        if (list(key).size >= 10) throw Invalid("at most 10 hooks per pass")
        val h = Hook("hk_" + Base58.encode(ByteArray(12).also { SecureRandom().nextBytes(it) }), key, url, Base58.encode(ByteArray(24).also { SecureRandom().nextBytes(it) }), ev, packages.toSet())
        db.exec("INSERT INTO hooks (id, key, url, secret, events, packages, created) VALUES (?, ?, ?, ?, ?, ?, ?)", h.id, key, url, h.secret, ev.joinToString(","), h.packages.joinToString(","), System.currentTimeMillis() / 1000)
        return h
    }

    fun list(key: String): List<Hook> = db.query("SELECT id, key, url, secret, events, packages FROM hooks WHERE key = ?", Billing.hashKey(key)) { row(it) }
    fun delete(key: String, id: String): Boolean = db.exec("DELETE FROM hooks WHERE key = ? AND id = ?", Billing.hashKey(key), id) == 1
    fun count(): Int = db.query("SELECT count(*) FROM hooks") { it.getInt(1) }.first()
    /** Deliveries that ended in failure in the last 24 h, for /v1/health. */
    fun failures24h(): Int = db.query("SELECT count(*) FROM hook_log WHERE at > ? AND (status < 200 OR status >= 300)", System.currentTimeMillis() / 1000 - 86_400) { it.getInt(1) }.first()
    private fun row(rs: java.sql.ResultSet) = Hook(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5).split(',').toSet(), rs.getString(6).split(',').toSet())

    /** Builds the events for what happened and queues them for every hook that watches them. Returns how many bodies were queued. */
    fun dispatch(newRecords: List<LedgerEntry>, changes: List<JSONObject>, bursts: List<Pair<String, ReviewBursts.Burst>>, now: Long = System.currentTimeMillis() / 1000, byId: (String) -> LedgerEntry? = { id -> newRecords.firstOrNull { it.signature == id } }): Int {
        val events = ArrayList<Pair<String, JSONObject>>()   // package → event
        for (e in newRecords) {
            val (type, pkg) = when (val m = e.memo) {
                is ReviewMemo.Review -> "review" to m.packageName
                is ReviewMemo.StoreNote -> "store_note" to m.packageName
                is ReviewMemo.Reply -> "reply" to (byId(m.reviewSignature)?.memo as? ReviewMemo.Review)?.packageName
                is ReviewMemo.Flag -> "flag" to (byId(m.recordId)?.memo as? ReviewMemo.Review)?.packageName
                is ReviewMemo.Claim -> "claim" to m.packageName
                // The second round (11 Oct 2026): a publisher's "fixed in version N" and a reviewer's follow-up, for the plan's webhooks.
                is ReviewMemo.Fixed -> "fixed" to (byId(m.reviewSignature)?.memo as? ReviewMemo.Review)?.packageName
                is ReviewMemo.Ack -> "decision" to (byId(m.reviewSignature)?.memo as? ReviewMemo.Review)?.packageName
                is ReviewMemo.FollowUp -> "follow_up" to (byId(m.originalSignature)?.memo as? ReviewMemo.Review)?.packageName
                else -> continue
            }
            pkg ?: continue
            events += pkg to JSONObject().put("id", "rec:${e.signature}").put("type", type).put("package", pkg).put("record", e.signature).put("signer", e.signer).put("time", e.blockTime)
        }
        for (c in changes) events += c.getString("package") to JSONObject().put("id", "chg:${c.getString("package")}:${c.getString("kind")}:${c.optString("new")}")
            .put("type", c.getString("kind")).put("package", c.getString("package")).put("old", c.opt("old")).put("new", c.opt("new")).put("time", c.optLong("time"))
        for ((pkg, b) in bursts) events += pkg to JSONObject().put("id", "bst:$pkg:${b.kind}:${b.start}").put("type", "burst").put("package", pkg).put("kind", b.kind.name.lowercase())
            .put("start", b.start).put("end", b.end).put("count", b.count).put("expected", Math.round(b.expected * 100) / 100.0)
        if (events.isEmpty()) return 0
        var queued = 0
        for (h in db.query("SELECT id, key, url, secret, events, packages FROM hooks") { row(it) }) {
            val p = billing.passByHash(h.key)           // hooks store the key's hash
            if (p == null || p.endsAt <= now) continue      // an ended pass stops its hooks
            val mine = events.filter { (pkg, ev) -> pkg in h.packages && ev.getString("type") in h.events }.map { it.second }
            if (mine.isEmpty()) continue
            val body = JSONObject().put("hook", h.id).put("events", JSONArray(mine)).put("sent_at", now).toString()
            db.exec("INSERT INTO hook_outbox (hook, body, attempts, next_at, created) VALUES (?, ?, 0, ?, ?)", h.id, body, now, now); queued++
        }
        return queued
    }

    /** Delivers everything due in the outbox: one POST per row, up to [RETRY_DELAYS].size + 1 tries, every attempt logged. Returns rows handled. */
    fun deliverDue(now: Long = System.currentTimeMillis() / 1000): Int {
        val due = db.query("SELECT o.id, o.hook, o.body, o.attempts, h.url, h.secret FROM hook_outbox o JOIN hooks h ON h.id = o.hook WHERE o.next_at <= ? ORDER BY o.id LIMIT 50", now) {
            listOf(it.getLong(1), it.getString(2), it.getString(3), it.getInt(4), it.getString(5), it.getString(6)) }
        // Rows whose hook was deleted go nowhere.
        db.exec("DELETE FROM hook_outbox WHERE hook NOT IN (SELECT id FROM hooks)")
        for (r in due) {
            val id = r[0] as Long; val hook = r[1] as String; val body = r[2] as String; val attempts = r[3] as Int; val url = r[4] as String; val secret = r[5] as String
            // Checked again at delivery: a name that resolved publicly when the hook was made may not any more.
            // -1: the URL now points into a private network (never retried). 0: no answer (down, refused, timed out): retried.
            val status = if (runCatching { checkUrl(url) }.isFailure) -1 else runCatching { post(url, body, headers(secret, body, now)) }.getOrDefault(0)
            val ids = runCatching { JSONObject(body).getJSONArray("events").let { a -> (0 until a.length()).joinToString(",") { a.getJSONObject(it).getString("id") } } }.getOrDefault("?").take(500)
            db.exec("INSERT INTO hook_log (hook, event, status, at) VALUES (?, ?, ?, ?)", hook, ids, status, now)
            if (status in 200..299 || status == -1 || attempts >= RETRY_DELAYS.size) db.exec("DELETE FROM hook_outbox WHERE id = ?", id)
            else db.exec("UPDATE hook_outbox SET attempts = ?, next_at = ? WHERE id = ?", attempts + 1, now + RETRY_DELAYS[attempts], id)
        }
        db.exec("DELETE FROM hook_log WHERE at < ?", now - 30L * 86_400)   // a month of history is enough
        return due.size
    }

    /** A signed ping to one of the caller's hooks, now, outside the outbox: the receiver's answer, or null when not theirs. */
    fun test(key: String, id: String, now: Long = System.currentTimeMillis() / 1000): Int? {
        val h = list(key).firstOrNull { it.id == id } ?: return null
        val body = JSONObject().put("hook", h.id).put("sent_at", now).put("test", true)
            .put("events", org.json.JSONArray().put(JSONObject().put("id", "test-$now").put("type", "ping"))).toString()
        val status = if (runCatching { checkUrl(h.url) }.isFailure) -1 else runCatching { post(h.url, body, headers(h.secret, body, now)) }.getOrDefault(0)
        db.exec("INSERT INTO hook_log (hook, event, status, at) VALUES (?, ?, ?, ?)", h.id, "test-$now", status, now)
        return status
    }

    /** The last 100 delivery attempts to one of the caller's hooks, newest first; null when the hook is not theirs. */
    fun deliveries(key: String, id: String): org.json.JSONArray? {
        if (list(key).none { it.id == id }) return null
        return org.json.JSONArray(db.query("SELECT event, status, at FROM hook_log WHERE hook = ? ORDER BY at DESC LIMIT 100", id) { rs ->
            JSONObject().put("events", rs.getString(1)).put("status", rs.getInt(2)).put("at", rs.getLong(3))
                .put("delivered", rs.getInt(2) in 200..299) })
    }

    /**
     * One round after the indexer rebuilt: [newRecords] from it, store changes since the last round, and rating bursts not
     * sent before (their ids are remembered, so a burst that keeps growing is announced once). Nothing runs when no hook exists.
     */
    /** Set by the server: this week's digest (Views.digest), sent once a week to hooks that asked for "digest". */
    var weeklyDigest: (() -> JSONObject?)? = null

    /** Queues this week's digest for every hook subscribed to "digest", once per ISO week, from Monday. */
    private fun views_weekKey(d: java.time.ZonedDateTime) = "%d-W%02d".format(d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR), d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun digestTick(now: Long = System.currentTimeMillis() / 1000) {
        val d = java.time.Instant.ofEpochSecond(now).atZone(java.time.ZoneOffset.UTC)
        // Once per ISO week, at the first round of the week (normally just after midnight UTC on Monday).
        if (db.get("hooks_digest_week") == views_weekKey(d)) return
        val dg = weeklyDigest?.invoke() ?: return
        if (db.get("hooks_digest_week") == dg.getString("week")) return
        val targets = db.query("SELECT id, key, events FROM hooks") { listOf(it.getString(1), it.getString(2), it.getString(3)) }
            .filter { "digest" in it[2].split(',') && billing.passByHash(it[1])?.let { p -> p.endsAt > now } == true }
        targets.forEach { t ->
            val body = JSONObject().put("hook", t[0]).put("sent_at", now)
                .put("events", org.json.JSONArray().put(JSONObject(dg.toString()).put("id", "digest-${dg.getString("week")}").put("type", "digest"))).toString()
            db.exec("INSERT INTO hook_outbox (hook, body, attempts, next_at, created) VALUES (?, ?, 0, ?, ?)", t[0], body, now, now)
        }
        db.put("hooks_digest_week", dg.getString("week"))
    }

    fun tick(indexer: Indexer, newRecords: List<LedgerEntry>) {
        val now = System.currentTimeMillis() / 1000
        runCatching { digestTick(now) }.onFailure { log("hooks digest: ${it.message?.take(80)}") }
        if (count() == 0) { db.put("hooks_last", now.toString()); return }
        val last = db.get("hooks_last")?.toLongOrNull() ?: now
        val changes = db.query("SELECT pkg, at, kind, old, new FROM changes WHERE at > ?", last) { rs ->
            JSONObject().put("package", rs.getString(1)).put("time", rs.getLong(2)).put("kind", rs.getString(3)).put("old", rs.getString(4)).put("new", rs.getString(5)) }
        val sent = db.get("hooks_bursts")?.split('\n')?.toMutableSet() ?: mutableSetOf()
        val bursts = indexer.watchHits().filter { it.burst.end > now - 3 * ReviewBursts.DAY }.map { it.pkg to it.burst }.filter { (pkg, b) -> "$pkg:${b.kind}:${b.start}" !in sent }
        val byId = indexer.state.entries.associateBy { it.signature }
        dispatch(newRecords, changes, bursts, now) { byId[it] }
        sent += bursts.map { (pkg, b) -> "$pkg:${b.kind}:${b.start}" }
        db.put("hooks_bursts", sent.toList().takeLast(5000).joinToString("\n"))
        db.put("hooks_last", now.toString())
    }

    /** The delivery worker: every 20 s, whatever is due. Separate from indexing, so a dead receiver stalls nothing else. */
    fun startWorker() {
        Thread({ while (true) { runCatching { deliverDue() }.onFailure { log("hooks: ${it.message?.take(80)}") }; Thread.sleep(20_000) } }, "hooks").apply { isDaemon = true }.start()
    }

    companion object {
        val EVENTS = setOf("review", "reply", "store_note", "flag", "claim", "fixed", "follow_up", "burst", "version", "release", "certificate", "digest")

        /** Seconds before each retry: 1 min, 5 min, 30 min, 2 h, 6 h, 15 h (six tries over about 24 hours). */
        val RETRY_DELAYS = longArrayOf(60, 300, 1_800, 7_200, 21_600, 54_000)

        /** Both signatures: V2 binds the timestamp (replay-safe), the original stays for receivers built before it. */
        fun headers(secret: String, body: String, t: Long): Map<String, String> = mapOf(
            "X-Undersigned-Signature" to sign(secret, body),
            "X-Undersigned-Timestamp" to t.toString(),
            "X-Undersigned-Signature-V2" to "t=$t,v1=" + sign(secret, "$t.$body").removePrefix("sha256="))

        fun sign(secret: String, body: String): String = "sha256=" + Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")); doFinal(body.toByteArray()) }.joinToString("") { "%02x".format(it) }

        /**
         * https only, and never an address inside a private network (a webhook must not become a way to reach this
         * server's neighbours): loopback, RFC 1918, link-local, CGNAT/Tailscale 100.64/10, multicast, unspecified, IPv6
         * unique-local. On a private loopback-only server, http to localhost is allowed for testing. The JVM's DNS cache
         * (set to 60 s in main) means the delivery that follows a check reuses the checked resolution.
         */
        fun checkUrl(url: String, loopbackServer: Boolean = Config.HOOK_LOCAL_OK) {
            val u = runCatching { URI(url) }.getOrNull() ?: throw Invalid("not a URL")
            val host = u.host ?: throw Invalid("the URL has no host")
            val local = host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
            if (loopbackServer && local && (u.scheme == "http" || u.scheme == "https")) return
            if (u.scheme != "https") throw Invalid("webhooks must use https")
            val addrs = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { throw Invalid("the host does not resolve") }
            if (addrs.any { isPrivate(it) }) throw Invalid("webhooks cannot point into a private network")
        }

        fun isPrivate(a: InetAddress): Boolean {
            if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress) return true
            val b = a.address
            if (b.size == 4) return (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64      // 100.64.0.0/10
            return b.size == 16 && (b[0].toInt() and 0xfe) == 0xfc                                        // fc00::/7
        }

        fun httpPost(url: String, body: String, headers: Map<String, String>): Int {
            val u = URI(url).toURL(); require(u.protocol == "https") { "https only" }
            require(InetAddress.getAllByName(u.host).none { isPrivate(it) }) { "private destination" }   // re-checked at delivery, not only at registration (re-audit, 12 Oct 2026)
            val c = u.openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.instanceFollowRedirects = false; c.connectTimeout = 5000; c.readTimeout = 8000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "Undersigned-Hooks/2"); headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                c.outputStream.use { it.write(body.toByteArray()) }
                return c.responseCode
            } finally { c.disconnect() }
        }
    }
}
