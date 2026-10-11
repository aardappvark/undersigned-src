package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Review analytics across every source (owner, 12 Oct 2026: "filter between reviews on our dApp, organic, pro users etc
 * and the actual dAppstore itself … pulling data from all sources … beautiful graphs: by category, by dApp, change over
 * time"). Two kinds of row, always told apart by [Rev.cls]:
 *
 *  - STORE: the dApp Store's own public reviews, read by the paced crawl into `STORE_REVIEWS_DIR/reviews*.jsonl`
 *    (one JSON object a line: package, id, rating, created, text, domain, wallet, reply). Deduplicated by id. Only
 *    aggregates leave this server; no store review text is republished here.
 *  - SIGNED: the ledger's live reviews, each tagged with the classes the protocol already computes: `organic` (a
 *    Seeker wallet, counted, not promoted), `pro` (the reviewer held Pro when signing), `promoted` (the author paid to
 *    promote it), `quick` (no wallet; never counted), `other` (a wallet without a Genesis Token; shown, never counted),
 *    `ours` (the operator's own wallets; labelled, never counted).
 *
 * Everything is counts, means and shares; nothing here weighs a review, and a store review is never mixed into a
 * signed figure. Answers are cached by [Views.memo5]; the store file is re-read when it changes (at most every 10 min).
 */
class ReviewAnalytics(private val dir: File, private val categories: () -> Map<String, String?>, private val names: () -> Map<String, String>,
                      private val signed: () -> List<Rev>) {

    /** One review, from either source. [time] epoch seconds; [cls] its source classes ("store", or any of the signed classes). */
    data class Rev(val pkg: String, val rating: Int, val time: Long, val words: Int, val skr: Boolean, val replied: Boolean, val cls: Set<String>)
    data class Point(val bucket: String, val reviews: Int, val avg: Double)

    companion object {
        val SOURCES = listOf("store", "all", "organic", "pro", "promoted", "quick", "other", "ours")
        const val SHORT_WORDS = 3
        fun label(source: String) = when (source) {
            "store" -> "dApp Store reviews"; "all" -> "signed reviews, every wallet"; "organic" -> "organic signed reviews"
            "pro" -> "Pro members' signed reviews"; "promoted" -> "promoted signed reviews"; "quick" -> "quick reviews (no wallet)"
            "other" -> "signed by wallets without a Genesis Token"; "ours" -> "Undersigned's own wallets"; else -> source
        }
        private fun words(text: String?): Int = text?.trim()?.split(Regex("\\s+"))?.count { it.any(Char::isLetterOrDigit) } ?: 0
    }

    private class Cached(val stamp: String, val at: Long, val revs: List<Rev>)
    @Volatile private var cache: Cached? = null

    private fun files(): List<File> = dir.listFiles { f -> f.name.startsWith("reviews") && f.name.endsWith(".jsonl") }?.sortedBy { it.name }.orEmpty()

    /** The store rows, deduplicated by review id; re-read when any file changed and ten minutes passed. */
    fun store(): List<Rev> {
        val fs = files(); val stamp = fs.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" }
        cache?.let { c -> if (c.stamp == stamp || System.currentTimeMillis() - c.at < 600_000) return c.revs }
        val seen = HashSet<String>(); val out = ArrayList<Rev>()
        for (f in fs) f.bufferedReader().useLines { lines ->
            for (l in lines) {
                val o = runCatching { JSONObject(l) }.getOrNull() ?: continue
                val id = o.optString("id"); if (id.isEmpty() || !seen.add(id)) continue
                val t = runCatching { java.time.Instant.parse(o.optString("created")).epochSecond }.getOrNull() ?: continue
                out += Rev(o.optString("package"), o.optInt("rating"), t, words(o.optString("text", null)), o.optString("domain").isNotEmpty(),
                    !o.isNull("reply") && o.optString("reply").isNotEmpty(), setOf("store"))
            }
        }
        cache = Cached(stamp, System.currentTimeMillis(), out)
        return out
    }

    fun rows(source: String): List<Rev> = if (source == "store") store() else signed().filter { source == "all" || source in it.cls }

    private fun inWindow(rows: List<Rev>, window: String, now: Long): List<Rev> { val from = Analytics.windowStart(window, now); return rows.filter { it.time >= from } }
    private fun avg(l: List<Rev>) = if (l.isEmpty()) 0.0 else Analytics.r2(l.sumOf { it.rating }.toDouble() / l.size)
    private fun share(l: List<Rev>, p: (Rev) -> Boolean) = if (l.isEmpty()) 0.0 else Analytics.r2(100.0 * l.count(p) / l.size)
    /** Change of volume: the last 30 days against the 30 before (or the window against the one before when shorter than 60 days). */
    private fun change(all: List<Rev>, window: String, now: Long): Double? {
        val span = when (window) { "7d" -> 7 * 86_400L; "30d" -> 30 * 86_400L; else -> 30 * 86_400L }
        val cur = all.count { it.time >= now - span }; val prev = all.count { it.time >= now - 2 * span && it.time < now - span }
        return if (prev == 0) null else Analytics.r2(100.0 * (cur - prev) / prev)
    }

    /** The whole answer for one source: totals, stars, categories, apps, series. */
    fun summary(source: String, window: String, category: String?, pkg: String?, by: String, appLimit: Int, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val cats = categories(); val nm = names()
        var all = rows(source)
        if (category != null) all = all.filter { cats[it.pkg].equals(category, true) }
        if (pkg != null) all = all.filter { it.pkg == pkg }
        val l = inWindow(all, window, now)
        val o = JSONObject().put("source", source).put("label", label(source)).put("window", window).put("by", by)
            .put("category", category ?: JSONObject.NULL).put("package", pkg ?: JSONObject.NULL)
            .put("reviews", l.size).put("apps", l.map { it.pkg }.toSet().size).put("mean", avg(l))
            .put("stars", JSONArray((1..5).map { s -> l.count { it.rating == s } }))
            .put("replied_share", share(l) { it.replied }).put("short_share", share(l) { it.words <= SHORT_WORDS })
            .put("with_text_share", share(l) { it.words > 0 }).put("change", change(all, window, now) ?: JSONObject.NULL)
        if (source == "store") o.put("skr_share", share(l) { it.skr })
        if (category == null && pkg == null) o.put("categories", JSONArray(categoriesRows(l, all, window, now, cats)))
        if (pkg == null) o.put("top_apps", JSONArray(appRows(l, all, window, now, cats, nm, appLimit)))
        o.put("series", JSONArray(series(all, window, by, now).map { p -> JSONObject().put("bucket", p.bucket).put("reviews", p.reviews).put("mean", p.avg) }))
        return o
    }

    fun categoriesRows(l: List<Rev>, all: List<Rev>, window: String, now: Long, cats: Map<String, String?>): List<JSONObject> =
        l.groupBy { cats[it.pkg] ?: "Uncategorised" }.entries.sortedByDescending { it.value.size }.map { (c, g) ->
            JSONObject().put("category", c).put("reviews", g.size).put("apps", g.map { it.pkg }.toSet().size).put("mean", avg(g))
                .put("stars", JSONArray((1..5).map { s -> g.count { it.rating == s } }))
                .put("replied_share", share(g) { it.replied }).put("short_share", share(g) { it.words <= SHORT_WORDS })
                .put("change", change(all.filter { (cats[it.pkg] ?: "Uncategorised") == c }, window, now) ?: JSONObject.NULL)
        }

    fun appRows(l: List<Rev>, all: List<Rev>, window: String, now: Long, cats: Map<String, String?>, nm: Map<String, String>, limit: Int): List<JSONObject> =
        l.groupBy { it.pkg }.entries.sortedByDescending { it.value.size }.take(limit).mapIndexed { i, (p, g) ->
            JSONObject().put("rank", i + 1).put("package", p).put("name", nm[p] ?: p).put("category", cats[p] ?: JSONObject.NULL)
                .put("reviews", g.size).put("mean", avg(g)).put("replied_share", share(g) { it.replied }).put("short_share", share(g) { it.words <= SHORT_WORDS })
                .put("change", change(all.filter { it.pkg == p }, window, now) ?: JSONObject.NULL)
        }

    /** Reviews and mean stars per bucket across the window (empty buckets kept, so the line is honest about quiet weeks). */
    fun series(all: List<Rev>, window: String, by: String, now: Long): List<Point> {
        val first = all.minOfOrNull { it.time } ?: now
        val from = maxOf(Analytics.windowStart(window, now), if (window == "all") first else 0L)
        val grouped = all.filter { it.time >= from }.groupBy { Analytics.bucket(it.time, by) }
        val keys = LinkedHashSet<String>()
        if (by == "month") { var d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(from), java.time.ZoneOffset.UTC).withDayOfMonth(1)
            val end = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(now), java.time.ZoneOffset.UTC)
            while (!d.isAfter(end) && keys.size < 600) { keys += d.toString().take(7); d = d.plusMonths(1) } }
        else { val step = if (by == "day") 86_400L else 7 * 86_400L; var t = from; while (t <= now && keys.size < 2000) { keys += Analytics.bucket(t, by); t += step }; keys += Analytics.bucket(now, by) }
        val pts = keys.map { k -> val g = grouped[k].orEmpty(); Point(k, g.size, avg(g)) }
        // A handful of stray early-dated rows would stretch "all time" over empty years: start where 1% of the reviews have arrived.
        if (window != "all") return pts
        val total = pts.sumOf { it.reviews }; var cum = 0; var start = 0
        for ((i, p) in pts.withIndex()) { cum += p.reviews; if (cum * 100 >= total) { start = i; break } }
        return pts.drop(start)
    }

    /** The store rows as CSV, newest first, without text or wallet. */
    fun storeCsv(limit: Int): String {
        val cats = categories(); val nm = names()
        val sb = StringBuilder("package,name,category,stars,created,words,from_skr_name,publisher_replied\n")
        for (r in store().sortedByDescending { it.time }.take(limit)) sb.append("${r.pkg},\"${(nm[r.pkg] ?: r.pkg).replace("\"", "\"\"")}\",\"${(cats[r.pkg] ?: "").replace("\"", "\"\"")}\",${r.rating},${java.time.Instant.ofEpochSecond(r.time)},${r.words},${r.skr},${r.replied}\n")
        return sb.toString()
    }

    fun csv(o: JSONObject): String {
        val sb = StringBuilder("section,key,reviews,mean,change_pct,replied_pct,short_pct\n")
        fun row(sec: String, k: String, r: JSONObject) = sb.append("$sec,\"${k.replace("\"", "\"\"")}\",${r.optInt("reviews")},${r.optDouble("mean", 0.0)},${r.opt("change") ?: ""},${r.optDouble("replied_share", 0.0)},${r.optDouble("short_share", 0.0)}\n")
        o.optJSONArray("categories")?.let { a -> for (i in 0 until a.length()) row("category", a.getJSONObject(i).getString("category"), a.getJSONObject(i)) }
        o.optJSONArray("top_apps")?.let { a -> for (i in 0 until a.length()) row("app", a.getJSONObject(i).getString("name"), a.getJSONObject(i)) }
        o.optJSONArray("series")?.let { a -> for (i in 0 until a.length()) { val r = a.getJSONObject(i); sb.append("series,${r.getString("bucket")},${r.getInt("reviews")},${r.getDouble("mean")},,,\n") } }
        return sb.toString()
    }

    // ---- Charts: plain SVG, dark, the palette of the payment charts; no script, so they embed anywhere -------------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private val FONT = "font-family='Manrope, -apple-system, Segoe UI, Roboto, sans-serif'"
    private fun f1(v: Double) = "%.1f".format(java.util.Locale.US, v)
    private fun n(v: Int) = "%,d".format(java.util.Locale.US, v)
    private fun colour(source: String) = if (source == "store") "#FFB547" else "#14F195"
    private fun starColour(mean: Double) = when { mean >= 4.5 -> "#14F195"; mean >= 4.0 -> "#9BE37C"; mean >= 3.5 -> "#FFC46B"; else -> "#FF6B6B" }

    /** Horizontal bars with the count, and the mean stars as a coloured pill after each bar. */
    fun chartBars(title: String, subtitle: String, rows: List<JSONObject>, key: String, source: String, foot: String): String {
        val w = 1200; val rowH = 44; val top = 110; val h = top + rows.size.coerceAtLeast(1) * rowH + 60
        val max = rows.maxOfOrNull { it.getInt("reviews") }?.takeIf { it > 0 } ?: 1
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='$h' viewBox='0 0 $w $h' role='img' aria-label='${esc(title)}'>")
        sb.append("<rect width='$w' height='$h' fill='#0E0B18' rx='18'/>")
        sb.append("<text x='40' y='46' $FONT font-size='26' font-weight='700' fill='#F2EEFF'>${esc(title)}</text>")
        sb.append("<text x='40' y='76' $FONT font-size='15' fill='#B7AED1'>${esc(subtitle)}</text>")
        if (rows.isEmpty()) sb.append("<text x='40' y='${top + 20}' $FONT font-size='16' fill='#6E6588'>Nothing in this window yet.</text>")
        rows.forEachIndexed { i, r -> val y = top + i * rowH; val bw = (520.0 * r.getInt("reviews") / max).coerceAtLeast(2.0); val mean = r.optDouble("mean", 0.0)
            sb.append("<text x='40' y='${y + 27}' $FONT font-size='16' fill='#F2EEFF'>${esc(r.optString(key).take(30))}</text>")
            sb.append("<rect x='330' y='${y + 10}' width='${f1(bw)}' height='22' rx='6' fill='${colour(source)}' opacity='0.9'/>")
            sb.append("<text x='${330 + bw + 12}' y='${y + 27}' $FONT font-size='15' fill='#F2EEFF'>${n(r.getInt("reviews"))}</text>")
            val px = 330 + bw + 12 + 14 * n(r.getInt("reviews")).length + 10
            sb.append("<rect x='${f1(px)}' y='${y + 9}' width='56' height='24' rx='12' fill='${starColour(mean)}' opacity='0.18'/><text x='${f1(px + 28)}' y='${y + 26}' $FONT font-size='14' font-weight='700' fill='${starColour(mean)}' text-anchor='middle'>${f1(mean)}★</text>")
            val ch = r.opt("change"); if (ch is Double) sb.append("<text x='${f1(px + 70)}' y='${y + 26}' $FONT font-size='13' fill='${if (ch >= 0) "#14F195" else "#FF6B6B"}'>${if (ch >= 0) "▲" else "▼"} ${f1(kotlin.math.abs(ch))}%</text>") }
        sb.append("<text x='40' y='${h - 22}' $FONT font-size='12' fill='#6E6588'>${esc(foot)}</text></svg>")
        return sb.toString()
    }

    /** Reviews per bucket as columns, the mean stars as a line on a 1–5 axis at the right. */
    fun chartSeries(title: String, subtitle: String, pts: List<Point>, source: String, foot: String): String {
        val w = 1200; val h = 440; val l = 70; val r = 70; val t = 90; val b = 60
        val nn = pts.size.coerceAtLeast(1); val max = (pts.maxOfOrNull { it.reviews } ?: 0).takeIf { it > 0 } ?: 1
        val slot = (w - l - r).toDouble() / nn; val bw = (slot * 0.68).coerceAtLeast(2.0)
        fun x(i: Int) = l + slot * i + slot / 2
        fun y(v: Double) = t + (h - t - b) * (1 - v / max)
        fun ys(s: Double) = t + (h - t - b) * (1 - (s - 1.0) / 4.0)
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='$h' viewBox='0 0 $w $h' role='img' aria-label='${esc(title)}'>")
        sb.append("<rect width='$w' height='$h' fill='#0E0B18' rx='18'/>")
        sb.append("<text x='40' y='44' $FONT font-size='26' font-weight='700' fill='#F2EEFF'>${esc(title)}</text><text x='40' y='70' $FONT font-size='15' fill='#B7AED1'>${esc(subtitle)}</text>")
        for (k in 0..4) { val v = max.toDouble() * k / 4; val yy = y(v); sb.append("<line x1='$l' x2='${w - r}' y1='${f1(yy)}' y2='${f1(yy)}' stroke='#231c33' stroke-width='1'/><text x='${l - 8}' y='${f1(yy + 4)}' $FONT font-size='12' fill='#6E6588' text-anchor='end'>${n(v.toInt())}</text>")
            val s = 1.0 + k; sb.append("<text x='${w - r + 8}' y='${f1(ys(s) + 4)}' $FONT font-size='12' fill='#B7AED1'>${s.toInt()}★</text>") }
        pts.forEachIndexed { i, p -> if (p.reviews > 0) sb.append("<rect x='${f1(x(i) - bw / 2)}' y='${f1(y(p.reviews.toDouble()))}' width='${f1(bw)}' height='${f1(h - b - y(p.reviews.toDouble()))}' rx='3' fill='${colour(source)}' opacity='0.55'/>") }
        val line = pts.withIndex().filter { it.value.reviews > 0 }.joinToString(" ") { (i, p) -> "${f1(x(i))},${f1(ys(p.avg))}" }
        if (line.isNotEmpty()) sb.append("<polyline points='$line' fill='none' stroke='#F2EEFF' stroke-width='2.5' stroke-linejoin='round'/>")
        pts.withIndex().filter { it.value.reviews > 0 }.forEach { (i, p) -> sb.append("<circle cx='${f1(x(i))}' cy='${f1(ys(p.avg))}' r='3.5' fill='#F2EEFF'/>") }
        val every = (nn / 10).coerceAtLeast(1)
        for (i in pts.indices step every) sb.append("<text x='${f1(x(i))}' y='${h - 28}' $FONT font-size='12' fill='#6E6588' text-anchor='middle'>${esc(pts[i].bucket.let { if (it.length == 7) it else it.substring(5) })}</text>")
        sb.append("<text x='${w - r}' y='70' $FONT font-size='13' fill='#B7AED1' text-anchor='end'><tspan fill='${colour(source)}'>■</tspan> reviews  <tspan fill='#F2EEFF'>—</tspan> mean stars</text>")
        sb.append("<text x='40' y='${h - 8}' $FONT font-size='12' fill='#6E6588'>${esc(foot)}</text></svg>")
        return sb.toString()
    }

    /** The star distribution of one source: five columns with their share. */
    fun chartStars(title: String, subtitle: String, stars: JSONArray, source: String, foot: String): String {
        val w = 1200; val h = 360; val l = 70; val t = 90; val b = 60
        val total = (0 until 5).sumOf { stars.getInt(it) }.coerceAtLeast(1); val max = (0 until 5).maxOf { stars.getInt(it) }.coerceAtLeast(1)
        val sb = StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='$w' height='$h' viewBox='0 0 $w $h' role='img' aria-label='${esc(title)}'>")
        sb.append("<rect width='$w' height='$h' fill='#0E0B18' rx='18'/>")
        sb.append("<text x='40' y='44' $FONT font-size='26' font-weight='700' fill='#F2EEFF'>${esc(title)}</text><text x='40' y='70' $FONT font-size='15' fill='#B7AED1'>${esc(subtitle)}</text>")
        val slot = (w - l - 40) / 5.0; val bw = slot * 0.6
        for (s in 0 until 5) { val c = stars.getInt(s); val bh = (h - t - b) * c.toDouble() / max; val x = l + slot * s + (slot - bw) / 2; val y = h - b - bh
            sb.append("<rect x='${f1(x)}' y='${f1(y)}' width='${f1(bw)}' height='${f1(bh)}' rx='8' fill='${colour(source)}' opacity='${0.35 + 0.13 * s}'/>")
            sb.append("<text x='${f1(x + bw / 2)}' y='${f1(y - 10)}' $FONT font-size='15' font-weight='700' fill='#F2EEFF' text-anchor='middle'>${n(c)} <tspan fill='#B7AED1' font-weight='400'>· ${f1(100.0 * c / total)}%</tspan></text>")
            sb.append("<text x='${f1(x + bw / 2)}' y='${h - 30}' $FONT font-size='14' fill='#B7AED1' text-anchor='middle'>${s + 1}★</text>") }
        sb.append("<text x='40' y='${h - 8}' $FONT font-size='12' fill='#6E6588'>${esc(foot)}</text></svg>")
        return sb.toString()
    }
}
