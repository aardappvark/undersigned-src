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
            path.startsWith("/app/") && path.endsWith("/feed.xml") -> dec(path.removePrefix("/app/").removeSuffix("/feed.xml")).let { pkg -> views.app(pkg)?.let { feed(pkg, it) } ?: notFound() }
            path.startsWith("/app/") -> dec(path.removePrefix("/app/")).let { pkg -> views.app(pkg)?.let { page("${it.optString("name", pkg)} — Undersigned", app(pkg, it, lang), lang = lang,
                description = "Signed reviews of ${it.optString("name", pkg)} from Seeker owners, beside the dApp Store's own rating.", head = "<link rel=alternate type='application/atom+xml' href='/app/${enc(pkg)}/feed.xml'>") } ?: notFound() }
            path.startsWith("/wallet/") -> dec(path.removePrefix("/wallet/")).let { w -> if (runCatching { Base58.decode(w).size }.getOrNull() == 32) page("Wallet ${short(w)} — Undersigned", wallet(w)) else notFound() }
            path == "/icon.svg" -> Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=86400"), ICON.toByteArray(), "image/svg+xml; charset=utf-8")
            path == "/report" -> page("Report content — Undersigned", report())
            path == "/moderation" -> page("Moderation — Undersigned", moderation())
            path == "/robots.txt" -> Api.Response(200, JSONObject(), raw = "User-agent: *\nAllow: /\nDisallow: /v1/\nSitemap: ${Config.PUBLIC_URL}/sitemap.xml\n".toByteArray(), contentType = "text/plain; charset=utf-8")
            path == "/sitemap.xml" -> Api.Response(200, JSONObject(), mapOf("Cache-Control" to "public, max-age=3600"), sitemap().toByteArray(), "application/xml; charset=utf-8")
            path == "/watch" -> page("Store watch — Undersigned", watch())
            path == "/review" -> page("Write a signed review — Undersigned", review(query["package"], lang), lang = lang)
            path == "/seed" -> page("Seed the fleet — Undersigned", seed())
            path == "/act" -> act(query["kind"].orEmpty(), query["target"].orEmpty(), lang)
            path == "/publishers" -> page("For publishers — Undersigned", publishers(query["package"]?.trim()))
            extra.containsKey(path) -> extra.getValue(path)(query)
            else -> notFound()
        }
    }

    // ---- Badge ------------------------------------------------------------------------------------------------------

    /** A shields-style SVG: "Undersigned | 4.6 · 12 signed". Cached an hour. Unknown or unreviewed apps say so plainly. */
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
        sb.append("<h1>Undersigned</h1><p class=sub>Reviews of Solana dApp Store apps, each signed on-chain by a Seeker owner's wallet. Only wallets holding a Seeker Genesis Token are counted. Not affiliated with Solana Mobile.</p>")
        sb.append("<div class=card><div class=stats>")
        listOf("reviews_counted" to "counted reviews", "attested_signers" to "attested reviewers", "apps_reviewed" to "apps reviewed", "publisher_replies" to "publisher replies", "catalogue_apps" to "apps in the store")
            .forEach { (k, l) -> sb.append("<div class=stat><b>${s.opt(k)}</b><span>$l</span></div>") }
        sb.append("</div></div>")
        sb.append("<form method=get action='/'><input name=q value='${esc(q ?: "")}' placeholder='Search apps by name or package' aria-label='Search apps'></form>")
        val apps = views.apps(q, null, if (q.isNullOrBlank()) "signed" else "name", 30).getJSONArray("apps")
        sb.append("<h2>${if (q.isNullOrBlank()) "Signed scores" else "Results"}</h2>")
        for (i in 0 until apps.length()) {
            val a = apps.getJSONObject(i); val sg = a.optJSONObject("signed")
            if (q.isNullOrBlank() && sg == null) continue
            sb.append("<a class='card row link' href='/app/${enc(a.getString("package"))}'><span class=grow><span class=name>${esc(a.getString("name"))}</span><br><span class=meta>${esc(a.optString("category", ""))} · ${esc(a.getString("package"))}</span></span>")
            sb.append(if (sg != null) "<span class=score>${"%.1f".format(java.util.Locale.US, sg.optDouble("mean"))}<small>${sg.optInt("counted_reviews")} signed</small></span>" else "<span class=meta>no signed reviews</span>").append("</a>")
        }
        sb.append("<p class=small><a href='/watch'>Store watch report</a> · <a href='/review'>Write a signed review</a> · <a href='/publishers'>For publishers</a> · <a href='/v1/dataset.json'>Open dataset</a> · <a href='/v1/openapi.json'>API</a> · <a href='/v1/pricing'>API pricing</a></p>")
        return sb.toString()
    }

    private fun app(pkg: String, a: JSONObject, lang: String): String {
        val sb = StringBuilder()
        val sg = a.optJSONObject("signed"); val store = a.optJSONObject("store"); val sr = a.optJSONObject("store_rating")
        sb.append("<p class=small><a href='/'>Undersigned</a></p><h1>${esc(a.optString("name", pkg))}</h1><p class=meta>${esc(a.optString("category", ""))} · ${esc(pkg)}</p>")
        // The two scores side by side, each labelled with where it comes from.
        sb.append("<div class='card grid2'><div><div class=label>Signed by Seeker owners</div>")
        if (sg != null && sg.optInt("counted_reviews") > 0) sb.append("<div class=big>${"%.1f".format(java.util.Locale.US, sg.optDouble("mean"))}</div><div class=meta>${plural(sg.optInt("counted_reviews"), "counted review", "counted reviews")} · ranked as ${sg.opt("rank_score")} until more Seekers sign</div>${bars(sg.optJSONArray("stars"))}")
        else sb.append("<div class=big>–</div><div class=meta>No counted signed reviews yet.</div>")
        sb.append("</div><div><div class=label>The dApp Store's own rating</div>")
        if (sr != null && sr.optInt("count") > 0) sb.append("<div class=big>${sr.opt("average")}</div><div class=meta>${sr.optInt("count")} ratings, read ${esc(sr.optString("as_of"))}</div>${bars(sr.optJSONArray("stars"))}")
        else sb.append("<div class=big>–</div><div class=meta>Not read yet.</div>")
        sb.append("</div></div>")
        sg?.optJSONObject("tags")?.takeIf { it.length() > 0 }?.let { t -> sb.append("<div class=row>"); t.keys().asSequence().sortedByDescending { t.optInt(it) }.forEach { sb.append("<span class=tag>${esc(I18n.tag(lang, it))} · ${t.optInt(it)}</span>") }; sb.append("</div>") }

        // Bursts: timing facts with a daily sparkline, the burst window shaded AND labelled (never colour alone).
        val b = views.bursts(pkg)
        val sbur = b.getJSONObject("signed_reviews").getJSONArray("bursts"); val rbur = b.getJSONObject("store_ratings").getJSONArray("bursts")
        sb.append("<h2>Bursts</h2>")
        if (sbur.length() == 0 && rbur.length() == 0) sb.append("<p class=meta>No bursts found. Signed reviews checked: ${b.getJSONObject("signed_reviews").optInt("checked")}. Days of store snapshots: ${b.getJSONObject("store_ratings").optInt("snapshot_days")} (8 needed).</p>")
        for ((label, arr) in listOf("Signed reviews" to sbur, "Store star ratings" to rbur)) for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            sb.append("<div class='card warn'><div class=label>$label · ${if (x.optString("kind") == "high") "5★" else "1★"}</div><div><b>${x.optInt("count")}</b> against about ${x.opt("expected")} usual · ×${x.opt("multiple")}</div>")
            sb.append("<div class=meta>${day(x.optLong("start"))} – ${day(x.optLong("end"))}${if (x.has("attested")) " · ${x.optInt("attested")} of ${x.optInt("of")} with an attested device" else ""}${if (!x.isNull("season")) " · during ${esc(x.optString("season"))}" else ""}</div>")
            x.optJSONObject("spark")?.let { sb.append(spark(it)) }
            sb.append("</div>")
        }
        sb.append("<p class=small>A burst is a fact about timing, not a verdict about the reviews.</p>")

        // Reviews
        val rv = views.reviews(pkg, 50, 0)
        sb.append("<h2>Signed reviews</h2>")
        val list = rv.getJSONArray("reviews")
        if (list.length() == 0) sb.append("<p class=meta>None yet. <a href='/review?package=${enc(pkg)}'>Write one</a> from a Seeker.</p>")
        // Counted reviews first. Reviews from wallets without an attested device are folded away: anyone can sign text for
        // one network fee, and an uncounted wallet's words should not sit on an app's public page by default.
        val countedHtml = StringBuilder(); val uncountedHtml = StringBuilder(); var nUncounted = 0
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i); val who = r.getJSONObject("reviewer")
            @Suppress("NAME_SHADOWING") val sb = if (who.optBoolean("attested_device")) countedHtml else uncountedHtml.also { nUncounted++ }
            sb.append("<div class=rev><div class=row><span class=stars aria-label='${r.optInt("rating")} of 5'>${"★".repeat(r.optInt("rating"))}${"☆".repeat(5 - r.optInt("rating"))}</span>")
            if (who.optBoolean("attested_device")) sb.append("<span class='pill g'>Attested device · weight ${who.opt("weight")}</span>") else sb.append("<span class=pill>Not counted: no attested device</span>")
            if (r.optBoolean("paid_for_app")) sb.append("<span class='pill g'>Paid for this app</span>")
            if (r.optBoolean("self_published")) sb.append("<span class=pill>The publisher's own review, not counted</span>")
            sb.append("</div>")
            val tags = r.optJSONArray("tags"); if (tags != null && tags.length() > 0) sb.append("<div class=row>${(0 until tags.length()).joinToString("") { "<span class=tag>${esc(I18n.tag(lang, tags.getString(it)))}</span>" }}</div>")
            if (!r.isNull("text")) sb.append("<p class=txt>${esc(r.optString("text"))}</p>")
            else if (!r.isNull("hidden_by_moderator") && r.has("hidden_by_moderator")) sb.append("<p class=meta><i>Text hidden by a moderator: ${esc(modReason(r.optString("hidden_by_moderator")))}</i> (<a href='/moderation'>policy</a>). Rating and tags still count.</p>")
            else if (r.optBoolean("hidden_by_operator")) sb.append("<p class=meta>Text not shown on this site (<a href='/report'>why</a>). The on-chain record is unchanged.</p>")
            val facts = ArrayList<String>()
            if (!r.isNull("install_age")) facts += "On their phone: ${I18n.use(lang, r.optString("install_age"))}"
            if (!r.isNull("usage_time")) facts += "Used: ${I18n.time(lang, r.optString("usage_time"))}"
            r.optJSONObject("season")?.let { facts += "During ${it.optString("label")}" }
            if (!r.isNull("similar")) facts += "Text matches other reviews"
            if (r.optInt("flaggers") > 0) facts += "Flagged by ${r.optInt("flaggers")}"
            if (r.optBoolean("hidden_by_consensus")) facts += "Hidden by community consensus"
            sb.append("<div class=meta>${day(r.optLong("time"))} · v${r.opt("version")} · <a href='/wallet/${r.getString("signer")}'>${esc(who.optString("skr_name").takeIf { !who.isNull("skr_name") } ?: short(r.getString("signer")))}</a>${if (facts.isNotEmpty()) " · " + facts.joinToString(" · ") { esc(it) } else ""}</div>")
            val reps = r.optJSONArray("replies") ?: JSONArray()
            for (k in 0 until reps.length()) { val p = reps.getJSONObject(k); sb.append("<div class=reply><span class=meta>${if (p.optBoolean("publisher")) "<b class=pub>Publisher</b> · " else ""}${short(p.getString("wallet"))} · ${day(p.optLong("time"))}</span><br>${if (!p.isNull("hidden_by_moderator") && p.has("hidden_by_moderator")) "<span class=meta><i>Text hidden by a moderator: ${esc(modReason(p.optString("hidden_by_moderator")))}</i></span>" else if (p.optBoolean("hidden_by_operator")) "<span class=meta>Text not shown on this site.</span>" else esc(p.optString("text"))}</div>") }
            sb.append("<div class=small><a href='/act?kind=up&amp;target=${r.getString("id")}'>Helpful</a> · <a href='/act?kind=reply&amp;target=${r.getString("id")}'>Reply</a> · <a href='/act?kind=flag&amp;target=${r.getString("id")}'>Flag</a></div>")
            sb.append("</div>")
        }
        sb.append(countedHtml)
        if (nUncounted > 0) sb.append("<details class=card><summary>${plural(nUncounted, "review", "reviews")} from wallets without an attested device (recorded, not counted)</summary>$uncountedHtml</details>")
        if (rv.optInt("total") > list.length()) sb.append("<p class=meta>Showing ${list.length()} of ${rv.optInt("total")}.</p>")

        // Publisher
        val pub = a.optJSONObject("publisher")
        sb.append("<h2>Publisher</h2><div class=card>")
        store?.let { sb.append("<div class=meta>The store lists: ${esc(it.optString("publisher", "–"))}${if (!it.isNull("website")) " · " + link(it.optString("website")) else ""}</div>") }
        val claims = pub?.optJSONArray("claims") ?: JSONArray()
        if (claims.length() == 0) sb.append("<div class=meta>No wallet has claimed to publish this app.</div>")
        sb.append("<div class=small><a href='/publishers'>How a publisher is proven</a></div>")
        for (k in 0 until claims.length()) { val c = claims.getJSONObject(k)
            sb.append("<div>${short(c.getString("wallet"))}: ${if (c.optBoolean("verified")) "<span class='pill g'>${if (c.optString("proof") == "store_build") "Publisher, proven by the build the dApp Store serves" else "Publisher, proven by the website the store lists" + (if (!c.isNull("proof_first_seen")) ", seen since ${day(c.optLong("proof_first_seen"))}" else "")}</span>" else "<span class=pill>Claimed, not proven yet</span>"}</div>") }
        pub?.optJSONObject("responsiveness")?.let { if (it.optInt("total") > 0) sb.append("<div class=meta>Answered ${it.optInt("answered")} of ${it.optInt("total")} signed reviews.</div>") }
        sb.append("</div>")
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
        val sb = StringBuilder("<p class=small><a href='/'>Undersigned</a></p><h1>Store watch</h1><p class=sub>Every dApp Store app whose star ratings jumped far above its usual daily pace in the last 7 days, from one reading of the store's public star counts each day. Strongest first.</p>")
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
    private fun seed(): String = "<p class=small><a href='/'>Undersigned</a></p><h1>Seed the fleet</h1>" +
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
        val tagChips = com.undersigned.protocol.TagGroups.groups.joinToString("") { (g, tags) -> "<div class=label style='margin-top:8px'>${esc(I18n.str(lang, "tag_group_" + g.name.lowercase()) ?: g.name)}</div><div class=row>" + tags.joinToString("") { t -> "<button type=button class=chip data-tag='${esc(t.wire)}' data-opp='${esc(com.undersigned.protocol.TagGroups.opposites[t]?.wire ?: "")}' aria-pressed=false>${esc(I18n.tag(lang, t.wire))}</button>" } + "</div>" }
        return """<p class=small><a href='/'>Undersigned</a></p><h1>Write a signed review</h1>
<p class=sub>Your wallet signs the review and it is recorded on Solana. It is counted in scores only if the signing wallet holds a Seeker Genesis Token. You pay only the network fee.</p>
<div class=card>
<label class=label for=pkg>App</label><input id=pkg list=apps value='${esc(pkg ?: "")}' placeholder='Package name, e.g. com.solflare.mobile' autocomplete=off>
<div class=meta id=appname>${esc(app?.name ?: "")}</div>
<div class=label style='margin-top:12px'>Rating</div><div class=row id=rating>${(1..5).joinToString("") { "<button type=button class=chip data-r=$it aria-pressed=false>$it ★</button>" }}</div>
<div class=label style='margin-top:12px'>Tags (1 to 3)</div><div id=tags>$tagChips</div>
<label class=label for=text style='display:block;margin-top:12px'>Text (optional, 140 characters)</label><textarea id=text maxlength=140 rows=3></textarea>
<label class=check><input type=checkbox id=permanent><span class=meta>I understand my text stays on-chain for good. Withdrawing the review later leaves the record.</span></label>
<label class=check><input type=checkbox id=terms><span class=meta>I accept the <a href='https://aardappvark.github.io/undersigned-legal/terms.html' target=_blank>Terms</a>. The version recorded is the store's current version of the app.</span></label>
<div class=row style='margin-top:12px'><button id=connect type=button>Connect wallet</button><button id=sign type=button disabled>Sign review</button><button id=qrbtn type=button hidden class=chip>Sign with a phone (QR)</button></div>
<div id=qr></div>
<div class=meta id=status aria-live=polite></div></div>
<style>.chip{background:#17122a;color:var(--t2);min-height:40px;padding:8px 12px;font-weight:600}.chip[aria-pressed=true]{background:var(--p);color:#fff}</style>
<script type=module>
import { getWallets } from 'https://cdn.jsdelivr.net/npm/@wallet-standard/app@1.1.0/+esm';
const $ = (id) => document.getElementById(id); const st = (t) => $('status').textContent = t;
const IDENTITY = { name: 'Undersigned', uri: location.origin, icon: '/icon.svg' };
// On Android the page talks to the wallet through the Mobile Wallet Adapter protocol directly. The Wallet Standard
// wrapper first asks Chrome's local-network permission by fetching http://localhost, which never prompts when the page
// itself is on localhost, so it waits forever there (found 29 Sep 2026). Elsewhere, Wallet Standard (extensions).
const ANDROID = /Android/i.test(navigator.userAgent) && !/Firefox\//.test(navigator.userAgent);
let rating = 0, tags = [], wallet = null, account = null, authToken = null, connecting = false;
const B58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function bs58(bytes) { let n = 0n; for (const b of bytes) n = n * 256n + BigInt(b); let s = ''; while (n > 0n) { s = B58[Number(n % 58n)] + s; n /= 58n; } for (const b of bytes) { if (b !== 0) break; s = '1' + s; } return s; }
const b64 = (bytes) => btoa(String.fromCharCode(...bytes)); const unb64 = (s) => Uint8Array.from(atob(s), c => c.charCodeAt(0));
document.querySelectorAll('#rating .chip').forEach(b => b.onclick = () => { rating = +b.dataset.r; document.querySelectorAll('#rating .chip').forEach(x => x.setAttribute('aria-pressed', x === b)); ready(); });
document.querySelectorAll('#tags .chip').forEach(b => b.onclick = () => { const t = b.dataset.tag, o = b.dataset.opp;
  if (tags.includes(t)) tags = tags.filter(x => x !== t); else { tags = tags.filter(x => x !== o); if (tags.length < 3) tags.push(t); }
  document.querySelectorAll('#tags .chip').forEach(x => x.setAttribute('aria-pressed', tags.includes(x.dataset.tag))); ready(); });
['text','permanent','terms','pkg'].forEach(id => $(id).addEventListener('input', ready));
function ready() { const text = $('text').value.trim(); $('sign').disabled = !(account && rating && tags.length && $('terms').checked && (!text || $('permanent').checked) && $('pkg').value.trim()); }
async function mwa() { return (await import('https://cdn.jsdelivr.net/npm/@solana-mobile/mobile-wallet-adapter-protocol@2.3.0/+esm')).transact; }
function explain(e) { const c = e && e.code; return c === 'ERROR_WALLET_NOT_FOUND' ? 'No wallet app opened. Install a Solana wallet that supports Mobile Wallet Adapter (the Seeker Wallet does) and try again.'
  : c === 'ERROR_ASSOCIATION_CANCELLED' ? 'Connection cancelled or timed out. Tap the button again.' : (c ? c + ' — ' : '') + (e && e.message || e); }
$('connect').onclick = async () => {
  if (connecting) return; connecting = true; $('connect').disabled = true;
  try {
    if (ANDROID) {
      st('Opening your wallet…');
      const transact = await mwa();
      const auth = await transact(async (w) => w.authorize({ chain: 'solana:mainnet', identity: IDENTITY }));
      authToken = auth.auth_token; const acc = auth.accounts && auth.accounts[0];
      if (!acc) { st('The wallet returned no account.'); return; }
      account = { address: bs58(unb64(acc.address)), label: acc.label };
    } else {
      const ws = getWallets().get().filter(w => w.chains.some(c => c.startsWith('solana:')) && w.features['standard:connect'] && w.features['solana:signAndSendTransaction']);
      if (!ws.length) { st('No Solana wallet found in this browser. On a Seeker, open this page in Chrome; on a computer, install a Solana wallet extension.'); return; }
      const chosen = ws.length === 1 ? ws[0] : ws[Math.max(0, +prompt(ws.map((w, i) => i + ': ' + w.name).join('\n'), '0') || 0)];
      if (chosen !== wallet) { account = null; ready(); }          // a stale account must never be sent to a different wallet
      wallet = chosen;
      if (wallet.features['standard:events']) wallet.features['standard:events'].on('change', (p) => { if (p.accounts) { account = p.accounts[0] || null; st(account ? 'Account: ' + account.address.slice(0, 4) + '…' + account.address.slice(-4) : 'The wallet disconnected.'); ready(); } });
      st('Opening ' + wallet.name + '…');
      const r = await wallet.features['standard:connect'].connect(); account = r.accounts && r.accounts[0];
      if (!account) { st('The wallet returned no account. Reload this page and try again.'); return; }
    }
    st('Connected: ' + (account.label ? account.label + ' · ' : '') + account.address.slice(0, 4) + '…' + account.address.slice(-4)); ready();
  } catch (e) { account = null; ready(); st('The wallet did not connect: ' + explain(e)); console.warn('connect failed', e && e.code, e && e.message); }
  finally { connecting = false; $('connect').disabled = false; }
};
if (!ANDROID && location.protocol === 'https:') { $('qrbtn').hidden = false; $('qrbtn').onclick = async () => {
  const text = $('text').value.trim();
  if (!(rating && tags.length && $('terms').checked && (!text || $('permanent').checked) && $('pkg').value.trim())) { st('Choose a rating and tags and tick the boxes first.'); return; }
  const u = location.origin + '/v1/pay/review?package=' + encodeURIComponent($('pkg').value.trim()) + '&rating=' + rating + '&tags=' + encodeURIComponent(tags.join(',')) + (text ? '&text=' + encodeURIComponent(text) : '');
  const QR = (await import('https://cdn.jsdelivr.net/npm/qrcode@1.5.4/+esm')).default;
  const img = await QR.toDataURL('solana:' + encodeURIComponent(u), { margin: 1, width: 280 });
  $('qr').innerHTML = '<img alt="QR code for a phone wallet" width=280 height=280 src="' + img + '"><div class=meta>Scan with the wallet on your Seeker. It shows the transaction and asks you to sign.</div>';
}; }
$('sign').onclick = async () => {
  $('sign').disabled = true; st('Building the transaction…');
  try {
    const r = await fetch('/v1/tx/review', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ account: account.address, package: $('pkg').value.trim(), rating, tags, text: $('text').value.trim() }) });
    const j = await r.json(); if (!r.ok) { st(j.message || j.error); ready(); return; }
    st((j.counted_now === false ? 'Note: this server has read no Seeker Genesis Token on this wallet, so the review will be recorded but not counted. ' : '') + 'Sign in your wallet…');
    let sig;
    if (ANDROID) {
      const transact = await mwa();
      const res = await transact(async (w) => { await w.authorize({ chain: 'solana:mainnet', identity: IDENTITY, auth_token: authToken }); return w.signAndSendTransactions({ payloads: [j.transaction] }); });
      sig = bs58(unb64(res.signatures[0]));
    } else {
      const [res] = await wallet.features['solana:signAndSendTransaction'].signAndSendTransaction({ account, transaction: unb64(j.transaction), chain: 'solana:mainnet' });
      sig = bs58(res.signature);
    }
    $('status').innerHTML = 'Signed and sent. <a target=_blank href="https://solscan.io/tx/' + sig + '">View the transaction</a>. It appears here within a few minutes.';
  } catch (e) { st('Not signed: ' + explain(e)); ready(); }
};
</script>"""
    }

    /** What this site can and cannot do about content, and who to write to. */
    private fun report(): String = """<p class=small><a href='/'>Undersigned</a></p><h1>Report content</h1>
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
        return "<p class=small><a href='/'>Undersigned</a></p><h1>Moderation</h1>" +
            "<p>Moderation on Undersigned hides <b>text</b>, never a voice. A moderator can stop a review's or reply's words from being shown; its rating, tags and weight still count, and the record stays on Solana, where nobody can change or delete it.</p>" +
            "<div class=card><div class=label>Who moderates</div><p>The wallets named in the app's and this site's source code (<code>Moderation.MODERATORS</code>). Today that is one wallet, the operator's: <code>${esc(who)}</code>. Adding or removing one is a new release, visible to anyone.</p></div>" +
            "<div class=card><div class=label>The only four reasons</div><p>Illegal content · someone's personal data (names, addresses, phone numbers, keys or seed words) · abuse or harassment aimed at a person · impersonating a publisher.</p>" +
            "<p class=meta>Not reasons: a low rating, a harsh or negative opinion, criticism of an app or its publisher, or a review someone disagrees with. Spam and off-topic records are for community flags, which any Seeker owner can sign.</p></div>" +
            "<div class=card><div class=label>How it works</div><p>The moderator signs a public record naming the record and the reason. Every reader, the app and this site, then shows \"Text hidden by a moderator\" and the reason in place of the text. The same moderator can undo it with a second record; the later one counts. A moderator's record on a review of an app that moderator is the proven publisher of is ignored.</p></div>" +
            "<div class=card><div class=label>If your text was hidden</div><p>Sign a dispute from the app (it is shown beside your record), or write to $contact with the record id. A moderator looks again within 7 days and says what was decided.</p></div>" +
            "<h2>Every moderation record</h2>$list"
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
        val body = """<p class=small><a href='/'>Undersigned</a></p><h1>$title</h1>$about
<div class=card>$inputs
<label class=check><input type=checkbox id=terms><span class=meta>I accept the <a href='https://aardappvark.github.io/undersigned-legal/terms.html' target=_blank>Terms</a>. You pay only the network fee.</span></label>
<div class=row style='margin-top:12px'><button id=go type=button>Sign with your wallet</button></div><div class=meta id=status aria-live=polite></div></div>
<script type=module>
import { getWallets } from 'https://cdn.jsdelivr.net/npm/@wallet-standard/app@1.1.0/+esm';
$JS_HELPERS
const KIND = '${esc(kind)}', TARGET = '${esc(target)}';
$('go').onclick = async () => {
  if (!$('terms').checked) { st('Tick the Terms box first.'); return; }
  if (KIND === 'reply' && (!$('text').value.trim() || !$('permanent').checked)) { st('Write the reply and tick the box about permanence.'); return; }
  const build = async (address) => { const r = await fetch('/v1/tx/record', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ account: address, kind: KIND, target: TARGET, text: $('text') ? $('text').value.trim() : undefined, reason: $('reason') ? $('reason').value : undefined }) });
    const j = await r.json(); if (!r.ok) throw new Error(j.message || j.error); return j.transaction; };
  $('go').disabled = true;
  try {
    let sig;
    if (ANDROID) {
      st('Opening your wallet…'); const transact = await mwa();
      const res = await transact(async (w) => { const auth = await w.authorize({ chain: 'solana:mainnet', identity: IDENTITY }); const tx = await build(bs58(unb64(auth.accounts[0].address))); return w.signAndSendTransactions({ payloads: [tx] }); });
      sig = bs58(unb64(res.signatures[0]));
    } else {
      const ws = getWallets().get().filter(w => w.chains.some(c => c.startsWith('solana:')) && w.features['standard:connect'] && w.features['solana:signAndSendTransaction']);
      if (!ws.length) { st('No Solana wallet found in this browser. On a Seeker, open this page in Chrome; on a computer, install a Solana wallet extension.'); return; }
      const wallet = ws[0]; st('Opening ' + wallet.name + '…');
      const r = await wallet.features['standard:connect'].connect(); const account = r.accounts && r.accounts[0];
      if (!account) { st('The wallet returned no account. Reload this page and try again.'); return; }
      const [res] = await wallet.features['solana:signAndSendTransaction'].signAndSendTransaction({ account, transaction: unb64(await build(account.address)), chain: 'solana:mainnet' });
      sig = bs58(res.signature);
    }
    $('status').innerHTML = 'Signed and sent. <a target=_blank href="https://solscan.io/tx/' + sig + '">View the transaction</a>.';
  } catch (e) { st('Not signed: ' + explain(e)); } finally { $('go').disabled = false; }
};
</script>"""
        return page("$title — Undersigned", body, lang = lang)
    }

    /** How a publisher proves an app is theirs, and what this server currently reads for one package (from its cache only). */
    private fun publishers(pkg: String?): String {
        val sb = StringBuilder("""<p class=small><a href='/'>Undersigned</a></p><h1>For publishers</h1>
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
        val sb = StringBuilder("<p class=small><a href='/'>Undersigned</a></p><h1>${esc(if (j.isNull("skr_name")) short(w) else j.getString("skr_name"))}</h1><p class=meta><code>${esc(w)}</code></p><div class=card><div class=stats>")
        if (j.isNull("weight") && j.optInt("reviews") == 0 && j.isNull("standing")) {
            sb.append("<div class=stat><b>Not seen yet</b><span>this wallet has signed nothing on the ledger, or the index has not read it yet</span></div></div></div>")
            return sb.toString()
        }
        sb.append("<div class=stat><b>${if (j.optBoolean("attested_device")) "Yes" else "No"}</b><span>attested device</span></div><div class=stat><b>${j.opt("weight")}×</b><span>how much each review counts</span></div><div class=stat><b>${j.optInt("reviews")}</b><span>live reviews</span></div>")
        sb.append("</div></div>")
        val pubs = j.optJSONArray("publisher_of") ?: JSONArray()
        if (pubs.length() > 0) sb.append("<p>Proven publisher of: ${(0 until pubs.length()).joinToString(", ") { "<a href='/app/${enc(pubs.getString(it))}'>${esc(pubs.getString(it))}</a>" }}</p>")
        val recs = views.records("rev", null, w, null, null, null, 50).getJSONArray("records")
        sb.append("<h2>Signed reviews</h2>")
        for (i in 0 until recs.length()) { val r = recs.getJSONObject(i)
            sb.append("<div class=rev><a href='/app/${enc(r.optString("package"))}'>${esc(indexer.state.byPackage[r.optString("package")]?.name ?: r.optString("package"))}</a> <span class=stars>${"★".repeat(r.optInt("rating"))}</span>${if (r.optBoolean("withdrawn")) " <span class=pill>withdrawn</span>" else ""}<div class=meta>${day(r.optLong("time"))}</div></div>") }
        return sb.toString()
    }

    // ---- Pieces -------------------------------------------------------------------------------------------------------

    private fun modReason(wire: String) = when (wire) { "illegal" -> "illegal content"; "personal" -> "someone's personal data"; "abuse" -> "abuse or harassment"; "impersonation" -> "impersonates a publisher"; else -> wire }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    /** Cuts long store text at a word boundary and marks the cut. */
    private fun clip(t: String, max: Int): String = if (t.length <= max) t else t.take(max).substringBeforeLast(' ', t.take(max)).trimEnd(',', '.', ';', ':') + "…"

    /** An http(s) URL as a link that leaves no referrer; anything else as plain text. */
    private fun link(u: String): String = if (u.startsWith("https://") || u.startsWith("http://")) "<a href='${esc(u)}' rel='nofollow noopener noreferrer'>${esc(u.substringAfter("://").trimEnd('/'))}</a>" else esc(u)

    private fun bars(stars: JSONArray?): String {
        stars ?: return ""; val max = (0 until 5).maxOf { stars.optInt(it) }.coerceAtLeast(1)
        return "<div class=bars>" + (5 downTo 1).joinToString("") { s -> val n = stars.optInt(s - 1)
            "<div class=bar><span>$s★</span><i style='width:${(100.0 * n / max).toInt()}%'></i><span>$n</span></div>" } + "</div>"
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
        val html = "<!doctype html><html lang=${I18n.htmlLang(lang)}><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>${esc(title)}</title><link rel=icon href='/icon.svg'><meta name=description content='${esc(description)}'><meta property='og:title' content='${esc(title)}'><meta property='og:description' content='${esc(description)}'><meta property='og:image' content='${Config.PUBLIC_URL}/icon.svg'><meta name='twitter:card' content='summary'><style nonce=\"$nonce\">$CSS</style>$head</head><body><main>$withNonce<footer class=small>Undersigned is an open review ledger on Solana. Reviews are the opinions of individual Seeker owners. <a href='https://aardappvark.github.io/undersigned-legal/'>Terms and privacy</a> · <a href='/report'>Report content</a>.</footer></main></body></html>"
        val csp = "default-src 'none'; script-src 'nonce-$nonce' https://cdn.jsdelivr.net; style-src 'unsafe-inline'; img-src 'self' data: https:; " +
            "connect-src 'self' https://cdn.jsdelivr.net ws://localhost:* wss://localhost:* ws://127.0.0.1:* wss://127.0.0.1:*; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
        return Api.Response(status, JSONObject(), mapOf("Cache-Control" to "public, max-age=60", "Content-Security-Policy" to csp, "Referrer-Policy" to "no-referrer"), html.toByteArray(), "text/html; charset=utf-8")
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
        /** Wallet helpers shared by the action pages: base58, base64, the direct Mobile Wallet Adapter import, and plain error text. */
        const val JS_HELPERS = """const $ = (id) => document.getElementById(id); const st = (t) => $('status').textContent = t;
const IDENTITY = { name: 'Undersigned', uri: location.origin, icon: '/icon.svg' };
const ANDROID = /Android/i.test(navigator.userAgent) && !/Firefox\//.test(navigator.userAgent);
const B58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function bs58(bytes) { let n = 0n; for (const b of bytes) n = n * 256n + BigInt(b); let s = ''; while (n > 0n) { s = B58[Number(n % 58n)] + s; n /= 58n; } for (const b of bytes) { if (b !== 0) break; s = '1' + s; } return s; }
const unb64 = (s) => Uint8Array.from(atob(s), c => c.charCodeAt(0));
async function mwa() { return (await import('https://cdn.jsdelivr.net/npm/@solana-mobile/mobile-wallet-adapter-protocol@2.3.0/+esm')).transact; }
function explain(e) { const c = e && e.code; return c === 'ERROR_WALLET_NOT_FOUND' ? 'No wallet app opened. Install a Solana wallet that supports Mobile Wallet Adapter (the Seeker Wallet does) and try again.'
  : c === 'ERROR_ASSOCIATION_CANCELLED' ? 'Connection cancelled or timed out. Tap the button again.' : (c ? c + ' — ' : '') + (e && e.message || e); }"""
        const val CSS = ":root{--bg:#000;--card:#120e22;--line:#2a2145;--p:#9945ff;--pl:#b98cff;--g:#14f195;--t:#f2eeff;--t2:#b7aed1;--t3:#8e85a8;--w:#ffc46b}" +
            "body{margin:0;background:var(--bg);color:var(--t);font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}main{max-width:820px;margin:0 auto;padding:20px 16px 48px}" +
            "h1{font-size:26px;margin:4px 0}h2{font-size:17px;margin:26px 0 8px}.sub,.meta{color:var(--t2)}.meta,.small{font-size:13px}.small{color:var(--t3)}a{color:var(--pl)}" +
            ".card{display:block;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:14px 16px;margin:10px 0;text-decoration:none;color:inherit}.card.warn{border-color:#5a4524}" +
            ".check{display:flex;gap:10px;align-items:flex-start;margin-top:12px;cursor:pointer}.check input{width:20px;height:20px;flex:none;margin:2px 0 0}.check span{flex:1}" + ".row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:4px 0}.grow{flex:1 1 auto;min-width:0}.name{font-weight:700}.score{font-size:22px;font-weight:700;text-align:right}.score small{display:block;font-size:11px;color:var(--t3);font-weight:400}" +
            ".grid2{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:560px){.grid2{grid-template-columns:1fr}}.label{font-size:12px;color:var(--t3);text-transform:uppercase;letter-spacing:.06em}.big{font-size:34px;font-weight:700}" +
            ".bars{margin-top:8px}.bar{display:grid;grid-template-columns:28px 1fr 44px;gap:6px;align-items:center;font-size:12px;color:var(--t2)}.bar i{display:block;height:6px;border-radius:3px;background:var(--p);min-width:2px}" +
            ".stats{display:flex;flex-wrap:wrap;gap:8px 22px}.stat b{display:block;font-size:20px}.stat span{font-size:11px;color:var(--t3)}" +
            ".pill{font-size:11px;font-weight:700;border-radius:99px;padding:3px 9px;background:#1f1a33;color:var(--pl)}.pill.g{color:var(--g);background:#0f2a1f}.tag{font-size:12px;color:var(--t2);background:#17122a;border-radius:99px;padding:3px 9px}" +
            ".rev{border-top:1px solid var(--line);padding:12px 0}.stars{color:var(--w);letter-spacing:1px}.txt{margin:6px 0}.reply{margin:8px 0 0 14px;padding-left:10px;border-left:2px solid var(--line);font-size:14px}.pub{color:var(--g)}" +
            ".spark{width:100%;max-width:420px;height:auto;margin-top:6px}input,select,textarea{width:100%;box-sizing:border-box;background:#0e0b18;border:1px solid var(--line);border-radius:12px;color:var(--t);padding:12px;font-size:16px;margin:6px 0 12px}" +
            "button{background:var(--p);color:#fff;border:0;border-radius:12px;padding:12px 18px;font-size:15px;font-weight:700;min-height:48px;cursor:pointer}button[disabled]{opacity:.5}" +
            "pre.code{white-space:pre-wrap;word-break:break-all;background:#0e0b18;border:1px solid var(--line);border-radius:12px;padding:10px;font-size:12px;color:var(--t2)}code{font-size:12px;color:var(--t2)}" +
            "a.link:hover{border-color:var(--p)}footer{margin-top:40px;border-top:1px solid var(--line);padding-top:12px}"
    }
}
