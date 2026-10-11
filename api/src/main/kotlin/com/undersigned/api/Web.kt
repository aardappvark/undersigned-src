package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Pages anyone can open without a Seeker, and badges publishers can embed. Rendered on the server from the same JSON the
 * API serves, so a page never says anything the API would not. Facts, never verdicts: the vocabulary rules of the app
 * apply (no "safe", "scam" as a verdict, "verified by us", earn/reward words, no emoji).
 */
class Web(private val views: Views, private val indexer: Indexer, private val extra: Map<String, (Map<String, String>) -> Api.Response> = emptyMap()) {

    fun handle(path: String, query: Map<String, String>, lang: String = "en"): Api.Response = try {
        handle0(path, query, lang)
    } catch (e: IllegalArgumentException) { notFound() }   // a bad percent-escape in a path is a 404, not a failure
    catch (e: Exception) { log("web $path: ${e.message?.take(100)}"); Api.Response(500, JSONObject(), raw = "Something went wrong.".toByteArray(), contentType = "text/plain; charset=utf-8") }

    private fun handle0(path: String, query: Map<String, String>, lang: String): Api.Response = run {
        when {
            path == "/" -> page("Undersigned — signed reviews from Seeker owners", home(query["q"]))
            path.startsWith("/v1/badge/") && path.endsWith(".svg") -> badge(dec(path.removePrefix("/v1/badge/").removeSuffix(".svg")))
            path.startsWith("/v1/payments/chart/") && path.endsWith(".svg") -> chart(path.removePrefix("/v1/payments/chart/").removeSuffix(".svg"), query)
            path.startsWith("/app/") && path.endsWith("/feed.xml") -> dec(path.removePrefix("/app/").removeSuffix("/feed.xml")).let { pkg -> views.app(pkg)?.let { feed(pkg, it) } ?: notFound() }
            path.startsWith("/app/") -> dec(path.removePrefix("/app/")).let { pkg -> views.app(pkg)?.let { page("${it.optString("name", pkg)} — Undersigned", app(pkg, it, lang, query["sort"], query["stars"]?.toIntOrNull()?.takeIf { s -> s in 1..5 }), lang = lang,
                description = "Signed reviews of ${it.optString("name", pkg)} from Seeker owners, beside the dApp Store's own rating.", head = "<link rel=alternate type='application/atom+xml' href='/app/${enc(pkg)}/feed.xml'>") } ?: notFound() }
            path.startsWith("/wallet/") -> dec(path.removePrefix("/wallet/")).let { w -> if (runCatching { Base58.decode(w).size }.getOrNull() == 32) page("Wallet ${short(w)} — Undersigned", wallet(w)) else notFound() }
            // The app's own font (OFL, licence beside it) and photographs, served from here: no third-party requests.
            path.startsWith("/assets/") -> asset(path.removePrefix("/assets/")) ?: notFound()
            path == "/icon.svg" -> Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=86400"), ICON.toByteArray(), "image/svg+xml; charset=utf-8")
            // Discovery for agents and directories (9 Oct 2026): a plain-text map of the API and MCP, and an MCP server card at
            // both paths SEP-2127 has used while it settles.
            path == "/changelog" -> page("Changelog — Undersigned", "<h1>Changelog</h1><p class=sub>What changed in the API and MCP. Also as JSON: <a href='/v1/changelog'>/v1/changelog</a>.</p>" +
                Changelog.ENTRIES.joinToString("") { e -> "<div class=card><div class=label>${esc(e.date)}</div><h3>${esc(e.title)}</h3><ul>${e.items.joinToString("") { "<li>${esc(it)}</li>" }}</ul></div>" })
            path == "/status" -> page("Status — Undersigned", status())
            path == "/llms.txt" -> Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=3600"), llmsTxt().toByteArray(), "text/plain; charset=utf-8")
            path == "/.well-known/mcp/server-card.json" || path == "/.well-known/mcp/server-cards.json" -> Api.Response(200, serverCard(), mapOf("Cache-Control" to "public, max-age=3600"))
            path == "/report" -> page("Report content — Undersigned", report())
            path == "/moderation" -> page("Moderation — Undersigned", moderation())
            // This site is the website Undersigned's store listing names: its own publisher proof lives here.
            path == "/.well-known/undersigned.txt" -> Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=3600"),
                raw = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q\nHVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD\ntreasury: DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q\n".toByteArray(), contentType = "text/plain; charset=utf-8")
            path == "/robots.txt" -> Api.Response(200, JSONObject(), raw = "User-agent: *\nAllow: /\nDisallow: /v1/\nSitemap: ${Config.PUBLIC_URL}/sitemap.xml\n".toByteArray(), contentType = "text/plain; charset=utf-8")
            path == "/sitemap.xml" -> Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=3600"), sitemap().toByteArray(), "application/xml; charset=utf-8")
            path == "/watch" -> page("Store watch — Undersigned", watch())
            path == "/reviews" -> page("Review analytics — Undersigned", reviewsPage(query["source"]?.takeIf { it in ReviewAnalytics.SOURCES } ?: "store", query["window"]?.takeIf { it in Analytics.WINDOWS } ?: "all", query["category"]?.takeIf { it.isNotBlank() }), description = "What the reviews say: the dApp Store's own reviews and the signed reviews on Undersigned, by category, by dApp and over time, with every source told apart.")
            path == "/payments" -> page("Payments — Undersigned", paymentsPage(query["window"]?.takeIf { it in Analytics.WINDOWS } ?: "30d"), description = "What Solana dApp Store apps are paid on-chain: leaderboards, categories, trends and charts from confirmed treasuries and wallets found in app code.")
            path == "/review" -> page("Write a signed review — Undersigned", review(query["package"], lang), lang = lang)
            path == "/seed" -> page("Seed the fleet — Undersigned", seed())
            path == "/act" -> act(query["kind"].orEmpty(), query["target"].orEmpty(), lang)
            path == "/api" -> page("API — Undersigned", apiPage())
            path == "/plans" -> page("Publisher and data plans — Undersigned", plansPage())
            path == "/publishers" -> page("For publishers — Undersigned", publishers(query["package"]?.trim()))
            extra.containsKey(path) -> extra.getValue(path)(query)
            else -> notFound()
        }
    }

    // ---- Badge ------------------------------------------------------------------------------------------------------

    /** A shields-style SVG: "Undersigned | 4.6 · 12 signed". Cached an hour. Unknown or unreviewed apps say so plainly. */
    /** The analytics charts (Analytics.kt), served like badges: cached five minutes, embeddable, no script. */
    fun chart(which: String, query: Map<String, String>): Api.Response {
        val an = views.analytics
        val window = query["window"]?.takeIf { it in Analytics.WINDOWS } ?: "30d"
        val by = query["by"]?.takeIf { it in listOf("day", "week", "month") } ?: (if (window == "7d") "day" else if (window == "all") "month" else "week")
        val svg = views.memo5("chart:$which:$window:$by:${query["n"]}:${query["include"]}") { when {
            which == "top" -> an.chartTop(window, query["n"]?.toIntOrNull()?.coerceIn(1, 25) ?: 10, query["include"]?.takeIf { it in listOf("confirmed", "found", "all") } ?: "all")
            which == "store" -> an.storeSeries(window, by).let { s -> an.chartSeries("Payments into dApp wallets · " + an.label(window), "USD on the day paid, per $by · gross receipts, from the Solana ledger", an.pts(s.getJSONArray("confirmed")), an.pts(s.getJSONArray("found"))) }
            which.startsWith("app/") -> an.appSeries(dec(which.removePrefix("app/")), by)?.let { s -> an.chartSeries(s.getString("name") + " · payments received", "USD on the day paid, per $by · ${s.getInt("payments")} payments from ${s.getInt("payers")} payers", an.pts(s.getJSONArray("series"))) } ?: ""
            else -> ""
        } }
        if (svg.isEmpty()) return Api.Response(404, JSONObject().put("error", "not_found").put("message", if (which.startsWith("app/")) "no payments seen for that package" else "no such chart"))
        return Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=300"), svg.toByteArray(), "image/svg+xml; charset=utf-8")
    }

    fun badge(pkg: String): Api.Response {
        val app = views.app(pkg)
        val signed = app?.optJSONObject("signed")
        val n = signed?.optInt("counted_reviews") ?: 0
        val right = when {
            app == null -> "unknown app"
            n == 0 -> "no signed reviews yet"
            else -> "${"%.1f".format(java.util.Locale.US, signed!!.optDouble("mean"))} · $n signed"
        }
        val left = "Undersigned"
        fun w(s: String) = (s.length * 6.6 + 12).toInt()
        val lw = w(left); val rw = w(right); val total = lw + rw
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="$total" height="20" role="img" aria-label="${esc("$left: $right")}"><title>${esc("$left: $right")}</title>""" +
            """<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#fff" stop-opacity=".08"/><stop offset="1" stop-opacity=".1"/></linearGradient>""" +
            """<clipPath id="r"><rect width="$total" height="20" rx="4" fill="#fff"/></clipPath><g clip-path="url(#r)"><rect width="$lw" height="20" fill="#9945ff"/><rect x="$lw" width="$rw" height="20" fill="#1c1630"/><rect width="$total" height="20" fill="url(#s)"/></g>""" +
            """<g fill="#fff" text-anchor="middle" font-family="Verdana,DejaVu Sans,sans-serif" font-size="11"><text x="${lw / 2}" y="14">${esc(left)}</text><text x="${lw + rw / 2}" y="14" fill="#14f195">${esc(right)}</text></g></svg>"""
        return Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=3600"), svg.toByteArray(), "image/svg+xml; charset=utf-8")
    }

    // ---- Pages ------------------------------------------------------------------------------------------------------

    private fun home(q: String?): String {
        val s = views.stats()
        val sb = StringBuilder()
        if (q.isNullOrBlank()) {
            sb.append("<section class=hero style=\"background-image:url('/assets/photo-fingertip.jpg')\"><div class=in><div class=label style='color:#d9d2ee'>For the Solana dApp Store</div>" +
                "<h1>Signed reviews from Seeker owners</h1><p class=sub>Every review here is signed on-chain by its author's own wallet. Only wallets holding a Seeker Genesis Token are counted: one phone, one voice. No payment to Undersigned changes a score.</p>" +
                "<div class=ctas><a class=btn href='/review'>Write a signed review</a><a class='btn sec' href='#apps'>Browse apps</a></div></div></section>")
            sb.append("<div class=card><div class=stats>")
            listOf("reviews_counted" to "counted reviews", "attested_signers" to "independent Seeker owners reviewing", "apps_reviewed" to "apps reviewed", "publisher_replies" to "publisher replies", "catalogue_apps" to "apps in the store")
                .forEach { (k, l) -> sb.append("<div class=stat><b>${"%,d".format(java.util.Locale.US, s.optLong(k))}</b><span>$l</span></div>") }
            sb.append("</div></div>")
            // Early days, said plainly (judge review, 10 Oct 2026): an empty score is the rules working, not the site failing.
            if (s.optLong("reviews_counted") == 0L) sb.append("<div class='card how'><div class=label>Early days</div><p class=meta>Undersigned launched in September 2026. " +
                "Reviews signed by Undersigned's own wallets are shown, labelled, and never counted, so the signed scores fill only as Seeker owners sign. " +
                "Every record so far is on-chain and readable here and in the <a href='https://aardappvark.github.io/undersigned-viewer/'>browser viewer</a>. " +
                "<a href='/review'>Be one of the first.</a> <a href='https://aardappvark.github.io/undersigned-legal/integrity.html'>How counting works</a>.</p></div>")
            sb.append("<h2>How it works</h2><div class=steps3>" +
                "<div class=card><b>1</b><strong>Rate an app</strong><p class=meta>Stars, one to three tags, a short note if you like. From this site or the Undersigned app.</p></div>" +
                "<div class=card><b>2</b><strong>Sign it with your wallet</strong><p class=meta>Your wallet signs the record. You pay only the Solana network fee; Undersigned takes nothing.</p></div>" +
                "<div class=card><b>3</b><strong>It counts beside the store's rating</strong><p class=meta>Reviews from Seeker owners are counted. Publishers can answer, and anyone can check every record.</p></div></div>")
        }
        sb.append("<h2 id=apps>${if (q.isNullOrBlank()) "Apps with signed reviews" else "Results for “${esc(q)}”"}</h2>")
        sb.append("<form method=get action='/' role=search><input name=q value='${esc(q ?: "")}' placeholder='Search ${"%,d".format(java.util.Locale.US, s.optLong("catalogue_apps"))} dApp Store apps by name or package' aria-label='Search apps'></form>")
        val apps = views.apps(q, null, if (q.isNullOrBlank()) "signed" else "name", 30).getJSONArray("apps")
        var shown = 0
        for (i in 0 until apps.length()) {
            val a = apps.getJSONObject(i); val sg = a.optJSONObject("signed")
            if (q.isNullOrBlank() && sg == null) continue
            shown++
            val name = a.getString("name")
            sb.append("<a class='card row link' href='/app/${enc(a.getString("package"))}'><span class=badge aria-hidden=true>${esc(name.trim().take(1).uppercase())}</span><span class=grow><span class=name>${esc(name)}</span><br><span class=meta>${esc(a.optString("category", ""))} · ${esc(a.getString("package"))}</span></span>")
            sb.append(when {
                sg != null && sg.optInt("counted_reviews") > 0 -> "<span class=score>${"%.1f".format(java.util.Locale.US, sg.optDouble("mean"))}<small>${sg.optInt("counted_reviews")} signed</small></span>"
                sg != null -> "<span class=meta>signed, not counted</span>"
                else -> "<span class=meta>no signed reviews yet</span>" }).append("</a>")
        }
        // The stats above count reviews from our own wallets under "apps reviewed"; this list shows counted scores only. Say both (judge, 11 Oct 2026).
        if (shown == 0) sb.append("<p class=meta>${if (!q.isNullOrBlank()) "No app matches. Try part of its name or its package." else if (s.optLong("apps_reviewed") > 0) "No counted reviews yet · ${s.optLong("apps_reviewed")} ${if (s.optLong("apps_reviewed") == 1L) "app" else "apps"} reviewed from Undersigned's own wallets, shown on each app's page and never counted. <a href='/v1/records?type=rev'>Those records</a> · <a href='/review'>be the first Seeker owner to sign</a>." else "No signed reviews yet."}</p>")
        sb.append("<div class='card how'><div class=label>More</div><p class=meta><a href='/watch'>Store watch</a>: a weekly report of unusual rating bursts. <a href='/publishers'>For publishers</a>: prove an app is yours and answer reviews. <a href='/v1/dataset.json'>Open dataset</a> and <a href='/api'>API</a>: every record, free to read within limits.</p></div>")
        return sb.toString()
    }

    private fun app(pkg: String, a: JSONObject, lang: String, sort: String? = null, stars: Int? = null): String {
        val sb = StringBuilder()
        val sg = a.optJSONObject("signed"); val store = a.optJSONObject("store"); val sr = a.optJSONObject("store_rating")
        sb.append("<h1>${esc(a.optString("name", pkg))}</h1><p class=meta>${esc(a.optString("category", ""))} · ${esc(pkg)}</p>")
        if (a.optJSONObject("signed")?.optBoolean("operator_publishes") == true) sb.append("<div class='card warn'><div class=meta>Undersigned's operator publishes this app. Reviews from its own wallets are shown, labelled, and never counted.</div></div>")
        sb.append("<div class=ctas><a class=btn href='/review?package=${enc(pkg)}'>Write a signed review</a><a class='btn sec' href='/app/${enc(pkg)}/feed.xml' title='New signed reviews of this app, in any feed reader'>Follow new reviews (RSS)</a></div>")
        // The two scores side by side, each labelled with where it comes from.
        sb.append("<div class='card grid2'><div><div class=label>Signed by Seeker owners</div>")
        if (sg != null && sg.optInt("counted_reviews") > 0) sb.append("<div class=big>${"%.1f".format(java.util.Locale.US, sg.optDouble("mean"))}</div><div class=meta>${plural(sg.optInt("counted_reviews"), "counted review", "counted reviews")} · ranked as ${sg.opt("rank_score")} until more Seekers sign</div>${bars(sg.optJSONArray("stars"), pkg)}")
        else sb.append("<div class=big>–</div><div class=meta>No counted signed reviews yet. Reviews from Undersigned's own wallets, and from wallets without a Seeker Genesis Token, are shown below but not counted.</div>")
        sb.append("</div><div><div class=label>The dApp Store's own rating</div>")
        if (sr != null && sr.optInt("count") > 0) sb.append("<div class=big>${sr.opt("average")}</div><div class=meta>${sr.optInt("count")} ratings, read ${esc(sr.optString("as_of"))}</div>${bars(sr.optJSONArray("stars"))}")
        else sb.append("<div class=big>–</div><div class=meta>Not read yet.</div>")
        sb.append("</div></div>")
        // Pro members' reviews as their own labelled score, never blended into the signed one (compliance review, 9 Oct 2026).
        a.optJSONObject("signed")?.optJSONObject("pro_members")?.takeIf { it.optInt("counted_reviews") > 0 }?.let { m ->
            sb.append("<div class=card><div class=label>Pro members</div><div><b>${"%.1f".format(java.util.Locale.US, m.optDouble("mean"))}</b> <span class=meta>from ${plural(m.optInt("counted_reviews"), "review", "reviews")} by reviewers who pay Undersigned for Pro, at the same weight as everyone. Shown apart; the signed score never includes payment to Undersigned.</span></div></div>")
        }
        // A suggested summary of the counted reviews (Ai.kt), labelled, with each point's count; only when there are enough.
        views.summary(pkg)?.let { sm -> val pts = sm.optJSONArray("points") ?: JSONArray()
            if (pts.length() > 0) {
                sb.append("<div class=card><div class=label>Suggested summary</div><ul>")
                for (i in 0 until pts.length()) { val pt = pts.getJSONObject(i); sb.append("<li>${esc(pt.optString("text"))} <span class=meta>(${plural(pt.optInt("count"), "review", "reviews")})</span></li>") }
                sb.append("</ul><p class=small>${esc(sm.optString("label"))}</p></div>")
            } }
        sg?.optJSONObject("tags")?.takeIf { it.length() > 0 }?.let { t -> sb.append("<div class=row>"); t.keys().asSequence().sortedByDescending { t.optInt(it) }.forEach { sb.append("<span class=tag>${esc(I18n.tag(lang, it))} · ${t.optInt(it)}</span>") }; sb.append("</div>") }
        // Pro reviewers' own tags on this app, most used first (owner, 11 Oct 2026).
        sg?.optJSONObject("custom_tags")?.takeIf { it.length() > 0 }?.let { t -> sb.append("<div class=row>"); t.keys().asSequence().sortedByDescending { t.optInt(it) }.take(12).forEach { sb.append("<span class='tag own' title='reviewers&#39; own tags (Pro)'>${esc(it)} · ${t.optInt(it)}</span>") }; sb.append("</div>") }

        // Bursts: timing facts with a daily sparkline, the burst window shaded AND labelled (never colour alone).
        val b = views.bursts(pkg)
        val sbur = b.getJSONObject("signed_reviews").getJSONArray("bursts"); val rbur = b.getJSONObject("store_ratings").getJSONArray("bursts")
        // Bursts after the signed reviews, as in the app: the reviews are what the page is for.
        val bursts = StringBuilder()
        bursts.append("<h2>Bursts</h2>")
        if (sbur.length() == 0 && rbur.length() == 0) bursts.append("<p class=meta>No bursts found. Signed reviews checked: ${b.getJSONObject("signed_reviews").optInt("checked")}. Days of store snapshots: ${b.getJSONObject("store_ratings").optInt("snapshot_days")} (8 needed).</p>")
        for ((label, arr) in listOf("Signed reviews" to sbur, "Store star ratings" to rbur)) for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            bursts.append("<div class='card warn'><div class=label>$label · ${if (x.optString("kind") == "high") "5★" else "1★"}</div><div><b>${x.optInt("count")}</b> against about ${x.opt("expected")} usual · ×${x.opt("multiple")}</div>")
            bursts.append("<div class=meta>${day(x.optLong("start"))} – ${day(x.optLong("end"))}${if (x.has("attested")) " · ${x.optInt("attested")} of ${x.optInt("of")} with an attested device" else ""}${if (!x.isNull("season")) " · during ${esc(x.optString("season"))}" else ""}</div>")
            x.optJSONObject("spark")?.let { bursts.append(spark(it)) }
            bursts.append("</div>")
        }
        bursts.append("<p class=small>A burst is a fact about timing, not a verdict about the reviews.</p>")

        // Reviews
        // The reader's own choice of order, within each section; stake is as read when each review appeared (never a score).
        val order = sort?.takeIf { it in setOf("helpful", "skr", "sol") } ?: "newest"
        val rv = views.reviews(pkg, 50, 0, order)
        sb.append("<h2 id=reviews>Signed reviews</h2>")
        // A star row on the histogram links here with ?stars=N: how many gave N stars, then straight to why (owner, 11 Oct 2026).
        if (stars != null) sb.append("<p class=small><b>Showing $stars-star reviews only</b> · <a href='/app/${enc(pkg)}#reviews'>all stars</a></p>")
        sb.append("<p class=small>Sort: " + listOf("newest" to "Newest", "helpful" to "Most helpful", "skr" to "Most SKR staked", "sol" to "Most SOL staked").joinToString(" · ") { (k, label) ->
            if (k == order) "<b>$label</b>" else "<a href='/app/${enc(pkg)}?sort=$k#reviews'>$label</a>" } + ". Sorts within each section. Stake is as read when the review first appeared, shown for transparency, and never changes a score.</p>")
        val list = rv.getJSONArray("reviews")
        if (list.length() == 0) sb.append("<p class=meta>None yet. <a href='/review?package=${enc(pkg)}'>Write one</a> from a Seeker.</p>")
        // Under headings, as in the app (owner, 9 Oct 2026): organic, Pro members, related to Undersigned or the publisher (shown,
        // labelled, counted nowhere), and not counted. The last is folded away: anyone can sign text for one network fee.
        val groups = List(5) { StringBuilder() }; val counts = IntArray(5)
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i); val who = r.getJSONObject("reviewer")
            if (stars != null && r.optInt("rating") != stars) continue
            val related = if (r.isNull("related")) null else r.optString("related")
            // Promoted reviews get their own section after organic and Pro members, never first (protocol Weighting).
            val parts = who.optJSONObject("weight_parts")
            val g = when { related != null -> 3; !r.optBoolean("counted") -> 4; parts?.optBoolean("promoted") == true -> 2; parts?.optBoolean("pro_member") == true -> 1; else -> 0 }
            counts[g]++
            @Suppress("NAME_SHADOWING") val sb = groups[g]
            sb.append("<div class=rev><div class=row><span class=stars aria-label='${r.optInt("rating")} of 5'>${"★".repeat(r.optInt("rating"))}${"☆".repeat(5 - r.optInt("rating"))}</span>")
            when {
                related == "operator" -> sb.append("<span class=pill>Undersigned operator · not counted</span>")
                related == "pro_from_publisher" -> sb.append("<span class=pill>Pro from this app's publisher · not counted</span>")
                who.optBoolean("attested_device") -> sb.append("<span class='pill g'>Attested device · weight ${who.opt("weight")}</span>")
                else -> sb.append("<span class=pill>Not counted: no attested device</span>")
            }
            if (parts?.optBoolean("pro_member") == true && g <= 2) sb.append("<span class='pill g'>Pro member</span>")
            if (g == 2) sb.append("<span class=pill>Promoted · paid</span>")
            // Every review shows its wallet's stake as read at the time, whatever its heading: transparency, never weight.
            who.optJSONObject("stake_at_review")?.let { k -> sb.append("<span class=pill>Staked ${"%,.0f".format(java.util.Locale.US, k.optDouble("skr"))} SKR${if (k.isNull("sol")) "" else " · " + "%,.2f".format(java.util.Locale.US, k.optDouble("sol")) + " SOL"} when the review was read, ${day(k.optLong("read_at"))} (shown, never weighed)</span>") }
            if (r.optBoolean("paid_for_app")) sb.append("<span class='pill g'>Paid for this app</span>")
            if (r.optBoolean("self_published")) sb.append("<span class=pill>The publisher's own review, not counted</span>")
            sb.append("</div>")
            val tags = r.optJSONArray("tags"); val own = r.optJSONArray("custom_tags")
            if ((tags != null && tags.length() > 0) || (own != null && own.length() > 0)) sb.append("<div class=row>${(0 until (tags?.length() ?: 0)).joinToString("") { "<span class=tag>${esc(I18n.tag(lang, tags!!.getString(it)))}</span>" }}" +
                // The reviewer's own words (Pro), marked apart from the fixed vocabulary.
                (0 until (own?.length() ?: 0)).joinToString("") { "<span class='tag own' title='the reviewer&#39;s own tag (Pro)'>${esc(own!!.getString(it))}</span>" } + "</div>")
            if (!r.isNull("text")) sb.append("<p class=txt>${esc(r.optString("text"))}</p>")
            else if (!r.isNull("hidden_by_moderator") && r.has("hidden_by_moderator")) sb.append("<p class=meta><i>Text hidden by a moderator: ${esc(modReason(r.optString("hidden_by_moderator")))}</i> (<a href='/moderation'>policy</a>). Rating and tags still count.</p>")
            else if (r.optBoolean("hidden_by_operator")) sb.append("<p class=meta>Text not shown on this site (<a href='/report'>why</a>). The on-chain record is unchanged.</p>")
            val facts = ArrayList<String>()
            // The reviewer's record, counts only (owner, 11 Oct 2026): what this wallet has signed, so a reader can weigh the voice.
            who.optJSONObject("standing")?.takeIf { it.optInt("counted_reviews") > 0 && !com.undersigned.protocol.Operator.isOperator(r.getString("signer")) }?.let { sd ->
                facts += "Reviewer: ${sd.optInt("counted_reviews")} signed · ${sd.optInt("apps_reviewed")} apps" + (if (sd.optInt("follow_ups") > 0) " · ${sd.optInt("follow_ups")} follow-ups" else "") +
                    (if (sd.optInt("fixes_confirmed") > 0) " · ${sd.optInt("fixes_confirmed")} fixes confirmed" else "") + (if (sd.optLong("first_seen") > 0) " · since ${day(sd.optLong("first_seen")).take(7)}" else "") }
            if (!r.isNull("install_age")) facts += "On their phone: ${I18n.use(lang, r.optString("install_age"))}"
            if (!r.isNull("usage_time")) facts += "Used: ${I18n.time(lang, r.optString("usage_time"))}"
            r.optJSONObject("season")?.let { facts += "During ${it.optString("label")}" }
            r.optJSONObject("similar")?.let { sm -> val ms = sm.optJSONArray("matches"); val m = ms?.optJSONObject(0)
                facts += if (m == null) "Text matches other reviews" else "Similar text to a review of ${esc(m.optString("name").takeIf { !m.isNull("name") && it.isNotEmpty() } ?: m.optString("package"))} signed ${day(m.optLong("time"))}" +
                    (if (ms.length() > 1) " and ${ms.length() - 1} more" else "") + (if (sm.optBoolean("recent")) ", within 3 days" else "") }
            if (r.optInt("flaggers") > 0) facts += "Flagged by ${r.optInt("flaggers")}"
            if (r.optBoolean("hidden_by_consensus")) facts += "Hidden by community consensus"
            sb.append("<div class=meta>${day(r.optLong("time"))} · v${r.opt("version")} · <a href='/wallet/${r.getString("signer")}'>${esc(who.optString("skr_name").takeIf { !who.isNull("skr_name") } ?: short(r.getString("signer")))}</a>${if (facts.isNotEmpty()) " · " + facts.joinToString(" · ") { esc(it) } else ""}</div>")
            val reps = r.optJSONArray("replies") ?: JSONArray()
            for (k in 0 until reps.length()) { val p = reps.getJSONObject(k); sb.append("<div class=reply><span class=meta>${if (p.optBoolean("publisher")) "<b class=pub>Publisher</b> · " else ""}${short(p.getString("wallet"))} · ${day(p.optLong("time"))}</span><br>${if (!p.isNull("hidden_by_moderator") && p.has("hidden_by_moderator")) "<span class=meta><i>Text hidden by a moderator: ${esc(modReason(p.optString("hidden_by_moderator")))}</i></span>" else if (p.optBoolean("hidden_by_operator")) "<span class=meta>Text not shown on this site.</span>" else esc(p.optString("text"))}</div>") }
            sb.append("<div class=small><a href='/act?kind=up&amp;target=${r.getString("id")}'>Helpful</a> · <a href='/act?kind=reply&amp;target=${r.getString("id")}'>Reply</a> · <a href='/act?kind=flag&amp;target=${r.getString("id")}'>Flag</a></div>")
            sb.append("</div>")
        }
        val heads = listOf(
            "Organic reviews" to "Counted Seeker owners with no tie to Undersigned or to this app's publisher.",
            "Pro members" to "Reviewers who pay Undersigned for Pro: counted at the same weight as everyone, and also shown as their own score.",
            "Promoted reviews · paid" to "Reviews their own authors paid Undersigned to promote. Counted once at the same weight as everyone, and kept apart from organic reviews.",
            "Related to Undersigned or the publisher" to "Undersigned's own wallets, or a reviewer holding Pro gifted by this app's publisher. Shown, labelled, counted in no score.",
        )
        // The Promoted score sits in its own shaded section after organic reviews, never in the headline card (legal review, 9 Oct 2026).
        val promotedLine = sg?.optJSONObject("promoted")?.takeIf { it.optInt("counted_reviews") > 0 }?.let { m -> "<p class=meta><b>Promoted score ${"%.1f".format(java.util.Locale.US, m.optDouble("mean"))}</b> from ${plural(m.optInt("counted_reviews"), "review", "reviews")}, at their ordinary weight.</p>" } ?: ""
        for (g in 0..3) if (counts[g] > 0) sb.append(if (g == 2) "<div class='card warn'>" else "").append("<h3>${heads[g].first} · ${counts[g]}</h3><p class=small>${heads[g].second}</p>").append(if (g == 2) promotedLine else "").append(groups[g]).append(if (g == 2) "</div>" else "")
        if (counts[4] > 0) sb.append("<details class=card><summary>Not counted · ${plural(counts[4], "review", "reviews")}: no attested device, or the publisher's own</summary>${groups[4]}</details>")
        sb.append("<p class=small><a href='https://aardappvark.github.io/undersigned-legal/integrity.html'>How reviews are collected, checked and counted</a></p>")
        if (rv.optInt("total") > list.length()) sb.append("<p class=meta>Showing ${list.length()} of ${rv.optInt("total")}.</p>")

        // Publisher
        val pub = a.optJSONObject("publisher")
        sb.append(bursts)
        sb.append("<h2>Publisher</h2><div class=card>")
        store?.let { sb.append("<div class=meta>The store lists: ${esc(it.optString("publisher", "–"))}${if (!it.isNull("website")) " · " + link(it.optString("website")) else ""}</div>") }
        val claims = pub?.optJSONArray("claims") ?: JSONArray()
        if (claims.length() == 0) sb.append("<div class=meta>No wallet has claimed to publish this app.</div>")
        sb.append("<div class=small><a href='/publishers'>How a publisher is proven</a></div>")
        for (k in 0 until claims.length()) { val c = claims.getJSONObject(k)
            sb.append("<div>${short(c.getString("wallet"))}: ${if (c.optBoolean("verified")) "<span class='pill g'>${if (c.optString("proof") == "store_build") "Publisher, proven by the build the dApp Store serves" else "Publisher, proven by the website the store lists" + (if (!c.isNull("proof_first_seen")) ", seen since ${day(c.optLong("proof_first_seen"))}" else "")}</span>" else "<span class=pill>Claimed, not proven yet</span>"}</div>") }
        pub?.optJSONObject("responsiveness")?.let { if (it.optInt("total") > 0) sb.append("<div class=meta>Answered ${it.optInt("answered")} of ${it.optInt("total")} signed reviews.</div>") }
        pub?.optJSONObject("feature_requests")?.let { if (it.optInt("total") > 0) sb.append("<div class=meta>Feature requests: ${it.optInt("answered")} of ${it.optInt("total")} answered" + (it.optDouble("median_decision_hours").takeIf { d -> !d.isNaN() }?.let { d -> " · median ${hoursText(d)} to decide" } ?: "") + " · ${it.optInt("delivered")} delivered" + (it.optDouble("median_deliver_hours").takeIf { d -> !d.isNaN() }?.let { d -> ", median ${hoursText(d)}" } ?: "") + "." + (it.optJSONObject("by_category")?.takeIf { b -> b.length() > 0 }?.let { b -> " By category: " + b.keys().asSequence().map { k -> "$k ${b.optInt(k)}" }.joinToString(" · ") + "." } ?: "") + "</div>") }
        pub?.optJSONObject("crashes")?.let { if (it.optInt("reported") > 0) sb.append("<div class=meta>Crashes reported: ${it.optInt("reported")} · ${it.optInt("fixed")} fixed" + (it.optDouble("median_fix_hours").takeIf { d -> !d.isNaN() }?.let { d -> " · median ${hoursText(d)} to fix" } ?: "") + ".</div>") }
        sb.append("</div>")
        if (views.analytics.appSeries(pkg, "week") != null) sb.append("<h2>Payments over time</h2><div class=card style='padding:10px'><img alt='Payments received over time' style='width:100%;height:auto;border-radius:14px' src='/v1/payments/chart/app/${enc(pkg)}.svg?by=week'><p class=small>USD on the day paid, across the app's confirmed treasury and any wallet found in its code (labelled). Gross receipts, not revenue.</p></div>")
        a.optJSONObject("payments")?.let { p -> sb.append("<h2>Payments received on-chain</h2><div class=card><div class=stats><div class=stat><b>${if (p.isNull("usd_when_paid")) "–" else "$" + p.opt("usd_when_paid")}</b><span>USD on the day of each payment</span></div><div class=stat><b>${p.optInt("payments")}</b><span>payments</span></div><div class=stat><b>${p.optInt("payers")}</b><span>payers</span></div><div class=stat><b>${p.optInt("attested_payers")}</b><span>payers with an attested device</span></div></div><p class=small>Publisher-confirmed treasury only. Off-chain income is invisible to this; nothing here is a statement about profit.</p></div>") }
        store?.let { sb.append("<h2>From the store</h2><div class=card><div class=meta>Version ${it.opt("version")} · signing certificate SHA-256 <code>${esc(it.optString("certificate_sha256", "–").take(16))}…</code></div>${if (!it.isNull("whats_new")) "<p class=txt>${esc(clip(it.optString("whats_new"), 400))}</p>" else ""}</div>") }

        // Embed
        val url = "${Config.PUBLIC_URL}/app/${enc(pkg)}"; val img = "${Config.PUBLIC_URL}/v1/badge/${enc(pkg)}.svg"
        sb.append("<h2>Badge</h2><p><img src='/v1/badge/${enc(pkg)}.svg' alt='Undersigned badge'></p><pre class=code>${esc("<a href=\"$url\"><img src=\"$img\" alt=\"Undersigned signed reviews\"></a>")}</pre>")
        sb.append("<p class=small><a href='/review?package=${enc(pkg)}'>Write a signed review</a> · <a href='/v1/apps/${enc(pkg)}'>This page as JSON</a></p>")
        return sb.toString()
    }

    private fun watch(): String {
        val r = views.watchReport(7)
        val sb = StringBuilder("<h1>Store watch</h1><p class=sub>Every dApp Store app whose star ratings jumped far above its usual daily pace in the last 7 days, from one reading of the store's public star counts each day. Strongest first.</p>")
        if (!r.optBoolean("ready")) {
            val have = r.optInt("snapshot_days"); val need = r.optInt("needed")
            sb.append("<div class=card><div class=label>Collecting</div><div class=big>Day $have of $need</div><progress max='$need' value='$have' style='width:100%'></progress><div class=meta>${r.optInt("apps_read")} apps read each day${if (!r.isNull("first_day")) ", since ${esc(r.optString("first_day"))}" else ""}. A burst can only be told from an app's usual pace once there are $need days to compare.</div></div>")
        } else {
            val hits = r.getJSONArray("hits")
            sb.append("<p class=meta>${r.optInt("apps_read")} apps · snapshots ${esc(r.optString("first_day"))} to ${esc(r.optString("last_day"))} · ${hits.length()} with a burst this week</p>")
            if (hits.length() == 0) sb.append("<div class=card>No rating bursts in the last 7 days.</div>")
            for (i in 0 until hits.length()) { val h = hits.getJSONObject(i)
                sb.append("<a class='card warn link' href='/app/${enc(h.getString("package"))}'><div class=name>${esc(h.getString("name"))}</div><div><b>${h.optInt("count")}</b> ${if (h.optString("kind") == "high") "5★" else "1★"} ratings against about ${h.opt("expected")} usual · ×${h.opt("multiple")}</div><div class=meta>${day(h.optLong("start"))} – ${day(h.optLong("end"))}${if (!h.isNull("category")) " · " + esc(h.optString("category")) else ""}</div>${spark(h.getJSONObject("spark"))}</a>") }
        }
        sb.append("<p class=small>A burst is a fact about timing, not a verdict about the app or its reviewers. Ratings that arrive between two daily readings are spread evenly across the gap.</p>")
        return sb.toString()
    }

    /**
     * Review from a browser: the server builds the transaction (POST /v1/tx/review), the reader's own wallet signs and
     * sends it through the Wallet Standard (browser extensions, in-wallet browsers, and the Seeker's wallet via Mobile
     * Wallet Adapter in Chrome). The server never holds a key. Consent mirrors the app: the terms, and a second,
     * explicit consent before any text goes on-chain for good.
     */
    /** Local only: the treasury wallet signs the fleet's claims and listings from this Mac's browser (docs/FLEET_SEEDING.md). */
    private fun seed(): String = "<h1>Seed the fleet</h1>" +
        "<p>Connect the treasury wallet <code>DD4a…Wq2Q</code> (Phantom in this browser). This builds a publisher claim and a free treasury listing for each of the fleet's 28 apps, skipping any this wallet already signed, in as few transactions as fit, and asks the wallet to sign each one. Network fees only.</p>" +
        "<div class=card><div class=row><button id=connect>Connect wallet</button><button id=sign disabled>Build and sign</button></div><p id=status class=meta></p><pre id=plan class=code hidden></pre></div>" +
        "<script type=module>\nimport { getWallets } from 'https://cdn.jsdelivr.net/npm/@wallet-standard/app@1.1.0/+esm';\n$JS_HELPERS\n" + """
let wallet = null, account = null;
$('connect').onclick = async () => {
  const ws = getWallets().get().filter(w => w.chains.some(c => c.startsWith('solana:')) && w.features['standard:connect'] && w.features['solana:signAndSendTransaction']);
  if (!ws.length) { st('No Solana wallet found in this browser.'); return; }
  wallet = ws.find(w => /phantom/i.test(w.name)) || ws[0];
  try { const r = await wallet.features['standard:connect'].connect(); account = r.accounts && r.accounts[0]; }
  catch (e) { st('Not connected: ' + explain(e)); return; }
  if (!account) { st('The wallet returned no account.'); return; }
  st('Connected: ' + account.address.slice(0, 4) + '…' + account.address.slice(-4) + (account.address.startsWith('DD4a') ? '' : ' (not the treasury wallet: these records would be its own claims and listings)'));
  $('sign').disabled = false;
};
$('sign').onclick = async () => {
  $('sign').disabled = true; st('Building…');
  try {
    const r = await fetch('/v1/tx/seed', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ account: account.address }) });
    const j = await r.json(); if (!r.ok) { st(j.message || j.error); $('sign').disabled = false; return; }
    if (!j.transactions.length) { st(j.message); return; }
    $('plan').hidden = false; $('plan').textContent = j.plan.join('\n');
    const sigs = [];
    for (let i = 0; i < j.transactions.length; i++) {
      st('Sign transaction ' + (i + 1) + ' of ' + j.transactions.length + ' in your wallet (' + j.records + ' records in all)…');
      const [res] = await wallet.features['solana:signAndSendTransaction'].signAndSendTransaction({ account, transaction: unb64(j.transactions[i]), chain: 'solana:mainnet' });
      sigs.push(bs58(res.signature));
    }
    $('status').innerHTML = 'Done: ' + sigs.map((s, i) => '<a target=_blank href="https://solscan.io/tx/' + s + '">transaction ' + (i + 1) + '</a>').join(' · ') + '. The index picks them up within a few minutes.';
  } catch (e) { st('Stopped: ' + explain(e) + '. Press the button again: records already signed are skipped.'); $('sign').disabled = false; }
};
</script>"""

    private fun review(pkg: String?, lang: String): String {
        val app = pkg?.let { indexer.state.byPackage[it] }
        // The same groups and opposite pairs as the app's composer (protocol TagGroups).
        val tagChips = com.undersigned.protocol.TagGroups.groups.joinToString("") { (g, tags) -> "<div class=label style='margin-top:10px'>${esc(I18n.str(lang, "tag_group_" + g.name.lowercase()) ?: g.name)}</div><div class=row>" + tags.joinToString("") { t -> "<button type=button class=chip data-tag='${esc(t.wire)}' data-opp='${esc(com.undersigned.protocol.TagGroups.opposites[t]?.wire ?: "")}' aria-pressed=false>${esc(I18n.tag(lang, t.wire))}</button>" } + "</div>" }
        return """<h1>Write a signed review</h1>
<p class=sub>Rate a dApp Store app with your own wallet. The review is recorded on Solana, signed by you, and counted beside the store's own rating.</p>
<ol class=stepper aria-label=Progress><li class=on>1 · Write</li><li>2 · Connect</li><li>3 · Sign</li><li>4 · Recorded</li></ol>
<div class=grid2>
<div class=card>
<label class=label for=pkg>App (package name)</label><input id=pkg value='${esc(pkg ?: "")}' placeholder='e.g. com.solflare.mobile' autocomplete=off spellcheck=false autocapitalize=off>
<div class=meta id=appname>${esc(app?.name ?: "")}</div>
<div class=label style='margin-top:14px'>Rating</div><div class=row id=rating role=radiogroup aria-label=Rating>${(1..5).joinToString("") { "<button type=button class=chip data-r=$it aria-pressed=false aria-label='$it of 5'>$it ★</button>" }}</div>
<div class=label style='margin-top:14px'>Tags (one to three)</div><div id=tags>$tagChips</div>
<label class=label for=text style='display:block;margin-top:14px'>Note (optional, 140 characters)</label><textarea id=text maxlength=140 rows=3 placeholder='Short, factual, in your own words.'></textarea>
<label class=check><input type=checkbox id=permanent><span class=meta>I understand my note stays on-chain for good. Withdrawing the review later takes it out of the scores; the record stays.</span></label>
<label class=check><input type=checkbox id=terms><span class=meta>I accept the <a href='https://aardappvark.github.io/undersigned-legal/terms.html' target=_blank>Terms</a>. The version recorded is the store's current version of the app.</span></label>
<div class=ctas><button id=connect type=button class=sec>Connect wallet</button><button id=sign type=button disabled>Sign review</button><button id=qrbtn type=button hidden class=sec>Sign with a phone (QR)</button></div>
<div id=qr></div>
<p class=meta id=status aria-live=polite></p>
</div>
<div class='card how'><div class=label>How it works</div><ol>
<li><b>Pick the app and rate it:</b> stars, one to three tags, a short note if you like.</li>
<li><b>Connect a wallet.</b> On a Seeker, open this page in Chrome and use the Seeker Wallet. On a computer, use Phantom, Solflare or Backpack in this browser, or <b>Sign with a phone</b> to scan a QR code with your phone's wallet.</li>
<li><b>Approve in your wallet.</b> It shows the exact record before you sign. You pay only the Solana network fee your wallet sets (about 0.00005 SOL). Undersigned takes nothing.</li>
<li><b>Recorded.</b> This page tells you when your review is live, and can send a browser notification.</li></ol>
<p class=small>Counted in scores only when the signing wallet holds a Seeker Genesis Token; other reviews are recorded but not counted. Everything you sign is public and permanent.</p></div>
</div>
<script type=module>
$JS_HELPERS
let rating = 0, tags = [];
const PKG = () => $('pkg').value.trim();
function ready() { const text = $('text').value.trim(); const ok = rating && tags.length && $('terms').checked && (!text || $('permanent').checked) && PKG();
  if (!$('sign').querySelector('.spin')) $('sign').disabled = !(W.address && ok); step(!ok ? 1 : (!W.address ? 2 : 3)); }
document.querySelectorAll('#rating .chip').forEach(b => b.onclick = () => { rating = +b.dataset.r; document.querySelectorAll('#rating .chip').forEach(x => x.setAttribute('aria-pressed', x === b)); ready(); });
document.querySelectorAll('#tags .chip').forEach(b => b.onclick = () => { const t = b.dataset.tag, o = b.dataset.opp;
  if (tags.includes(t)) tags = tags.filter(x => x !== t); else { tags = tags.filter(x => x !== o); if (tags.length < 3) tags.push(t); else toast('Three tags at most: remove one first.', 'warn', 4000); }
  document.querySelectorAll('#tags .chip').forEach(x => x.setAttribute('aria-pressed', tags.includes(x.dataset.tag))); ready(); });
['text', 'permanent', 'terms'].forEach(id => $(id).addEventListener('input', ready));
let lookup; $('pkg').addEventListener('input', () => { clearTimeout(lookup); ready(); lookup = setTimeout(async () => { const p = PKG(); if (!p) { $('appname').textContent = ''; return; }
  try { const r = await fetch('/v1/apps/' + encodeURIComponent(p)); if (r.ok) { const j = await r.json(); $('appname').textContent = j.name || p; } else $('appname').textContent = 'Not in the dApp Store list. Check the package name.'; } catch (e) {} }, 450); });
$('connect').onclick = async () => {
  busy($('connect'), true, ANDROID ? 'Opening your wallet…' : 'Choose a wallet…');
  try { await connectWallet(); busy($('connect'), false); $('connect').innerHTML = 'Connected · ' + esc(W.address.slice(0, 4) + '…' + W.address.slice(-4));
    toast('Connected to ' + esc(W.label || 'your wallet') + '.', 'ok', 4000); }
  catch (e) { busy($('connect'), false); toast('Not connected: ' + esc(explain(e)), 'err'); }
  ready(); };
$('sign').onclick = async () => {
  busy($('sign'), true, 'Building…'); step(3);
  try {
    const r = await fetch('/v1/tx/review', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ account: W.address, package: PKG(), rating, tags, text: $('text').value.trim() }) });
    const j = await r.json(); if (!r.ok) { busy($('sign'), false); toast(esc(j.message || j.error), 'err'); ready(); return; }
    if (j.counted_now === false) toast('This site has read no Seeker Genesis Token on this wallet, so the review will be recorded but not counted in scores.', 'warn', 12000);
    busy($('sign'), false); busy($('sign'), true, ANDROID ? 'Approve in your wallet (double-tap)…' : 'Approve in your wallet…');
    const sig = await sendTx(j.transaction);
    busy($('sign'), false); $('sign').disabled = true; $('sign').innerHTML = 'Signed';
    follow(sig, '/app/' + encodeURIComponent(PKG()), 'Your review');
  } catch (e) { busy($('sign'), false); toast('Not signed: ' + esc(explain(e)), 'err'); ready(); }
};
if (!ANDROID && location.protocol === 'https:') { $('qrbtn').hidden = false; $('qrbtn').onclick = async () => {
  const text = $('text').value.trim();
  if (!(rating && tags.length && $('terms').checked && (!text || $('permanent').checked) && PKG())) { toast('Choose a rating and tags and tick the boxes first.', 'warn'); return; }
  const u = location.origin + '/v1/pay/review?package=' + encodeURIComponent(PKG()) + '&rating=' + rating + '&tags=' + encodeURIComponent(tags.join(',')) + (text ? '&text=' + encodeURIComponent(text) : '');
  const QR = (await import('https://cdn.jsdelivr.net/npm/qrcode@1.5.4/+esm')).default;
  const img = await QR.toDataURL('solana:' + encodeURIComponent(u), { margin: 1, width: 280 });
  $('qr').innerHTML = '<img alt="QR code for a phone wallet" width=280 height=280 style="border-radius:14px;margin-top:10px" src="' + img + '"><div class=meta>Scan with the wallet on your Seeker. It shows the review and asks you to sign. Your review appears here within a few minutes.</div>';
}; }
ready();
</script>"""
    }

    /** Set by the server: the pass price and pay links, and the endpoint list (OpenAPI with x-tier). */
    var apiInfo: (() -> JSONObject)? = null
    var apiDocs: (() -> JSONObject)? = null

    /** The API for people (the nav's "API" used to open raw JSON, which read as an error, owner 7 Oct 2026). */
    private fun apiPage(): String {
        val p = apiInfo?.invoke() ?: JSONObject(); val pass = p.optJSONObject("pass") ?: JSONObject(); val price = pass.optJSONObject("price") ?: JSONObject()
        val free = p.optJSONObject("free") ?: JSONObject(); val pay = p.optJSONObject("solana_pay") ?: JSONObject()
        val n = { v: Long -> "%,d".format(java.util.Locale.US, v) }
        val sb = StringBuilder("<h1>The Undersigned API</h1><p class=sub>Every signed review, publisher proof, store rating, rating burst and payment board, as JSON. Read it free within limits; a pass lifts them. The ledger itself is on Solana, so anyone can also read it directly.</p>")
        sb.append("<div class=grid2><div class=card><div class=label>Free, no key</div><div class=big>${n(free.optLong("per_day"))}</div><p class=meta>requests a day per address (${free.optInt("per_minute")} a minute). Lists return the top ${free.optInt("rows")} rows.</p><p class=meta><a href='/v1/dataset.json'>Open dataset</a>: every record and score, rebuilt every few minutes.</p></div>")
        sb.append("<div class='card how'><div class=label>Pass</div><div class=big>${n(pass.optLong("requests"))}</div><p class=meta>requests over ${pass.optLong("days")} days, ${pass.optInt("per_minute")} a minute, every endpoint. ")
        sb.append("${esc(price.opt("sol").toString())} SOL or ${n(price.optDouble("skr").toLong())} SKR (${n(price.optDouble("skr_with_seeker_genesis_token").toLong())} SKR with a Seeker Genesis Token). Paying again adds another pass.</p>")
        p.optJSONObject("pay_per_call")?.let { c -> val pc = c.optJSONObject("per_call") ?: JSONObject(); val mn = c.optJSONObject("minimum") ?: JSONObject()
            sb.append("<p class=meta><b>Or pay per call</b> (for agents): ${esc(pc.opt("sol").toString())} SOL or ${esc(pc.opt("skr").toString())} SKR a call, from ${esc(mn.opt("sol").toString())} SOL or ${esc(mn.opt("skr").toString())} SKR, valid a year. <code>POST /v1/checkout</code> with <code>{\"calls\": N}</code>, pay, redeem.</p>") }
        sb.append("<div class=ctas><a class=btn href='${esc(pay.optString("sol"))}'>Pay in SOL</a><a class='btn sec' href='${esc(pay.optString("skr"))}'>Pay in SKR</a></div><div id=qr></div></div></div>")
        p.optJSONObject("publisher_plan")?.let { pp -> sb.append("<div class=grid2><div class=card><div class=label>Publisher plan</div><div class=big>${esc(pp.opt("sol").toString())} SOL</div><p class=meta>a month for one app (${n(pp.optDouble("skr").toLong())} SKR; ${n(pp.optDouble("skr_with_seeker_genesis_token").toLong())} with a Genesis Token), up to ${pp.optInt("max_months_per_payment")} months a payment. For the app's proven publisher: its report (reviews waiting for an answer, the store rating's trend, bursts, certificate and permission changes, scores by version, its category's benchmark), webhooks and the API. <a href='/plans'>Get it</a></p></div>") }
        p.optJSONObject("data_plan")?.let { dp -> val pr = dp.optJSONObject("price") ?: JSONObject(); sb.append("<div class=card><div class=label>Data plan</div><div class=big>${n(dp.optLong("requests"))}</div><p class=meta>requests over ${dp.optLong("days")} days, ${n(dp.optInt("per_minute").toLong())} a minute, and CSV exports of the store's history (${esc((dp.optJSONObject("exports") ?: JSONObject()).keys().asSequence().joinToString(", "))}). ${esc(pr.opt("sol").toString())} SOL or ${n(pr.optDouble("skr").toLong())} SKR. <a href='/plans'>Get it</a></p></div></div>") }
        sb.append("<div class='card how'><div class=label>Getting a key</div><ol><li><b>Pay</b> with the button above from a phone wallet, or scan the QR code on a computer. The payment carries the pass reference, so it is found on-chain.</li>" +
            "<li><b>Redeem</b>: <code>POST /v1/keys</code> with the payment's signature and the paying wallet's signature of the message <code>Undersigned API key for &lt;payment signature&gt;</code>. Only the wallet that paid can redeem it. Or start with <code>POST /v1/checkout</code> for a one-time secret instead of a message signature.</li>" +
            "<li><b>Call</b> with <code>Authorization: Bearer &lt;key&gt;</code>. <code>GET /v1/keys/me</code> shows what is left.</li></ol></div>")
        // Quickstart and agents (9 Oct 2026): copy-paste calls, and the MCP endpoint served by this API (Mcp.kt).
        val base = Config.PUBLIC_URL
        sb.append("<h2>Quickstart</h2><div class=card><p class=meta>No key, no sign-up. Every answer is JSON; add <code>?pretty=1</code> to read it.</p>")
        sb.append("<pre class=code>${esc("""curl "$base/v1/apps?q=wallet&limit=5"
curl "$base/v1/apps/com.solflare.mobile"
curl "$base/v1/apps/com.solflare.mobile/reviews?sort=newest"
curl -H "Authorization: Bearer YOUR_KEY" "$base/v1/watch?days=7"""")}</pre>")
        sb.append("<p class=meta>JavaScript</p><pre class=code>${esc("""const r = await fetch("$base/v1/apps/com.solflare.mobile");
const app = await r.json();
console.log(app.signed.mean, app.signed.counted_reviews, app.store_rating);""")}</pre>")
        sb.append("<p class=meta>Python</p><pre class=code>${esc("""import requests
app = requests.get("$base/v1/apps/com.solflare.mobile").json()
print(app["signed"]["mean"], app["signed"]["counted_reviews"])""")}</pre>")
        sb.append("<p class=small>Answers: 200 data · 402 needs a pass (the body carries the price and Solana Pay links) · 429 over the limit (Retry-After) · 404 unknown. Send a User-Agent header from scripts.</p></div>")
        sb.append("<h2>Ask the docs</h2><div class=card><p class=meta>Ask how to do something with this API or MCP. Answered by AI from the published docs only; check the docs it points to.</p>" +
            "<form id=ask><input id=q maxlength=500 placeholder='How do I get the reviews of one app sorted by newest?' aria-label='Your question' style='width:100%'><button class=btn style='margin-top:8px'>Ask</button></form><p id=ans class=meta></p></div>" +
            "<script>document.getElementById('ask').onsubmit=async e=>{e.preventDefault();const a=document.getElementById('ans');a.textContent='…';" +
            "try{const r=await fetch('/v1/ask',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question:document.getElementById('q').value})});" +
            "const j=await r.json();a.textContent=j.answer?(j.answer+'\\n\\n'+j.label):(j.message||'No answer right now.')}catch(x){a.textContent='No answer right now.'}};</script>")
        sb.append("<h2>Use it from an AI agent (MCP)</h2><div class=card><p>This API is also a Model Context Protocol server at <code>$base/mcp</code>. Add the URL to your agent; nothing to install. Its tools only read: search apps, get an app's scores, read its reviews, bursts and wallets. No tool writes, signs or submits a review.</p>")
        sb.append("<p class=meta>Claude Code</p><pre class=code>${esc("""claude mcp add --transport http undersigned $base/mcp""")}</pre>")
        sb.append("<p class=meta>Cursor and other clients (mcp.json)</p><pre class=code>${esc("""{
  "mcpServers": {
    "undersigned": {
      "url": "$base/mcp",
      "headers": { "Authorization": "Bearer YOUR_KEY" }
    }
  }
}""")}</pre>")
        sb.append("<p class=meta>Claude (web or desktop): Settings, Connectors, add a custom connector with the URL above. The header line is only needed for the tools marked as needing a pass.</p></div>")
        val docs = apiDocs?.invoke()?.optJSONObject("paths") ?: JSONObject()
        val rows = docs.keys().asSequence().flatMap { path -> docs.getJSONObject(path).let { m -> m.keys().asSequence().map { verb -> Triple(path, verb.uppercase(), m.getJSONObject(verb)) } } }.toList()
        for ((tier, title) in listOf("free" to "Free", "limited" to "Free within the limits above", "paid" to "With a pass")) {
            val t = rows.filter { it.third.optString("x-tier") == tier }.sortedBy { it.first }
            if (t.isEmpty()) continue
            sb.append("<h2>$title</h2><div class=card>")
            t.forEach { (path, verb, op) -> sb.append("<div class=rev style='padding:10px 0'><code style='color:var(--t)'>$verb ${esc(path)}</code><div class=meta>${esc(op.optString("summary"))}</div></div>") }
            sb.append("</div>")
        }
        sb.append("<p class=small>Full description: <a href='/v1/openapi.json'>OpenAPI</a> · Raw price and pay links: <a href='/v1/pricing'>/v1/pricing</a> · Store data shown here belongs to the Solana dApp Store.</p>")
        sb.append("<script type=module>\n${'$'}JS_HELPERS_PLACEHOLDER\nif (!ANDROID) { const QR = (await import('https://cdn.jsdelivr.net/npm/qrcode@1.5.4/+esm')).default; const u = document.querySelector('.ctas a.btn').href;\n  document.getElementById('qr').innerHTML = '<img alt=\"QR code to pay for a pass\" width=200 height=200 style=\"border-radius:12px;margin-top:12px\" src=\"' + await QR.toDataURL(u, { margin: 1, width: 200 }) + '\"><div class=meta>Scan with your phone wallet to pay in SOL.</div>'; }\n</script>")
        return sb.toString().replace("${'$'}JS_HELPERS_PLACEHOLDER", JS_HELPERS)
    }

    /**
     * Buying the publisher plan or the data plan from a browser (owner, 10 Oct 2026): a checkout secret and Solana Pay
     * links (a QR code on a computer), then the redeem, then the publisher's report. The key stays in this browser's
     * storage and in request headers, never in a URL.
     */
    private fun plansPage(): String = """<h1>Publisher and data plans</h1>
<p class=sub>Paid tools around the reviews, never inside them: no plan changes a score, a weight or an order. Every payment is public on Solana.</p>
<div class=grid2><div class=card><div class=label>Publisher plan</div><p class=meta>For an app's proven publisher: the app's report (reviews waiting for an answer, the store rating's trend, bursts, certificate and permission changes, scores by version, the category's benchmark), webhooks for the app, and the API. Paid by the month, per app; pay from the wallet that proved the app.</p>
<form id=pf><input id=pkg placeholder='Package, e.g. com.example.app' aria-label='Package' required style='width:100%'><label class=meta>Months <input id=months type=number min=1 max=12 value=1 style='width:70px'></label> <button class=btn>Get payment links</button></form></div>
<div class=card><div class=label>Data plan</div><p class=meta>Two million requests over 30 days and CSV exports of the store's history: daily star ratings per app, releases and certificate changes, every dApp with its scores, wallets found in app code.</p><button id=df class=btn>Get payment links</button></div></div>
<div class='card how' id=pay style='display:none'><div class=label>Pay</div><p class=meta id=paywhat></p><div class=ctas><a class=btn id=psol>Pay in SOL</a><a class='btn sec' id=pskr>Pay in SKR</a><a class='btn sec' id=psgt>SKR with a Genesis Token</a></div><div id=qr></div>
<p class=meta>Keep this page open. The secret below redeems the payment; anyone holding it can, so do not share it.</p><pre class=code id=secret></pre>
<form id=rf><input id=txsig placeholder='Payment transaction signature' aria-label='Payment transaction signature' required style='width:100%'><button class=btn style='margin-top:8px'>Redeem</button></form><p id=rout class=meta></p></div>
<div class=card><div class=label>Your publisher report</div><p class=meta>With a publisher key (kept in this browser only).</p><form id=kf><input id=rpkg placeholder='Package' aria-label='Package' style='width:100%'><input id=key placeholder='Key (us_…)' aria-label='Key' style='width:100%;margin-top:8px' autocomplete=off><button class=btn style='margin-top:8px'>Open report</button></form><div id=rep></div></div>
<script type=module>
const ${'$'} = (id) => document.getElementById(id); let job = null;
// The checkout secret survives the hand-off to a wallet app (the tab often reloads): kept here until redeemed.
try { ${'$'}('key').value = localStorage.getItem('us_key') || ''; job = JSON.parse(localStorage.getItem('us_checkout') || 'null'); } catch (e) {}
if (job && job.secret) { ${'$'}('pay').style.display = ''; ${'$'}('paywhat').textContent = job.what || ''; ${'$'}('secret').textContent = job.secret; ${'$'}('psol').href = job.sol; ${'$'}('pskr').href = job.skr; ${'$'}('psgt').href = job.sgt; }
async function checkout(body, what) {
  if (body.publisher) {   // pay only from a wallet that can redeem: a proven publisher of the app
    const ar = await fetch('/v1/apps/' + encodeURIComponent(body.publisher));
    if (ar.status === 404) { alert('Unknown package: ' + body.publisher); return; }
    if (ar.status === 429) { alert('Too many requests just now; try again in a minute.'); return; }
    const a = await ar.json().catch(() => null);
    const proven = ((a || {}).publisher || {}).verified_wallets || [];
    if (!a) { alert('Could not read that app right now; try again.'); return; }
    if (!proven.length) { alert('No wallet has proven it publishes ' + body.publisher + ' yet. Prove it first (a signed claim from the app), then come back.'); return; }
    what += ' Proven publisher wallet' + (proven.length > 1 ? 's' : '') + ': ' + proven.join(', ') + '.';
  }
  const r = await fetch('/v1/checkout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); const j = await r.json();
  job = { secret: j.secret, pkg: body.publisher || null, what, sol: j.solana_pay.sol, skr: j.solana_pay.skr, sgt: j.solana_pay.skr_with_seeker_genesis_token };
  try { localStorage.setItem('us_checkout', JSON.stringify(job)); } catch (e) {}
  ${'$'}('pay').style.display = ''; ${'$'}('paywhat').textContent = what; ${'$'}('secret').textContent = j.secret;
  ${'$'}('psol').href = j.solana_pay.sol; ${'$'}('pskr').href = j.solana_pay.skr; ${'$'}('psgt').href = j.solana_pay.skr_with_seeker_genesis_token;
  try { const QR = (await import('https://cdn.jsdelivr.net/npm/qrcode@1.5.4/+esm')).default; ${'$'}('qr').innerHTML = '<img alt="QR code to pay in SOL" width=220 height=220 style="border-radius:14px;margin-top:10px" src="' + await QR.toDataURL(j.solana_pay.sol, { width: 440, margin: 1 }) + '">'; } catch (e) {}
  ${'$'}('pay').scrollIntoView({ behavior: 'smooth' });
}
${'$'}('pf').onsubmit = (e) => { e.preventDefault(); const pkg = ${'$'}('pkg').value.trim(), m = +${'$'}('months').value || 1; checkout({ publisher: pkg, months: m }, 'Publisher plan for ' + pkg + ', ' + m + (m === 1 ? ' month' : ' months') + '. Pay from a wallet that is a proven publisher of the app.'); };
${'$'}('df').onclick = () => checkout({ plan: 'data' }, 'Data plan, 30 days.');
${'$'}('rf').onsubmit = async (e) => { e.preventDefault(); const sig = ${'$'}('txsig').value.trim();
  if (!job || !job.secret) { ${'$'}('rout').textContent = 'Start a checkout above first; the secret from it redeems the payment.'; return; }
  const post = (path, body) => fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  let r = job.pkg ? await post('/v1/publisher/plan', { signature: sig, package: job.pkg, secret: job.secret }) : await post('/v1/keys', { signature: sig, secret: job.secret });
  let j = await r.json();
  if (r.status === 403 && job.pkg && /not a proven publisher/.test(j.message || '') && confirm((j.message || '') + '\n\nRedeem this payment as an API pass or credits instead?')) {
    r = await post('/v1/keys', { signature: sig, secret: job.secret }); j = await r.json(); }
  if (!r.ok) { ${'$'}('rout').textContent = j.message || 'Not redeemed yet; try again in a minute.'; return; }
  try { localStorage.setItem('us_key', j.key); localStorage.removeItem('us_checkout'); } catch (e) {}
  ${'$'}('key').value = j.key; if (job.pkg) ${'$'}('rpkg').value = job.pkg;
  ${'$'}('rout').textContent = 'Done. Your key is saved in this browser and shown in the report form below; copy it somewhere safe.' + (j.plan_ends_at ? ' Plan ends ' + new Date(j.plan_ends_at * 1000).toISOString().slice(0, 10) + '.' : '');
};
${'$'}('kf').onsubmit = async (e) => { e.preventDefault(); const k = ${'$'}('key').value.trim(), pkg = ${'$'}('rpkg').value.trim(); try { localStorage.setItem('us_key', k); } catch (x) {}
  const r = await fetch('/v1/publisher/' + encodeURIComponent(pkg) + '/report', { headers: { Authorization: 'Bearer ' + k } }); const j = await r.json(); const out = ${'$'}('rep');
  if (!r.ok) { out.textContent = j.message || 'No report.'; return; }
  const t = j.store_rating_trend || {}, b = j.benchmark || {}, w = j.waiting_for_an_answer || {}, s = (j.app || {}).signed || {}, f = ((j.app || {}).publisher || {}).fixes || {};
  const row = (k, v) => '<div class=rev style="padding:8px 0"><b>' + k + '</b><div class=meta>' + v + '</div></div>';
  out.innerHTML = row('Signed score', (s.mean ?? '–') + ' from ' + (s.counted_reviews || 0) + ' counted reviews')
    + row('Waiting for your answer', (w.count || 0) + ' signed reviews')
    + row('Store rating', (t.average_then ?? '–') + ' → ' + (t.average_now ?? '–') + ', ' + (t.ratings_added ?? 0) + ' new ratings since ' + (t.from || '–') + ' (' + (t.ratings_per_day ?? 0) + ' a day)')
    + row('Category', (b.category || '–') + ': median rating ' + (b.median_store_rating ?? '–') + '; you are above ' + (b.store_rating_percentile ?? '–') + '% of ' + (b.apps_rated || 0) + ' rated apps')
    + row('Fixes', (f.announced || 0) + ' announced, ' + (f.follow_ups_after_fix || 0) + ' follow-ups, mean change ' + (f.mean_star_change_after_fix ?? '–') + '★')
    + row('Changes seen', (j.changes || []).length + ' (version, certificate, permissions)')
    + (j.payments_over_time ? '<img alt="Payments over time" style="width:100%;height:auto;border-radius:14px;margin-top:10px" src="' + j.payments_over_time.chart_svg.replace(/^https?:\/\/[^/]+/, '') + '">' : '')
    + '<p class=small>' + j.label + ' Full JSON: GET /v1/publisher/' + pkg + '/report with your key.</p>';
};
</script>"""

    /**
     * The payments analytics page (owner, 10 Oct 2026): what the store's apps are paid, from the Solana ledger. Every chart is
     * a server-made SVG, so the page needs no script and the images embed anywhere. Found wallets are always labelled.
     */
    private fun reviewsPage(source: String, window: String, category: String?): String {
        val ra = views.reviewAnalytics; val o = ra.summary(source, window, category, null, when (window) { "7d", "30d" -> "day"; "90d" -> "week"; else -> "month" }, 12)
        val q = { s: String, w: String -> "/reviews?source=$s&window=$w" + (category?.let { "&category=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "") }
        val qs = "source=$source&window=$window" + (category?.let { "&category=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
        val sb = StringBuilder("<h1>What the reviews say</h1><p class=sub>Every source, told apart and never mixed: the dApp Store's own reviews (read by a paced crawl of its public lists; only counts, means and shares are published here) and the signed reviews on Undersigned by class: organic Seeker wallets, Pro members, promoted, quick reviews without a wallet, wallets without a Genesis Token, and our own. By category, by dApp and over time. Nothing on this page weighs a review.</p>")
        sb.append("<div class=chips>" + ReviewAnalytics.SOURCES.joinToString("") { s -> "<a class='chip${if (s == source) " on" else ""}' href='${q(s, window)}'>${esc(ReviewAnalytics.label(s))}</a>" } + "</div>")
        sb.append("<div class=chips>" + Analytics.WINDOWS.joinToString("") { w -> "<a class='chip${if (w == window) " on" else ""}' href='${q(source, w)}'>${esc(views.analytics.label(w))}</a>" } + (category?.let { "<a class='chip on' href='/reviews?source=$source&window=$window'>✕ ${esc(it)}</a>" } ?: "") + "</div>")
        val stars = o.getJSONArray("stars"); val tot = (0 until 5).sumOf { stars.getInt(it) }.coerceAtLeast(1)
        sb.append("<div class=card><div class=stats><div class=stat><b>${"%,d".format(java.util.Locale.US, o.getInt("reviews"))}</b><span>reviews, ${esc(views.analytics.label(window))}</span></div><div class=stat><b>${o.getInt("apps")}</b><span>dApps</span></div><div class=stat><b>${o.getDouble("mean")}★</b><span>mean</span></div>" +
            "<div class=stat><b>${"%.0f".format(java.util.Locale.US, 100.0 * stars.getInt(4) / tot)}%</b><span>five stars</span></div><div class=stat><b>${o.optDouble("short_share", 0.0)}%</b><span>three words or fewer</span></div><div class=stat><b>${o.optDouble("replied_share", 0.0)}%</b><span>answered by the publisher</span></div>" +
            (if (source == "store") "<div class=stat><b>${o.optDouble("skr_share", 0.0)}%</b><span>from a .skr name</span></div>" else "") +
            (o.opt("change").let { if (it is Double) "<div class=stat><b>${if (it >= 0) "+" else ""}$it%</b><span>last 30 days against the 30 before</span></div>" else "" }) + "</div></div>")
        if (o.getInt("reviews") == 0) sb.append("<div class=card><p class=small>Nothing in this source and window yet. Counted signed reviews begin when Seeker owners outside Undersigned sign; our own are labelled and never counted.</p></div>")
        if (category == null) sb.append("<div class=card style='padding:10px'><img alt='Reviews by category' style='width:100%;height:auto;border-radius:14px' src='/v1/reviews/chart/categories.svg?$qs'></div>")
        sb.append("<div class=card style='padding:10px'><img alt='Most-reviewed dApps' style='width:100%;height:auto;border-radius:14px' src='/v1/reviews/chart/apps.svg?$qs&n=12'></div>")
        sb.append("<div class=card style='padding:10px'><img alt='Reviews over time' style='width:100%;height:auto;border-radius:14px' src='/v1/reviews/chart/series.svg?$qs'></div>")
        sb.append("<div class=card style='padding:10px'><img alt='Star distribution' style='width:100%;height:auto;border-radius:14px' src='/v1/reviews/chart/stars.svg?$qs'></div>")
        o.optJSONArray("categories")?.takeIf { it.length() > 0 }?.let { a ->
            sb.append("<h2>Categories</h2><div class=card>")
            for (i in 0 until a.length()) { val c = a.getJSONObject(i); val ch = c.opt("change")
                sb.append("<div class=row><a class=grow href='${q(source, window)}&category=${java.net.URLEncoder.encode(c.getString("category"), "UTF-8")}'><b>${esc(c.getString("category"))}</b></a><span class=meta>${"%,d".format(java.util.Locale.US, c.getInt("reviews"))} reviews · ${c.getInt("apps")} dApps · ${c.getDouble("mean")}★ · ${c.optDouble("replied_share", 0.0)}% answered" + (if (ch is Double) " · <span style='color:${if (ch >= 0) "var(--g)" else "var(--n)"}'>${if (ch >= 0) "▲" else "▼"} ${kotlin.math.abs(ch)}%</span>" else "") + "</span></div>") }
            sb.append("</div>") }
        o.optJSONArray("top_apps")?.takeIf { it.length() > 0 }?.let { a ->
            sb.append("<h2>Most reviewed</h2><div class=card>")
            for (i in 0 until a.length()) { val c = a.getJSONObject(i); val ch = c.opt("change")
                sb.append("<div class=row><span class=meta style='width:28px'>${c.getInt("rank")}</span><a class=grow href='/app/${esc(c.getString("package"))}'><b>${esc(c.getString("name"))}</b> <span class=small>${esc(c.optString("category"))}</span></a><span class=meta>${"%,d".format(java.util.Locale.US, c.getInt("reviews"))} · ${c.getDouble("mean")}★ · ${c.optDouble("replied_share", 0.0)}% answered" + (if (ch is Double) " · <span style='color:${if (ch >= 0) "var(--g)" else "var(--n)"}'>${if (ch >= 0) "▲" else "▼"} ${kotlin.math.abs(ch)}%</span>" else "") + "</span></div>") }
            sb.append("</div>") }
        sb.append("<p class=small>Data: <a href='/v1/reviews/analytics?$qs'>/v1/reviews/analytics</a> (JSON) · <a href='/v1/reviews/analytics.csv?$qs'>CSV</a> · charts: <code>/v1/reviews/chart/{categories,apps,series,stars}.svg?$qs</code> · the MCP tool <code>review_analytics</code> answers the same. Store reviews are read from the dApp Store's public lists (${"%,d".format(java.util.Locale.US, ra.store().size)} so far, the crawl continues); their text stays the store's and is not republished. Signed reviews are read from the Solana ledger. Disclosure: Undersigned is run by AardAppvark, publisher of 28 dApps on this store; reviews from its own wallets are the \"ours\" source and never count.</p>")
        return sb.toString()
    }

    private fun paymentsPage(window: String): String {
        val an = views.analytics
        val sum = an.summary(window)
        val lb = sum.getJSONObject("leaderboard"); val cats = sum.getJSONObject("categories").getJSONArray("categories"); val tr = sum.getJSONObject("trending"); val pt = sum.getJSONObject("patterns"); val ts = sum.getJSONObject("treasuries").getJSONArray("rows")
        val n = { v: Double -> "$" + "%,.0f".format(java.util.Locale.US, v) }
        val sb = StringBuilder("<h1>What dApps are paid</h1><p class=sub>Payments into dApp Store apps' wallets, read from the Solana ledger: confirmed treasuries (listed and proven by their publishers) and wallets found in app code (paid by 5+ wallets, confirmed by no one). USD on the day paid. Gross receipts, not revenue: a game can take deposits it pays back out, and trading apps make their money through swap fees that reach no app wallet.</p>")
        sb.append("<div class=chips>" + Analytics.WINDOWS.joinToString("") { w -> "<a class='chip${if (w == window) " on" else ""}' href='/payments?window=$w'>${esc(an.label(w))}</a>" } + "</div>")
        sb.append("<div class=card><div class=stats><div class=stat><b>${n(lb.getDouble("total_usd"))}</b><span>received, ${esc(an.label(window))}</span></div><div class=stat><b>${lb.getInt("payments")}</b><span>payments</span></div><div class=stat><b>${lb.getInt("payers")}</b><span>paying wallets</span></div><div class=stat><b>${lb.getInt("apps")}</b><span>apps paid</span></div>" +
            "<div class=stat><b>${pt.getJSONObject("payers").getDouble("repeat_share")}%</b><span>of payers had paid that app before</span></div><div class=stat><b>${pt.getJSONObject("concentration").getDouble("top_10_apps_share")}%</b><span>of it went to the top 10 apps</span></div></div></div>")
        sb.append("<div class=card style='padding:10px'><img alt='Top dApps by payments received' style='width:100%;height:auto;border-radius:14px' src='/v1/payments/chart/top.svg?window=$window&n=12'></div>")
        sb.append("<div class=card style='padding:10px'><img alt='Payments over time' style='width:100%;height:auto;border-radius:14px' src='/v1/payments/chart/store.svg?window=$window&by=${if (window == "7d") "day" else if (window == "all") "month" else "week"}'></div>")
        sb.append("<h2>Leaderboard</h2><div class=card>")
        val rows = lb.getJSONArray("rows")
        for (i in 0 until rows.length()) { val r = rows.getJSONObject(i); val conf = r.getString("source") == "confirmed"
            sb.append("<div class=rev style='padding:10px 0;display:flex;gap:12px;align-items:baseline'><b style='width:28px;color:var(--t3)'>${r.getInt("rank")}</b><div style='flex:1'><a href='/app/${enc(r.getString("package"))}'>${esc(r.getString("name"))}</a> <span class='pill ${if (conf) "g" else "w"}'>${if (conf) "confirmed treasury" else "found in app code"}</span>" +
                "<div class=meta>${r.getInt("payments")} payments · ${r.getInt("payers")} payers (${r.getInt("new_payers")} new) · ${r.getDouble("sol")} SOL · ${"%,.0f".format(java.util.Locale.US, r.getDouble("skr"))} SKR · ${r.getDouble("usdc")} USDC</div></div><b>${n(r.getDouble("usd"))}</b><span class=meta>${r.getDouble("share")}%</span></div>") }
        if (rows.length() == 0) sb.append("<p class=meta>No payments in this window yet.</p>")
        sb.append("</div>")
        sb.append("<div class=grid2><div class=card><div class=label>Categories</div>")
        for (i in 0 until minOf(8, cats.length())) { val c = cats.getJSONObject(i); val ch = c.opt("change_pct")
            sb.append("<div class=rev style='padding:8px 0'><b>${esc(c.getString("category"))}</b> <span class=meta>${n(c.getDouble("usd"))} · ${c.getInt("apps_paid")} apps · ${when (ch) { is Double -> (if (ch >= 0) "+" else "") + ch + "% vs the window before"; "new" -> "new this window"; else -> "" }}</span>" +
                "<div class=meta>${(0 until c.getJSONArray("leaders").length()).joinToString(" · ") { j -> val l = c.getJSONArray("leaders").getJSONObject(j); "<a href='/app/${enc(l.getString("package"))}'>${esc(l.optString("name"))}</a> ${n(l.getDouble("usd"))}" }}</div></div>") }
        sb.append("</div><div class=card><div class=label>Rising · ${esc(tr.getString("compared_with"))}</div>")
        fun trRows(k: String, empty: String) { val a = tr.getJSONArray(k); if (a.length() == 0) sb.append("<p class=meta>$empty</p>")
            for (i in 0 until a.length()) { val r = a.getJSONObject(i); val ch = r.opt("change_pct"); sb.append("<div class=rev style='padding:8px 0'><a href='/app/${enc(r.getString("package"))}'>${esc(r.getString("name"))}</a> <span class='pill ${if (r.getString("source") == "confirmed") "g" else "w"}'>${if (r.getString("source") == "confirmed") "confirmed" else "found"}</span><div class=meta>${n(r.getDouble("previous_usd"))} → ${n(r.getDouble("usd"))}${if (ch is Double) " (${if (ch >= 0) "+" else ""}$ch%)" else if (ch == "new") " (new)" else ""} · ${r.getInt("new_payers")} new payers, ${r.getInt("repeat_payers")} repeat</div></div>") } }
        trRows("rising", "Nothing rose above the base of $${Analytics.MIN_BASE_USD.toInt()}."); sb.append("<div class=label style='margin-top:14px'>Falling</div>"); trRows("falling", "Nothing fell."); sb.append("<div class=label style='margin-top:14px'>Newcomers</div>"); trRows("newcomers", "No app took its first payment this window.")
        sb.append("</div></div>")
        val rails = pt.getJSONObject("rails")
        val skrTop = an.leaderboard(window, "all", 5, rail = "skr").getJSONArray("rows")
        sb.append("<div class=card><div class=label>Paid in SKR</div><p class=meta>${rails.getJSONObject("skr").getInt("payments")} payments worth ${n(rails.getJSONObject("skr").getDouble("usd"))} in SKR, ${esc(an.label(window))}${if (skrTop.length() > 0) ": " + (0 until skrTop.length()).joinToString(" · ") { i -> val r = skrTop.getJSONObject(i); "<a href='/app/${enc(r.getString("package"))}'>${esc(r.getString("name"))}</a> ${n(r.getDouble("usd"))}" } else ""}. Undersigned's own Pro, listings and passes are paid in SKR too (369 SKR a year).</p></div>")
        sb.append("<div class=grid2><div class=card><div class=label>How it was paid</div><div class=stats>" + listOf("sol" to "SOL", "skr" to "SKR", "usdc" to "USDC").joinToString("") { (k, l) -> "<div class=stat><b>${rails.getJSONObject(k).getDouble("share")}%</b><span>$l · ${rails.getJSONObject(k).getInt("payments")} payments</span></div>" } + "</div>" +
            "<p class=meta>Median payment ${n(pt.getJSONObject("payment_size_usd").getDouble("median"))}; 90th percentile ${n(pt.getJSONObject("payment_size_usd").getDouble("p90"))}; largest ${n(pt.getJSONObject("payment_size_usd").getDouble("max"))}.</p>" +
            "<div class=meta>By weekday (Mon–Sun): ${(0 until 7).joinToString(" · ") { pt.getJSONArray("by_weekday").getInt(it).toString() }}</div></div>")
        sb.append("<div class=card><div class=label>Wallets</div>")
        for (i in 0 until minOf(10, ts.length())) { val r = ts.getJSONObject(i); sb.append("<div class=rev style='padding:8px 0'><code>${esc(r.getString("wallet").take(4))}…${esc(r.getString("wallet").takeLast(4))}</code> <span class='pill ${if (r.getString("source") == "confirmed") "g" else "w"}'>${r.getString("source")}</span> <b>${n(r.getDouble("usd"))}</b><div class=meta>${(0 until r.getJSONArray("apps").length()).joinToString(", ") { j -> esc(r.getJSONArray("apps").getJSONObject(j).optString("name")) }} · ${r.getInt("payments")} payments · ${r.getInt("payers")} payers</div></div>") }
        sb.append("</div></div>")
        sum.optJSONObject("coverage")?.let { c -> if (c.optInt("found_transactions_read") < c.optInt("found_transactions_listed")) sb.append("<p class=meta>Coverage: ${"%,d".format(java.util.Locale.US, c.optInt("found_transactions_read"))} of ${"%,d".format(java.util.Locale.US, c.optInt("found_transactions_listed"))} transactions into found wallets read so far; their figures are a floor until the read completes.</p>") }
        sb.append("<p class=small>Data: <a href='/v1/payments/summary?window=$window'>/v1/payments/summary</a> (JSON) · charts: <a href='/v1/payments/chart/top.svg?window=$window'>top.svg</a>, <a href='/v1/payments/chart/store.svg?window=$window'>store.svg</a>, <code>/v1/payments/chart/app/&lt;package&gt;.svg</code> · ${esc(lb.getString("label"))} Publishers: a wallet found in your code is listed as yours only once you confirm it in the app; to have one removed, <a href='/report'>tell us</a>. Disclosure: Undersigned is run by AardAppvark, publisher of 28 dApps on this store; the confirmed treasuries today are those apps', and reviews from its own wallets never count.</p>")
        return sb.toString()
    }

    /** What this site can and cannot do about content, and who to write to. */
    /** Live status from the indexer's own clocks: when the ledger and the store were last read, and whether the AI aids are on. */
    private fun status(): String {
        val now = System.currentTimeMillis()
        fun ago(t: Long) = if (t <= 0) "not yet" else ((now - t) / 60_000).let { m -> if (m < 1) "under a minute ago" else if (m < 120) "$m minutes ago" else "${m / 60} hours ago" }
        fun ok(t: Long, maxMin: Long) = t > 0 && now - t < maxMin * 60_000
        val ledger = indexer.lastLedgerSync; val store = indexer.lastStoreSync; val ai = views.ai?.enabled == true
        // The independent recompute (api verify, run by a timer every six hours on the server): the chain alone reproduces the scores.
        val verify = runCatching { java.io.File(java.io.File(Config.DB_PATH).absoluteFile.parentFile, "verify.txt").takeIf { it.exists() }?.let { f -> f.lastModified() to f.readText().trim().lines().lastOrNull().orEmpty() } }.getOrNull()
        val rows = listOf(Triple("Ledger (Solana)", ok(ledger, 30), "read ${ago(ledger)}"), Triple("dApp Store mirror", ok(store, 26 * 60), "read ${ago(store)}"),
            Triple("API and MCP", true, "answering"), Triple("AI reading aids", ai, if (ai) "on; ${views.ai?.callsToday() ?: 0} model calls today" else "off"),
            Triple("Scores recomputed from the chain alone", verify?.second?.startsWith("OK") == true && ok(verify.first, 8 * 60), verify?.let { "${it.second.take(90)} · ${ago(it.first)}" } ?: "not run yet (api verify)"))
        return "<h1>Status</h1><p class=sub>Read from the server's own clocks when this page loaded. Raw: <a href='/v1/health'>/v1/health</a>.</p><div class=card>" +
            rows.joinToString("") { (name, good, what) -> "<div class=rev><b>${if (good) "Working" else "Check"}</b> · ${esc(name)} <span class=meta>· ${esc(what)}</span></div>" } + "</div>"
    }

    private fun serverCard(): JSONObject = JSONObject().put("name", "dev.provenby.undersigned/mcp").put("title", "Undersigned")
        .put("description", "Reviews of Solana dApp Store apps, signed on-chain by Seeker owners, beside the store's own rating. Read-only.")
        .put("version", "1.0.0").put("websiteUrl", "${Config.PUBLIC_URL}/api")
        .put("remotes", JSONArray().put(JSONObject().put("type", "streamable-http").put("url", "${Config.PUBLIC_URL}/mcp")))
        .put("authentication", JSONObject().put("required", false).put("schemes", JSONArray().put("bearer")).put("note", "Optional API pass for the pass-only tools; see ${Config.PUBLIC_URL}/v1/pricing"))
        .put("capabilities", JSONObject().put("tools", true).put("resources", true).put("prompts", true))
        .put("readOnly", true)

    /** The plain-text docs, also what the docs helper (POST /v1/ask) answers from. */
    fun llmsText(): String = llmsTxt()

    private fun llmsTxt(): String = """# Undersigned

> Reviews of Solana dApp Store apps, each signed on-chain by a Seeker owner's own wallet, beside the store's own rating. One voice per attested device (Seeker Genesis Token); no payment and no token balance changes a score. Stake is shown per review, never weighed.

## Use it
- REST API, no key to start: ${Config.PUBLIC_URL}/v1/ (OpenAPI: ${Config.PUBLIC_URL}/v1/openapi.json)
- MCP server (Streamable HTTP, read-only): ${Config.PUBLIC_URL}/mcp
- Developer page: ${Config.PUBLIC_URL}/api
- Pricing and limits: ${Config.PUBLIC_URL}/v1/pricing

## Key endpoints
- GET /v1/apps?q=&category=&sort=: search the catalogue with signed scores; ?packages=a,b,c reads many apps in one call (20 free, 100 with a key)
- GET /v1/apps/{package}: signed score and count, Pro members' and promoted scores apart, the store's rating, publisher proof
- GET /v1/apps/{package}/reviews?sort=newest|helpful|skr|sol: every signal per review
- GET /v1/apps/{package}/bursts: rating bursts (facts, not verdicts)
- GET /v1/dataset.json: the whole ledger, free
- GET /v1/records?type=&package=&signer=: decoded records
- GET /v1/apps/{package}/summary: suggested summary by AI of counted reviews (labelled)
- GET /v1/records/{id}/translation?lang=: suggested translation by AI (Pro wallet or key)
- POST /v1/ask: docs helper
- POST /v1/help {question, lang}: how scoring works, answered by AI from the integrity policy only (labelled, free)
- GET /v1/digest: this week's store-wide bursts and app updates in plain words, by AI (labelled; Pro wallet or key; also a weekly `digest` webhook)
- GET /v1/changelog: what changed in the API and MCP
- GET /v1/payments: payments board (publisher-confirmed treasuries); GET /v1/payments/found: wallets found in app code, NOT confirmed (separate, labelled, never ranked); /v1/payments/found/{package}: full-history analytics; /v1/payments/found/trends: store-wide monthly

## MCP tools
search_apps, get_app, get_apps, scoring_help, get_reviews, get_bursts, get_wallet, get_record, list_records, leaderboard, payments_board, ledger_stats, review_tags, get_pricing, app_summary (AI, labelled), translate_review (AI, Pro wallet or key), weekly_digest (AI, Pro wallet or key), found_treasuries (labelled: found in app code, not confirmed), review_analytics (every review source: the store's own reviews and signed reviews by class, by category, dApp and over time); with a pass: store_watch, app_changes, version_scores, similar_reviews, store_rating_history. Resources: undersigned://policy, undersigned://pricing, undersigned://tags, undersigned://app/{package}. Prompts: audit_app_reputation, compare_apps.

## Rules for describing the data
- Quote a score with its counted-review count.
- Bursts and similarity are facts about timing and wording; do not call an app or a review fake.
- Integrity policy: https://aardappvark.github.io/undersigned-legal/integrity.html
"""

    private fun report(): String = """<h1>Report content</h1>
<p>Every review, reply and note shown here was signed by its author's wallet and recorded on Solana. Nobody can change or delete a record on-chain: not its author, not this site.</p>
<p>What this site can do is stop showing a record's <b>text</b>. When it does, the record still appears with its rating, tags and date, marked "Text not shown on this site". The hide list is the operator's, applies to this site and its API only, and does not touch the Undersigned app. Separately, a moderator can hide a record's text everywhere, the app included, by signing a public record (<a href='/moderation'>how moderation works</a>).</p>
<p>Write to us about text that is unlawful where you are, exposes private information, or targets a person: ${if (Config.CONTACT.startsWith("http")) "<a href='${esc(Config.CONTACT)}'>${esc(Config.CONTACT)}</a>" else "<a href='mailto:${esc(Config.CONTACT)}'>${esc(Config.CONTACT)}</a>"}. Say which record (its page link, or the transaction signature) and why. Disagreeing with a rating is not a reason; publishers can answer a review with a signed reply instead.</p>
<p class=small>Wallet addresses and .skr names shown here are public on-chain data. See the privacy policy for what the server itself keeps.</p>"""

    /** The moderation policy (docs/MODERATION.md), and every moderation record, newest first. */
    private fun moderation(): String {
        val st = indexer.state
        val mods = st.entries.filter { it.memo is com.undersigned.protocol.ReviewMemo.Moderate || it.memo is com.undersigned.protocol.ReviewMemo.Unmoderate }.sortedByDescending { it.blockTime }
        val list = if (mods.isEmpty()) "<p class=meta>No moderation records yet.</p>" else mods.take(100).joinToString("") { e ->
            val m = e.memo
            val target = (m as? com.undersigned.protocol.ReviewMemo.Moderate)?.recordId ?: (m as com.undersigned.protocol.ReviewMemo.Unmoderate).recordId
            val what = if (m is com.undersigned.protocol.ReviewMemo.Moderate) "hid the text of" else "showed again the text of"
            val why = (m as? com.undersigned.protocol.ReviewMemo.Moderate)?.let { " · " + esc(modReason(it.reason.wire)) } ?: ""
            "<div class=rev><span class=meta>${day(e.blockTime)} · ${short(e.signer)}</span> $what <code>${esc(target.take(16))}…</code>$why</div>"
        }
        val contact = if (Config.CONTACT.startsWith("http")) "<a href='${esc(Config.CONTACT)}'>${esc(Config.CONTACT)}</a>" else "<a href='mailto:${esc(Config.CONTACT)}'>${esc(Config.CONTACT)}</a>"
        val who = com.undersigned.protocol.Moderation.MODERATORS.joinToString(", ") { short(it) }
        return "<h1>Moderation</h1>" +
            "<p>Moderation on Undersigned hides <b>text</b>, never a voice. A moderator can stop a review's or reply's words from being shown; its rating, tags and weight still count, and the record stays on Solana, where nobody can change or delete it.</p>" +
            "<div class=card><div class=label>Who moderates</div><p>The wallets named in the app's and this site's source code (<code>Moderation.MODERATORS</code>). Today that is one wallet, the operator's: <code>${esc(who)}</code>. Adding or removing one is a new release, visible to anyone.</p></div>" +
            "<div class=card><div class=label>The only four reasons</div><p>Illegal content · someone's personal data (names, addresses, phone numbers, keys or seed words) · abuse or harassment aimed at a person · impersonating a publisher.</p>" +
            "<p class=meta>Not reasons: a low rating, a harsh or negative opinion, criticism of an app or its publisher, or a review someone disagrees with. Spam and off-topic records are for community flags, which any Seeker owner can sign.</p></div>" +
            "<div class=card><div class=label>How it works</div><p>The moderator signs a public record naming the record and the reason. Every reader, the app and this site, then shows \"Text hidden by a moderator\" and the reason in place of the text. The same moderator can undo it with a second record; the later one counts. A moderator's record on a review of an app that moderator is the proven publisher of is ignored.</p></div>" +
            "<div class=card><div class=label>If your text was hidden</div><p>Sign a dispute from the app (it is shown beside your record), or write to $contact with the record id. A moderator looks again within 7 days and says what was decided.</p></div>" +
            "<h2>Every moderation record</h2>$list" +
            "<h2>Hidden on this site only</h2><p class=meta>Text this site does not serve, for the same four reasons. The record stays on Solana and in the open dataset; the app is not affected.</p>" +
            st.operatorHidden.entries.sortedBy { it.key }.let { h -> if (h.isEmpty()) "<p class=meta>Nothing is hidden.</p>" else h.joinToString("") { (id, why) -> "<div class=rev><code>${esc(id.take(16))}…</code> · ${esc(modReason(why))}</div>" } }
    }

    private fun sitemap(): String {
        val st = indexer.state
        val urls = listOf("/", "/watch", "/review", "/report", "/moderation") + st.scores.keys.sorted().map { "/app/${enc(it)}" }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" + urls.joinToString("") { "<url><loc>${esc(Config.PUBLIC_URL + it)}</loc></url>" } + "</urlset>"
    }

    /** Helpful vote, reply or flag on one record, signed by the reader's wallet: connect and sign in one step. */
    private fun act(kind: String, target: String, lang: String): Api.Response {
        val rec = views.recordById(target)
        if (kind !in setOf("up", "reply", "flag") || rec == null) return notFound()
        val title = when (kind) { "up" -> "Mark as helpful"; "reply" -> "Reply to this review"; else -> "Flag this record" }
        val about = "<div class=card><div class=meta>${esc(rec.optString("package", rec.optString("type")))} · ${day(rec.optLong("time"))} · ${short(rec.getString("signer"))}</div>" +
            (if (rec.has("rating")) "<div class=stars>${"★".repeat(rec.optInt("rating"))}</div>" else "") + (if (!rec.isNull("text") && rec.has("text")) "<p class=txt>${esc(rec.optString("text"))}</p>" else "") + "</div>"
        val inputs = when (kind) {
            "reply" -> "<label class=label for=text>Your reply (140 characters)</label><textarea id=text maxlength=140 rows=3></textarea><label class=check><input type=checkbox id=permanent><span class=meta>I understand my reply stays on-chain for good.</span></label>"
            "flag" -> "<label class=label for=reason>Reason</label><select id=reason>${com.undersigned.protocol.ReviewMemo.FlagReason.entries.joinToString("") { "<option value='${it.wire}'>${esc(I18n.flag(lang, it.wire))}</option>" }}</select><p class=meta>A flag is a signed opinion from your wallet. Records are hidden in the app only when flags from several attested devices agree.</p>"
            else -> "<p class=meta>A helpful vote is a signed record from your wallet. It counts when the wallet holds a Seeker Genesis Token.</p>"
        }
        val pkg = rec.optString("package", "")
        val body = """<p class=small><a href='/app/${enc(pkg)}'>Back to the app</a></p><h1>$title</h1>$about
<ol class=stepper aria-label=Progress><li class=on>1 · Connect</li><li>2 · Check</li><li>3 · Sign</li><li>4 · Recorded</li></ol>
<div class=card>$inputs
<label class=check><input type=checkbox id=terms><span class=meta>I accept the <a href='https://aardappvark.github.io/undersigned-legal/terms.html' target=_blank>Terms</a>. You pay only the Solana network fee your wallet sets (about 0.00005 SOL).</span></label>
<div class=ctas><button id=go type=button>Sign with your wallet</button></div><p class=meta id=status aria-live=polite></p>
<p class=small>On a Seeker, open this page in Chrome and approve with the Seeker Wallet. On a computer, use a wallet extension such as Phantom, Solflare or Backpack.</p></div>
<script type=module>
$JS_HELPERS
const KIND = '${esc(kind)}', TARGET = '${esc(target)}', LIVE = '/app/${enc(pkg)}';
$('go').onclick = async () => {
  if (!$('terms').checked) { toast('Tick the Terms box first.', 'warn'); return; }
  if (KIND === 'reply' && (!$('text').value.trim() || !$('permanent').checked)) { toast('Write the reply and tick the box about permanence.', 'warn'); return; }
  try {
    if (!W.address) { busy($('go'), true, ANDROID ? 'Opening your wallet…' : 'Choose a wallet…'); step(1); await connectWallet(); busy($('go'), false); toast('Connected to ' + esc(W.label || 'your wallet') + '.', 'ok', 4000); }
    busy($('go'), true, 'Building…'); step(2);
    const r = await fetch('/v1/tx/record', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ account: W.address, kind: KIND, target: TARGET, text: $('text') ? $('text').value.trim() : undefined, reason: $('reason') ? $('reason').value : undefined }) });
    const j = await r.json(); if (!r.ok) { busy($('go'), false); toast(esc(j.message || j.error), 'err'); return; }
    busy($('go'), false); busy($('go'), true, ANDROID ? 'Approve in your wallet (double-tap)…' : 'Approve in your wallet…'); step(3);
    const sig = await sendTx(j.transaction);
    busy($('go'), false); $('go').disabled = true; $('go').innerHTML = 'Signed';
    follow(sig, LIVE, KIND === 'reply' ? 'Your reply' : KIND === 'up' ? 'Your helpful vote' : 'Your flag');
  } catch (e) { busy($('go'), false); toast('Not signed: ' + esc(explain(e)), 'err'); }
};
</script>"""
        return page("$title — Undersigned", body, lang = lang)
    }

    /** How a publisher proves an app is theirs, and what this server currently reads for one package (from its cache only). */
    private fun publishers(pkg: String?): String {
        val sb = StringBuilder("""<h1>For publishers</h1>
<p class=sub>Anyone can sign "I publish this app". A claim only shows as proven when one of two things is true.</p>
<div class=card><div class=label>1 · Name your wallet in the app itself</div><p>Add one line inside <code>&lt;application&gt;</code> in your manifest and ship a release:</p>
<pre class=code>&lt;meta-data android:name="undersigned.publisher" android:value="YOUR_WALLET_ADDRESS" /&gt;</pre>
<p class=meta>The manifest is covered by your signing key, so nobody else can put a wallet there. Readers check the build the dApp Store serves and the certificate the store lists. Several wallets: separate them with commas.</p></div>
<div class=card><div class=label>2 · Or put a file on the website the store lists for your app</div><p>Serve <code>/.well-known/undersigned.txt</code> with your wallet address on a line by itself. To list a treasury on the payments board, add a line <code>treasury: ADDRESS</code>. Then list it in the app: the treasury wallet signs the listing itself, or, if that wallet should never sign anything, your proven publisher wallet lists it for the treasury (same fee). Nobody else can list a treasury for your app.</p>
<p class=meta>A GitHub website is read from the repository's default branch. Social profiles, link pages and shared hosts are not accepted as proof.</p></div>
<div class=card><div class=label>Then</div><p>Sign the claim from that wallet in the Undersigned app (open your app's page, "I publish this app"). Your replies then carry the Publisher mark, and your own reviews of your app are not counted.</p></div>
<div class=card><div class=label>What does not prove it</div><p>The wallet's own signature only shows who is claiming. A payment address, or the wallet that published the app on the dApp Store, does not prove it either: the store does not say which on-chain record belongs to which listing, and anyone can mint one naming any app. The proof is something only the publisher controls &mdash; the app's signing key, or the website the store lists &mdash; naming the same wallet.</p></div>
<h2>Check a package</h2><form method=get action='/publishers'><input name=package value='${esc(pkg ?: "")}' placeholder='com.example.app' aria-label='Package name'></form>""")
        if (!pkg.isNullOrEmpty()) {
            val st = indexer.state; val d = st.details[pkg]; val build = d?.let { indexer.apkFacts(pkg, it.versionCode) }
            val url = d?.website?.let { com.undersigned.protocol.PublisherProof.wellKnownUrl(it) }
            sb.append("<div class=card>")
            if (st.byPackage[pkg] == null) sb.append("<div class=meta>This package is not in the store catalogue this server has read.</div>")
            else if (d == null) sb.append("<div class=meta>The store's details for this app have not been read yet. They are read once someone reviews or claims it.</div>")
            else {
                sb.append("<div class=meta>Store version ${d.versionCode} · certificate <code>${esc((d.certFingerprint ?: "–").take(16))}…</code></div>")
                sb.append("<div class=meta>Website the store lists: ${esc(d.website ?: "none")}${if (d.website != null && url == null) " — not accepted as proof (a shared host)" else ""}</div>")
                url?.let { sb.append("<div class=meta>Proof file read from: <code>${esc(it)}</code></div>") }
                sb.append(when { build == null -> "<div class=meta>The store's build has not been read yet (it is read once the app is claimed).</div>"
                    build.declared.isEmpty() -> "<div class=meta>The store's build names no publisher wallet.</div>"
                    else -> "<div class=meta>The store's build names: ${build.declared.joinToString(", ") { short(it) }}${if (d.certFingerprint in build.certSha256) "" else " — but its certificate is not the one the store lists"}</div>" })
                val vp = st.verified[pkg].orEmpty()
                sb.append(if (vp.isEmpty()) "<div class=meta>No proven claim yet.</div>" else "<div><span class='pill g'>Proven: ${vp.joinToString(", ") { short(it) }}</span></div>")
            }
            sb.append("</div>")
        }
        return sb.toString()
    }

    /** Counted reviews of one app as an Atom feed, newest first. */
    private fun feed(pkg: String, a: JSONObject): Api.Response {
        val list = views.reviews(pkg, 30, 0).getJSONArray("reviews")
        fun iso(t: Long) = java.time.Instant.ofEpochSecond(t).toString()
        val entries = (0 until list.length()).map { list.getJSONObject(it) }.filter { it.getJSONObject("reviewer").optBoolean("attested_device") }.joinToString("") { r ->
            val tags = r.optJSONArray("tags")?.let { t -> (0 until t.length()).joinToString(", ") { I18n.tag("en", t.getString(it)) } } ?: ""
            "<entry><id>urn:undersigned:${esc(r.getString("id"))}</id><title>${r.optInt("rating")} of 5 · ${esc(tags)}</title><updated>${iso(r.optLong("time"))}</updated><link href='${esc(Config.PUBLIC_URL)}/app/${enc(pkg)}'/><content type='text'>${esc(if (r.isNull("text")) tags else r.optString("text"))}</content></entry>" }
        val xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><feed xmlns=\"http://www.w3.org/2005/Atom\"><title>${esc(a.optString("name", pkg))} — signed reviews on Undersigned</title><id>urn:undersigned:app:${esc(pkg)}</id><link href='${esc(Config.PUBLIC_URL)}/app/${enc(pkg)}'/><updated>${iso(indexer.state.builtAt / 1000)}</updated>$entries</feed>"
        return Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=300"), xml.toByteArray(), "application/atom+xml; charset=utf-8")
    }

    private fun wallet(w: String): String {
        val j = views.wallet(w, false)
        val sb = StringBuilder("<h1>${esc(if (j.isNull("skr_name")) short(w) else j.getString("skr_name"))}</h1><p class=meta><code>${esc(w)}</code></p><div class=card><div class=stats>")
        if (j.optBoolean("banned")) { sb.append("<div class=stat><b>Excluded</b><span>a moderator's signed record excludes this wallet for abuse; its records stay on Solana and are shown nowhere here</span></div></div></div>"); return sb.toString() }
        if (j.isNull("weight") && j.optInt("reviews") == 0 && j.isNull("standing")) {
            sb.append("<div class=stat><b>Not seen yet</b><span>this wallet has signed nothing on the ledger, or the index has not read it yet</span></div></div></div>")
            return sb.toString()
        }
        sb.append("<div class=stat><b>${if (j.optBoolean("attested_device")) "Yes" else "No"}</b><span>attested device</span></div><div class=stat><b>${j.opt("weight")}×</b><span>how much each review counts</span></div><div class=stat><b>${j.optInt("reviews")}</b><span>live reviews</span></div>")
        j.optJSONObject("standing")?.let { sd -> sb.append("<div class=stat><b>${sd.optInt("apps_reviewed")}</b><span>apps reviewed</span></div><div class=stat><b>${sd.optInt("follow_ups")}</b><span>follow-ups, ${sd.optInt("fixes_confirmed")} confirming a fix</span></div><div class=stat><b>${sd.optInt("flags_upheld")}</b><span>flags upheld of ${sd.optInt("flags_given")}</span></div>") }
        sb.append("</div></div>")
        val pubs = j.optJSONArray("publisher_of") ?: JSONArray()
        if (pubs.length() > 0) sb.append("<p>Proven publisher of: ${(0 until pubs.length()).joinToString(", ") { "<a href='/app/${enc(pubs.getString(it))}'>${esc(pubs.getString(it))}</a>" }}</p>")
        val recs = views.records("rev", null, w, null, null, null, 50).getJSONArray("records")
        sb.append("<h2>Signed reviews</h2>")
        // By month, so a reader sees the pattern over time (owner, 11 Oct 2026).
        (0 until recs.length()).map { java.time.Instant.ofEpochSecond(recs.getJSONObject(it).optLong("time")).toString().take(7) }.groupingBy { it }.eachCount().toSortedMap().takeIf { it.isNotEmpty() }
            ?.let { m -> sb.append("<p class=small>By month: ${m.entries.joinToString(" · ") { "${it.key} ×${it.value}" }}</p>") }
        for (i in 0 until recs.length()) { val r = recs.getJSONObject(i)
            sb.append("<div class=rev><a href='/app/${enc(r.optString("package"))}'>${esc(indexer.state.byPackage[r.optString("package")]?.name ?: r.optString("package"))}</a> <span class=stars>${"★".repeat(r.optInt("rating"))}</span>${if (r.optBoolean("withdrawn")) " <span class=pill>withdrawn</span>" else ""}<div class=meta>${stamp(r.optLong("time"))}${r.optJSONArray("tags")?.let { t -> " · " + (0 until t.length()).joinToString(", ") { I18n.tag("en", t.getString(it)) } } ?: ""}</div></div>") }
        return sb.toString()
    }

    // ---- Pieces -------------------------------------------------------------------------------------------------------

    private fun modReason(wire: String) = when (wire) { "illegal" -> "illegal content"; "personal" -> "someone's personal data"; "abuse" -> "abuse or harassment"; "impersonation" -> "impersonates a publisher"; else -> wire }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    /** Cuts long store text at a word boundary and marks the cut. */
    private fun clip(t: String, max: Int): String = if (t.length <= max) t else t.take(max).substringBeforeLast(' ', t.take(max)).trimEnd(',', '.', ';', ':') + "…"

    /** An http(s) URL as a link that leaves no referrer; anything else as plain text. */
    private fun link(u: String): String = if (u.startsWith("https://") || u.startsWith("http://")) "<a href='${esc(u)}' rel='nofollow noopener noreferrer'>${esc(u.substringAfter("://").trimEnd('/'))}</a>" else esc(u)

    /** The star histogram; with [pkg] each row links to the reviews of that rating (signed reviews only). */
    private fun bars(stars: JSONArray?, pkg: String? = null): String {
        stars ?: return ""; val max = (0 until 5).maxOf { stars.optInt(it) }.coerceAtLeast(1)
        return "<div class=bars>" + (5 downTo 1).joinToString("") { s -> val n = stars.optInt(s - 1)
            val row = "<span>$s★</span><i style='width:${(100.0 * n / max).toInt()}%'></i><span>$n</span>"
            if (pkg != null && n > 0) "<a class=bar href='/app/${enc(pkg)}?stars=$s#reviews' title='read the $s-star reviews'>$row</a>" else "<div class=bar>$row</div>" } + "</div>"
    }

    private fun spark(sp: JSONObject): String {
        val c = sp.getJSONArray("daily_counts"); val n = c.length(); if (n == 0) return ""
        val max = (0 until n).maxOf { c.optInt(it) }.coerceAtLeast(1); val from = sp.optInt("burst_from"); val to = sp.optInt("burst_to")
        val w = 300.0 / n
        val rects = (0 until n).joinToString("") { i -> val h = 36.0 * c.optInt(i) / max
            "<rect x='${"%.1f".format(java.util.Locale.US, i * w)}' y='${"%.1f".format(java.util.Locale.US, 40 - h)}' width='${"%.1f".format(java.util.Locale.US, w * 0.8)}' height='${"%.1f".format(java.util.Locale.US, h)}' fill='${if (i in from..to) "#ffc46b" else "#6b5f8f"}'/>" }
        return "<svg class=spark viewBox='0 0 300 52' role='img' aria-label='Reviews per day over 30 days; the burst days are shaded and marked'><rect x='${"%.1f".format(java.util.Locale.US, from * w)}' y='0' width='${"%.1f".format(java.util.Locale.US, (to - from + 1) * w)}' height='42' fill='#ffc46b' opacity='.12'/>$rects<text x='${"%.1f".format(java.util.Locale.US, from * w)}' y='51' font-size='8' fill='#ffc46b'>burst</text></svg>"
    }

    /**
     * Every page carries a Content Security Policy: scripts only from this origin's nonce and the wallet libraries' CDN,
     * connections only to this origin, that CDN and the wallet's local websocket; nothing else loads or runs. The nonce is
     * fresh per response, so an injected <script> (were escaping ever to fail) would not execute.
     */
    fun page(title: String, body: String, status: Int = 200, head: String = "", lang: String = "en", description: String = "Reviews of Solana dApp Store apps, signed on-chain by Seeker owners. Facts, not verdicts."): Api.Response {
        val nonce = Base58.encode(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })
        val withNonce = body.replace("<script type=module>", "<script type=module nonce=\"$nonce\">")
        val html = "<!doctype html><html lang=${I18n.htmlLang(lang)}><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>${esc(title)}</title><link rel=icon href='/icon.svg'><meta name=description content='${esc(description)}'><meta property='og:title' content='${esc(title)}'><meta property='og:description' content='${esc(description)}'><meta property='og:image' content='${Config.PUBLIC_URL}/icon.svg'><meta name='twitter:card' content='summary'><style nonce=\"$nonce\">$CSS</style>$head</head><body>$HEADER<main>$withNonce</main>$FOOTER<div class=toasts id=toasts aria-live=polite></div></body></html>"
        val csp = "default-src 'none'; script-src 'nonce-$nonce' https://cdn.jsdelivr.net; style-src 'unsafe-inline'; img-src 'self' data: https:; " +
            "connect-src 'self' https://cdn.jsdelivr.net ws://localhost:* wss://localhost:* ws://127.0.0.1:* wss://127.0.0.1:*; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
        return Api.Response(status, JSONObject(), mapOf("Cache-Control" to "public, max-age=60", "Content-Security-Policy" to csp, "Referrer-Policy" to "no-referrer"), html.toByteArray(), "text/html; charset=utf-8")
    }

    private fun asset(name: String): Api.Response? {
        if (!Regex("^[a-z0-9-]+\\.(ttf|jpg|txt)$").matches(name)) return null
        val bytes = Web::class.java.getResourceAsStream("/web/$name")?.use { it.readBytes() } ?: return null
        val type = when (name.substringAfterLast('.')) { "ttf" -> "font/ttf"; "jpg" -> "image/jpeg"; else -> "text/plain; charset=utf-8" }
        return Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=604800"), bytes, type)
    }

    private fun notFound() = page("Not found — Undersigned", "<h1>Not found</h1><p><a href='/'>Back to Undersigned</a></p>", 404)

    companion object {
        /** The Undersigned mark (the app's launcher art): a ring struck through, on the app's background. */
        const val ICON = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 108 108'><rect width='108' height='108' rx='24' fill='#120e22'/><g fill='none' stroke='#F2EEFF' stroke-width='5.54' stroke-linecap='round'><circle cx='54' cy='54' r='20.68'/><path d='M28.52 28.52 79.48 79.48'/></g></svg>"
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun dec(s: String) = java.net.URLDecoder.decode(s, "UTF-8")
        fun short(w: String) = if (w.length > 10) w.take(4) + "…" + w.takeLast(4) else w
        fun day(t: Long) = java.time.Instant.ofEpochSecond(t).toString().take(10)
        fun stamp(t: Long) = java.time.Instant.ofEpochSecond(t).toString().take(16).replace('T', ' ') + " UTC"
        /** Wallet helpers shared by the action pages: base58, base64, the direct Mobile Wallet Adapter import, and plain error text. */
        const val JS_HELPERS = """const $ = (id) => document.getElementById(id); const st = (t) => { const s = $('status'); if (s) s.textContent = t; };
const IDENTITY = { name: 'Undersigned', uri: location.origin, icon: '/icon.svg' };
// Android: the Mobile Wallet Adapter protocol directly (the Wallet Standard wrapper waits forever for a local-network
// prompt Chrome never raises on localhost, 29 Sep 2026). Elsewhere: Wallet Standard (browser extensions).
const ANDROID = /Android/i.test(navigator.userAgent) && !/Firefox\//.test(navigator.userAgent);
const B58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function bs58(bytes) { let n = 0n; for (const b of bytes) n = n * 256n + BigInt(b); let s = ''; while (n > 0n) { s = B58[Number(n % 58n)] + s; n /= 58n; } for (const b of bytes) { if (b !== 0) break; s = '1' + s; } return s; }
const unb64 = (s) => Uint8Array.from(atob(s), c => c.charCodeAt(0));
const esc = (t) => String(t).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
async function mwa() { return (await import('https://cdn.jsdelivr.net/npm/@solana-mobile/mobile-wallet-adapter-protocol@2.3.0/+esm')).transact; }
function explain(e) { const c = e && e.code; const m = (e && e.message || String(e)).toLowerCase();
  if (c === 'ERROR_WALLET_NOT_FOUND') return 'No wallet app opened. On a Seeker, open this page in Chrome; the Seeker Wallet supports it.';
  if (c === 'ERROR_ASSOCIATION_CANCELLED' || c === 'CANCELLED' || m.includes('reject') || m.includes('declin') || m.includes('cancel')) return 'Cancelled in the wallet. Nothing was signed.';
  if (m.includes('insufficient')) return 'Not enough SOL in this wallet for the network fee.';
  if (c === 'NO_WALLET') return e.message;
  return (c ? c + ' — ' : '') + (e && e.message || e); }
// Notices: one line per step, kept short; links open in a new tab.
function toast(html, kind = 'info', ms = 8000) { const box = $('toasts'); if (!box) return; const t = document.createElement('div'); t.className = 'toast ' + kind; t.innerHTML = html; box.appendChild(t); if (ms) setTimeout(() => t.remove(), ms); return t; }
function busy(btn, on, label) { if (!btn) return; if (on) { btn.dataset.l = btn.innerHTML; btn.disabled = true; btn.innerHTML = '<span class=spin></span>' + esc(label || 'Working…'); } else { btn.disabled = false; if (btn.dataset.l) btn.innerHTML = btn.dataset.l; } }
function step(n) { document.querySelectorAll('.stepper li').forEach((li, i) => { li.className = i + 1 < n ? 'done' : (i + 1 === n ? 'on' : ''); }); }
// A wallet picker instead of prompt(): each installed wallet with its own icon.
function pickWallet(ws) { return new Promise((res, rej) => {
  if (ws.length === 1) return res(ws[0]);
  const m = document.createElement('div'); m.className = 'modal';
  m.innerHTML = '<div class=box role=dialog aria-modal=true aria-label="Choose a wallet"><h2 style="margin-top:0">Choose a wallet</h2><div class=wl></div><button class=sec style="width:100%;margin-top:8px">Cancel</button></div>';
  ws.forEach(w => { const b = document.createElement('button'); b.className = 'wallet'; b.innerHTML = (w.icon ? '<img alt="" src="' + esc(w.icon) + '">' : '') + '<span>' + esc(w.name) + '</span>';
    b.onclick = () => { m.remove(); res(w); }; m.querySelector('.wl').appendChild(b); });
  m.querySelector('button.sec').onclick = () => { m.remove(); rej(Object.assign(new Error('cancelled'), { code: 'CANCELLED' })); };
  document.body.appendChild(m); }); }
const W = { mode: null, wallet: null, account: null, address: null, label: null, authToken: null };
async function connectWallet() {
  if (ANDROID) {
    const transact = await mwa();
    const auth = await transact(async (w) => w.authorize({ chain: 'solana:mainnet', identity: IDENTITY }));
    const a = auth.accounts && auth.accounts[0]; if (!a) throw new Error('The wallet returned no account.');
    Object.assign(W, { mode: 'mwa', authToken: auth.auth_token, address: bs58(unb64(a.address)), label: a.label || 'Seeker Wallet' });
  } else {
    const { getWallets } = await import('https://cdn.jsdelivr.net/npm/@wallet-standard/app@1.1.0/+esm');
    const ws = getWallets().get().filter(w => w.chains.some(c => c.startsWith('solana:')) && w.features['standard:connect'] && w.features['solana:signAndSendTransaction']);
    if (!ws.length) throw Object.assign(new Error('No Solana wallet in this browser. Install Phantom, Solflare or Backpack, or open this page in Chrome on your Seeker.'), { code: 'NO_WALLET' });
    const w = await pickWallet(ws); const r = await w.features['standard:connect'].connect(); const acc = r.accounts && r.accounts[0];
    if (!acc) throw new Error('The wallet returned no account. Reload this page and try again.');
    Object.assign(W, { mode: 'std', wallet: w, account: acc, address: acc.address, label: w.name });
    if (w.features['standard:events']) w.features['standard:events'].on('change', (p) => { if (p.accounts) { W.account = p.accounts[0] || null; W.address = W.account && W.account.address; toast(W.account ? 'Wallet account changed to ' + esc(W.address.slice(0, 4) + '…' + W.address.slice(-4)) : 'The wallet disconnected.', 'warn'); } });
  }
  return W;
}
async function sendTx(b64tx) {
  if (W.mode === 'mwa') { const transact = await mwa();
    const res = await transact(async (w) => { await w.authorize({ chain: 'solana:mainnet', identity: IDENTITY, auth_token: W.authToken }); return w.signAndSendTransactions({ payloads: [b64tx] }); });
    return bs58(unb64(res.signatures[0])); }
  const [res] = await W.wallet.features['solana:signAndSendTransaction'].signAndSendTransaction({ account: W.account, transaction: unb64(b64tx), chain: 'solana:mainnet' });
  return bs58(res.signature);
}
// Recorded = this server has read the record off the chain (it reads new blocks every minute or so).
async function waitRecorded(id, ms = 600000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { await new Promise(r => setTimeout(r, 10000));
  try { if ((await fetch('/v1/records/' + encodeURIComponent(id))).ok) return true; } catch (e) {} } return false; }
async function askNotify() { if (!('Notification' in window)) return false; if (Notification.permission === 'granted') return true; if (Notification.permission === 'denied') return false; return (await Notification.requestPermission()) === 'granted'; }
function notifyLive(title, body, url) { try { if ('Notification' in window && Notification.permission === 'granted') { const n = new Notification(title, { body, icon: '/icon.svg' }); n.onclick = () => { window.focus(); if (url) location.href = url; }; } } catch (e) {} }
// After a signature: sent → recorded, with a notice at each step and an optional browser notification.
async function follow(sig, liveUrl, what) {
  step(4);
  const sent = toast('Signed and sent. <a target=_blank href="https://solscan.io/tx/' + sig + '">View on Solscan</a>. Waiting for it to be recorded… <button class=sec id=nfy style="min-height:36px;padding:6px 12px;margin-left:6px">Notify me</button>', 'info', 0);
  const nb = sent.querySelector('#nfy'); if (nb) nb.onclick = async () => { nb.remove(); toast((await askNotify()) ? 'You\'ll get a browser notification when it\'s live.' : 'Notifications are blocked in this browser; this page will say so instead.', 'info'); };
  st('Sent. Waiting for it to be recorded (usually within two minutes)…');
  const ok = await waitRecorded(sig); sent.remove();
  if (ok) { document.querySelectorAll('.stepper li').forEach(li => li.className = 'done');
    toast(esc(what) + ' is live. <a href="' + esc(liveUrl) + '">See it</a>', 'ok', 0); st(what + ' is recorded and live.'); notifyLive('Undersigned', what + ' is live.', liveUrl); }
  else { toast('Sent, but not read here yet. It is on Solana (<a target=_blank href="https://solscan.io/tx/' + sig + '">Solscan</a>) and will show once this site catches up.', 'warn', 0); st('Sent; this site has not read it yet.'); }
}"""
        /** The mark, inline (the same vector as /icon.svg and the app's launcher). */
        private val MARK: String get() = ICON.replace("<svg ", "<svg aria-hidden=true ")
        val HEADER: String get() = "<header class=top><div class=in><a class=brand href='/'>$MARK<span>Undersigned</span></a><nav class=nav aria-label=Main>" +
            "<a href='/'>Apps</a><a href='/reviews'>Reviews</a><a href='/payments'>Payments</a><a href='/watch'>Store watch</a><a href='/publishers'>Publishers</a><a href='/api'>API</a><a class=cta href='/review'>Write a review</a></nav></div></header>"
        const val FOOTER = "<footer class=foot><div class=in><div class=small style='max-width:520px'>Undersigned is an open review ledger on Solana, run by AardAppvark, publisher of 28 dApps on this store, whose own reviews never count. " +
            "Reviews are the opinions of individual Seeker owners, signed by their own wallets. Not affiliated with Solana Mobile.</div><nav class=small><a href='/review'>How to review</a><a href='/publishers'>For publishers</a><a href='/moderation'>Moderation</a>" +
            "<a href='/report'>Report content</a><a href='https://aardappvark.github.io/undersigned-legal/'>Terms and privacy</a><a href='/assets/OFL-1.1.txt'>Font licence</a></nav></div></footer>"
        const val CSS = ".chips{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0 14px}.chip{padding:6px 14px;border-radius:999px;border:1px solid var(--line,#2a2634);color:var(--t2,#B7AED1);font-size:13px;text-decoration:none}.chip.on{background:rgba(153,69,255,.18);color:#B98CFF;border-color:#B98CFF}.pill.w{background:rgba(255,181,71,.15);color:#FFB547}@font-face{font-family:Manrope;src:url(/assets/manrope.ttf) format('truetype');font-weight:200 800;font-display:swap}:root{--bg:#000;--surface:#0e0b18;--card:#120e22;--sv:#17122a;--line:rgba(153,69,255,.22);--p:#9945ff;--pd:#6b2fc7;--pl:#b98cff;--g:#14f195;--t:#f2eeff;--t2:#b7aed1;--t3:#8c83a8;--w:#ffc46b;--n:#ff6b6b;--i:#6bb9ff}*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{margin:0;background:var(--bg);color:var(--t);font:15px/1.55 Manrope,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;-webkit-font-smoothing:antialiased}a{color:var(--pl);text-underline-offset:3px}a:hover{color:#d4b8ff}.top{position:sticky;top:0;z-index:20;background:rgba(0,0,0,.82);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);border-bottom:1px solid var(--line)}.top .in{max-width:1000px;margin:0 auto;padding:10px 16px;display:flex;align-items:center;gap:14px;flex-wrap:wrap}.brand{display:flex;align-items:center;gap:10px;color:var(--t);text-decoration:none;font-weight:800;font-size:19px;letter-spacing:-.3px}.brand svg{width:30px;height:30px;border-radius:8px}.nav{display:flex;gap:4px;flex-wrap:wrap;margin-left:auto}.nav a{color:var(--t2);text-decoration:none;font-weight:600;font-size:14px;padding:8px 10px;border-radius:10px;min-height:40px;display:inline-flex;align-items:center}.nav a:hover,.nav a[aria-current=page]{color:var(--t);background:var(--sv)}.nav a.cta{background:var(--p);color:#fff}.nav a.cta:hover{background:#8a3bf0;color:#fff}main{max-width:1000px;margin:0 auto;padding:22px 16px 56px}h1{font-size:32px;line-height:1.15;font-weight:800;letter-spacing:-.6px;margin:6px 0 8px}h2{font-size:19px;font-weight:800;letter-spacing:-.2px;margin:30px 0 10px}.sub{color:var(--t2);font-size:16px;max-width:720px}.meta{color:var(--t2)}.meta,.small{font-size:13px}.small{color:var(--t3)}.card{display:block;background:var(--card);border:1px solid var(--line);border-radius:18px;padding:16px 18px;margin:12px 0;text-decoration:none;color:inherit}a.card:hover,a.link:hover{border-color:var(--p);color:inherit}.card.warn{border-color:#5a4524}.hero{position:relative;border-radius:24px;overflow:hidden;margin:4px 0 18px;min-height:360px;display:flex;align-items:flex-end;background:#0e0b18 center/cover no-repeat}.hero::before{content:'';position:absolute;inset:0;background:linear-gradient(180deg,rgba(0,0,0,.15) 0%,rgba(0,0,0,.35) 40%,rgba(0,0,0,.92) 85%)}.hero .in{position:relative;padding:28px 26px}.hero h1{font-size:38px;max-width:640px}.hero .sub{color:#d9d2ee}.ctas{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px}.btn,button{display:inline-flex;align-items:center;justify-content:center;gap:8px;background:var(--p);color:#fff;border:0;border-radius:14px;padding:12px 20px;font:700 15px/1.2 Manrope,system-ui,sans-serif;min-height:52px;cursor:pointer;text-decoration:none}.btn:hover,button:hover{background:#8a3bf0;color:#fff}.btn.sec,button.sec{background:transparent;color:var(--pl);border:1.5px solid var(--line)}.btn.sec:hover,button.sec:hover{border-color:var(--p);background:rgba(153,69,255,.08)}button[disabled]{opacity:.45;cursor:not-allowed}button .spin{width:16px;height:16px;border:2px solid rgba(255,255,255,.35);border-top-color:#fff;border-radius:50%;animation:sp .8s linear infinite}@keyframes sp{to{transform:rotate(360deg)}}.steps3{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}@media(max-width:700px){.steps3{grid-template-columns:1fr}}.steps3 .card{margin:0}.steps3 b{display:block;color:var(--pl);font-size:24px;font-weight:800}.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:4px 0}.grow{flex:1;min-width:0}.check{display:flex;gap:10px;align-items:flex-start;margin-top:12px;cursor:pointer}.check input{width:20px;height:20px;flex:none;margin:2px 0 0;accent-color:var(--p)}.check span{flex:1}.grid2{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:600px){.grid2{grid-template-columns:1fr}}.label{font-size:12px;color:var(--t3);text-transform:uppercase;letter-spacing:.08em;font-weight:700}.big{font-size:40px;font-weight:800;letter-spacing:-1px}.bars{margin-top:8px}.bar{display:grid;grid-template-columns:28px 1fr 44px;gap:6px;align-items:center;font-size:12px;color:var(--t2)}a.bar{text-decoration:none;color:inherit}a.bar:hover i{filter:brightness(1.3)}.bar i{display:block;height:6px;border-radius:3px;background:var(--p);min-width:2px}.stats{display:flex;flex-wrap:wrap;gap:10px 26px}.stat b{display:block;font-size:24px;font-weight:800}.stat span{font-size:12px;color:var(--t3)}.pill{font-size:12px;font-weight:700;border-radius:99px;padding:4px 10px;background:#1f1a33;color:var(--pl);display:inline-block}.pill.g{color:var(--g);background:#0f2a1f}.tag{font-size:12px;color:var(--t2);background:var(--sv);border-radius:99px;padding:4px 10px;font-weight:600}.tag.own{color:#B98CFF;background:rgba(153,69,255,.14);font-style:italic}.rev{border-top:1px solid var(--line);padding:14px 0}.stars{color:var(--w);letter-spacing:1px}.txt{margin:6px 0}.reply{margin:8px 0 0 14px;padding-left:10px;border-left:2px solid var(--line);font-size:14px}.pub{color:var(--g)}.name{font-weight:800}.score{font-size:24px;font-weight:800;text-align:right}.score small{display:block;font-size:12px;font-weight:600;color:var(--t3)}.badge{width:44px;height:44px;border-radius:12px;display:grid;place-items:center;font-weight:800;color:#fff;background:linear-gradient(135deg,#9945ff,#6b2fc7);flex:none}.spark{width:100%;max-width:420px;height:auto;margin-top:6px}input,select,textarea{width:100%;background:var(--surface);border:1.5px solid var(--line);border-radius:14px;color:var(--t);padding:13px 14px;font:16px Manrope,system-ui,sans-serif;margin:6px 0 12px}input:focus,select:focus,textarea:focus{outline:none;border-color:var(--p)}.chip{background:var(--sv);color:var(--t2);min-height:44px;padding:8px 14px;font-weight:600;border-radius:99px;font-size:14px}.chip:hover{background:#221a3d;color:var(--t)}.chip[aria-pressed=true]{background:var(--p);color:#fff}.stepper{display:flex;gap:6px;list-style:none;padding:0;margin:14px 0 6px;flex-wrap:wrap}.stepper li{flex:1;min-width:0;font-size:13px;font-weight:700;color:var(--t3);border-top:3px solid var(--sv);padding-top:8px}.stepper li.on{color:var(--t);border-color:var(--p)}.stepper li.done{color:var(--g);border-color:var(--g)}.how{background:linear-gradient(135deg,rgba(153,69,255,.10),rgba(20,241,149,.04));}.how ol{margin:6px 0 0;padding-left:20px}.how li{margin:4px 0}.toasts{position:fixed;right:16px;bottom:16px;z-index:50;display:flex;flex-direction:column;gap:8px;max-width:min(420px,calc(100vw - 32px))}.toast{background:#17122a;border:1px solid var(--line);border-left:4px solid var(--p);border-radius:14px;padding:12px 14px;font-size:14px;box-shadow:0 10px 30px rgba(0,0,0,.6);animation:in .2s ease-out}.toast.ok{border-left-color:var(--g)}.toast.err{border-left-color:var(--n)}.toast.warn{border-left-color:var(--w)}@keyframes in{from{transform:translateY(8px);opacity:0}}.modal{position:fixed;inset:0;z-index:60;background:rgba(0,0,0,.7);display:grid;place-items:center;padding:16px}.modal .box{background:var(--card);border:1px solid var(--line);border-radius:20px;padding:18px;width:min(420px,100%)}.wallet{display:flex;align-items:center;gap:12px;width:100%;background:var(--sv);color:var(--t);border-radius:14px;padding:10px 12px;margin:6px 0;min-height:56px;justify-content:flex-start;font-weight:700}.wallet img{width:32px;height:32px;border-radius:8px}.wallet:hover{background:#221a3d}pre.code{white-space:pre-wrap;word-break:break-all;background:var(--surface);border:1px solid var(--line);border-radius:12px;padding:12px;font-size:12px;color:var(--t2)}code{font-size:12.5px;color:var(--t2)}.foot{border-top:1px solid var(--line);margin-top:48px;padding:22px 16px 40px}.foot .in{max-width:1000px;margin:0 auto;display:flex;gap:18px;flex-wrap:wrap;align-items:flex-start;justify-content:space-between}.foot nav{display:flex;gap:14px;flex-wrap:wrap}@media(max-width:600px){h1{font-size:27px}.hero h1{font-size:30px}.hero{min-height:420px}.hero::before{background:linear-gradient(180deg,rgba(0,0,0,.35) 0%,rgba(0,0,0,.6) 35%,rgba(0,0,0,.94) 75%)}.top .in{gap:8px;padding:8px 12px}.nav{margin-left:0;width:100%;flex-wrap:nowrap;overflow-x:auto;scrollbar-width:none}.nav::-webkit-scrollbar{display:none}.nav a{padding:6px 10px;white-space:nowrap;flex:none}.brand{font-size:17px}}"
    }
}

/** "40 min", "3 h", "2 d". */
private fun hoursText(h: Double): String = when { h < 1 -> "${maxOf(1, (h * 60).toInt())} min"; h < 48 -> "${(h + 0.5).toInt()} h"; else -> "${(h / 24 + 0.5).toInt()} d" }
