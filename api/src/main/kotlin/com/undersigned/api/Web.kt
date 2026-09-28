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

    fun handle(path: String, query: Map<String, String>): Api.Response = try {
        handle0(path, query)
    } catch (e: IllegalArgumentException) { notFound() }   // a bad percent-escape in a path is a 404, not a failure
    catch (e: Exception) { log("web $path: ${e.message?.take(100)}"); Api.Response(500, JSONObject(), raw = "Something went wrong.".toByteArray(), contentType = "text/plain; charset=utf-8") }

    private fun handle0(path: String, query: Map<String, String>): Api.Response = run {
        when {
            path == "/" -> page("Undersigned — signed reviews from Seeker owners", home(query["q"]))
            path.startsWith("/v1/badge/") && path.endsWith(".svg") -> badge(dec(path.removePrefix("/v1/badge/").removeSuffix(".svg")))
            path.startsWith("/app/") -> dec(path.removePrefix("/app/")).let { pkg -> views.app(pkg)?.let { page("${it.optString("name", pkg)} — Undersigned", app(pkg, it)) } ?: notFound() }
            path.startsWith("/wallet/") -> dec(path.removePrefix("/wallet/")).let { w -> if (runCatching { Base58.decode(w).size }.getOrNull() == 32) page("Wallet ${short(w)} — Undersigned", wallet(w)) else notFound() }
            path == "/watch" -> page("Store watch — Undersigned", watch())
            path == "/review" -> page("Write a signed review — Undersigned", review(query["package"]))
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
        sb.append("<p class=small><a href='/watch'>Store watch report</a> · <a href='/review'>Write a signed review</a> · <a href='/v1/openapi.json'>API</a> · <a href='/v1/pricing'>API pricing</a></p>")
        return sb.toString()
    }

    private fun app(pkg: String, a: JSONObject): String {
        val sb = StringBuilder()
        val sg = a.optJSONObject("signed"); val store = a.optJSONObject("store"); val sr = a.optJSONObject("store_rating")
        sb.append("<p class=small><a href='/'>Undersigned</a></p><h1>${esc(a.optString("name", pkg))}</h1><p class=meta>${esc(a.optString("category", ""))} · ${esc(pkg)}</p>")
        // The two scores side by side, each labelled with where it comes from.
        sb.append("<div class='card grid2'><div><div class=label>Signed by Seeker owners</div>")
        if (sg != null && sg.optInt("counted_reviews") > 0) sb.append("<div class=big>${"%.1f".format(java.util.Locale.US, sg.optDouble("mean"))}</div><div class=meta>${sg.optInt("counted_reviews")} counted review(s) · rank score ${sg.opt("rank_score")}</div>${bars(sg.optJSONArray("stars"))}")
        else sb.append("<div class=big>–</div><div class=meta>No counted signed reviews yet.</div>")
        sb.append("</div><div><div class=label>The dApp Store's own rating</div>")
        if (sr != null && sr.optInt("count") > 0) sb.append("<div class=big>${sr.opt("average")}</div><div class=meta>${sr.optInt("count")} ratings, read ${esc(sr.optString("as_of"))}</div>${bars(sr.optJSONArray("stars"))}")
        else sb.append("<div class=big>–</div><div class=meta>Not read yet.</div>")
        sb.append("</div></div>")
        sg?.optJSONObject("tags")?.takeIf { it.length() > 0 }?.let { t -> sb.append("<div class=row>"); t.keys().asSequence().sortedByDescending { t.optInt(it) }.forEach { sb.append("<span class=tag>${esc(TAGS[it] ?: it)} · ${t.optInt(it)}</span>") }; sb.append("</div>") }

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
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i); val who = r.getJSONObject("reviewer")
            sb.append("<div class=rev><div class=row><span class=stars aria-label='${r.optInt("rating")} of 5'>${"★".repeat(r.optInt("rating"))}${"☆".repeat(5 - r.optInt("rating"))}</span>")
            if (who.optBoolean("attested_device")) sb.append("<span class='pill g'>Attested device · weight ${who.opt("weight")}</span>") else sb.append("<span class=pill>Not counted: no attested device</span>")
            if (r.optBoolean("paid_for_app")) sb.append("<span class='pill g'>Paid for this app</span>")
            if (r.optBoolean("self_published")) sb.append("<span class=pill>The publisher's own review, not counted</span>")
            sb.append("</div>")
            val tags = r.optJSONArray("tags"); if (tags != null && tags.length() > 0) sb.append("<div class=row>${(0 until tags.length()).joinToString("") { "<span class=tag>${esc(TAGS[tags.getString(it)] ?: tags.getString(it))}</span>" }}</div>")
            if (!r.isNull("text")) sb.append("<p class=txt>${esc(r.optString("text"))}</p>")
            val facts = ArrayList<String>()
            if (!r.isNull("install_age")) facts += "On their phone: ${USE[r.optString("install_age")] ?: r.optString("install_age")}"
            if (!r.isNull("usage_time")) facts += "Used: ${TIM[r.optString("usage_time")] ?: r.optString("usage_time")}"
            r.optJSONObject("season")?.let { facts += "During ${it.optString("label")}" }
            if (!r.isNull("similar")) facts += "Text matches other reviews"
            if (r.optInt("flaggers") > 0) facts += "Flagged by ${r.optInt("flaggers")}"
            if (r.optBoolean("hidden_by_consensus")) facts += "Hidden by community consensus"
            sb.append("<div class=meta>${day(r.optLong("time"))} · v${r.opt("version")} · <a href='/wallet/${r.getString("signer")}'>${esc(who.optString("skr_name").takeIf { !who.isNull("skr_name") } ?: short(r.getString("signer")))}</a>${if (facts.isNotEmpty()) " · " + facts.joinToString(" · ") { esc(it) } else ""}</div>")
            val reps = r.optJSONArray("replies") ?: JSONArray()
            for (k in 0 until reps.length()) { val p = reps.getJSONObject(k); sb.append("<div class=reply><span class=meta>${if (p.optBoolean("publisher")) "<b class=pub>Publisher</b> · " else ""}${short(p.getString("wallet"))} · ${day(p.optLong("time"))}</span><br>${esc(p.optString("text"))}</div>") }
            sb.append("</div>")
        }
        if (rv.optInt("total") > list.length()) sb.append("<p class=meta>Showing ${list.length()} of ${rv.optInt("total")}.</p>")

        // Publisher
        val pub = a.optJSONObject("publisher")
        sb.append("<h2>Publisher</h2><div class=card>")
        store?.let { sb.append("<div class=meta>The store lists: ${esc(it.optString("publisher", "–"))}${if (!it.isNull("website")) " · " + esc(it.optString("website")) else ""}</div>") }
        val claims = pub?.optJSONArray("claims") ?: JSONArray()
        if (claims.length() == 0) sb.append("<div class=meta>No wallet has claimed to publish this app.</div>")
        for (k in 0 until claims.length()) { val c = claims.getJSONObject(k)
            sb.append("<div>${short(c.getString("wallet"))}: ${if (c.optBoolean("verified")) "<span class='pill g'>${if (c.optString("proof") == "store_build") "Named in the store's own signed build" else "Proven through the store-listed website" + (if (!c.isNull("proof_first_seen")) ", seen since ${day(c.optLong("proof_first_seen"))}" else "")}</span>" else "<span class=pill>Claimed, not proven</span>"}</div>") }
        pub?.optJSONObject("responsiveness")?.let { if (it.optInt("total") > 0) sb.append("<div class=meta>Answered ${it.optInt("answered")} of ${it.optInt("total")} signed reviews.</div>") }
        sb.append("</div>")
        a.optJSONObject("payments")?.let { p -> sb.append("<h2>Payments received on-chain</h2><div class=card><div class=stats><div class=stat><b>${if (p.isNull("usd_when_paid")) "–" else "$" + p.opt("usd_when_paid")}</b><span>USD on the day of each payment</span></div><div class=stat><b>${p.optInt("payments")}</b><span>payments</span></div><div class=stat><b>${p.optInt("payers")}</b><span>payers</span></div><div class=stat><b>${p.optInt("attested_payers")}</b><span>payers with an attested device</span></div></div><p class=small>Publisher-confirmed treasury only. Off-chain income is invisible to this; nothing here is a statement about profit.</p></div>") }
        store?.let { sb.append("<h2>From the store</h2><div class=card><div class=meta>Version ${it.opt("version")} · signing certificate SHA-256 <code>${esc(it.optString("certificate_sha256", "–").take(16))}…</code></div>${if (!it.isNull("whats_new")) "<p class=txt>${esc(it.optString("whats_new").take(400))}</p>" else ""}</div>") }

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
    private fun review(pkg: String?): String {
        val app = pkg?.let { indexer.state.byPackage[it] }
        val tagChips = TAGS.entries.joinToString("") { (w, l) -> "<button type=button class=chip data-tag='${esc(w)}' aria-pressed=false>${esc(l)}</button>" }
        return """<p class=small><a href='/'>Undersigned</a></p><h1>Write a signed review</h1>
<p class=sub>Your wallet signs the review and it is recorded on Solana. It is counted in scores only if the signing wallet holds a Seeker Genesis Token. You pay only the network fee.</p>
<div class=card>
<label class=label for=pkg>App</label><input id=pkg list=apps value='${esc(pkg ?: "")}' placeholder='Package name, e.g. com.solflare.mobile' autocomplete=off>
<div class=meta id=appname>${esc(app?.name ?: "")}</div>
<div class=label style='margin-top:12px'>Rating</div><div class=row id=rating>${(1..5).joinToString("") { "<button type=button class=chip data-r=$it aria-pressed=false>$it ★</button>" }}</div>
<div class=label style='margin-top:12px'>Tags (1 to 3)</div><div class=row id=tags>$tagChips</div>
<label class=label for=text style='display:block;margin-top:12px'>Text (optional, 140 characters)</label><textarea id=text maxlength=140 rows=3></textarea>
<label class=row><input type=checkbox id=permanent style='width:auto;margin:0'> <span class=meta>I understand my text stays on-chain for good. Withdrawing the review later leaves the record.</span></label>
<label class=row><input type=checkbox id=terms style='width:auto;margin:0'> <span class=meta>I accept the <a href='https://aardappvark.github.io/undersigned-legal/terms.html' target=_blank>Terms</a>. The version recorded is the store's current version of the app.</span></label>
<div class=row style='margin-top:12px'><button id=connect type=button>Connect wallet</button><button id=sign type=button disabled>Sign review</button></div>
<div class=meta id=status aria-live=polite></div></div>
<style>.chip{background:#17122a;color:var(--t2);min-height:40px;padding:8px 12px;font-weight:600}.chip[aria-pressed=true]{background:var(--p);color:#fff}</style>
<script type=module>
import { getWallets } from 'https://cdn.jsdelivr.net/npm/@wallet-standard/app@1.1.0/+esm';
const $ = (id) => document.getElementById(id); const st = (t) => $('status').textContent = t;
const IDENTITY = { name: 'Undersigned', uri: location.origin, icon: '/v1/badge/icon.svg' };
// On Android the page talks to the wallet through the Mobile Wallet Adapter protocol directly. The Wallet Standard
// wrapper first asks Chrome's local-network permission by fetching http://localhost, which never prompts when the page
// itself is on localhost, so it waits forever there (found 29 Sep 2026). Elsewhere, Wallet Standard (extensions).
const ANDROID = /Android/i.test(navigator.userAgent) && !/Firefox\//.test(navigator.userAgent);
let rating = 0, tags = [], wallet = null, account = null, authToken = null, connecting = false;
const B58 = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
function bs58(bytes) { let n = 0n; for (const b of bytes) n = n * 256n + BigInt(b); let s = ''; while (n > 0n) { s = B58[Number(n % 58n)] + s; n /= 58n; } for (const b of bytes) { if (b !== 0) break; s = '1' + s; } return s; }
const b64 = (bytes) => btoa(String.fromCharCode(...bytes)); const unb64 = (s) => Uint8Array.from(atob(s), c => c.charCodeAt(0));
document.querySelectorAll('#rating .chip').forEach(b => b.onclick = () => { rating = +b.dataset.r; document.querySelectorAll('#rating .chip').forEach(x => x.setAttribute('aria-pressed', x === b)); ready(); });
document.querySelectorAll('#tags .chip').forEach(b => b.onclick = () => { const t = b.dataset.tag; if (tags.includes(t)) tags = tags.filter(x => x !== t); else if (tags.length < 3) tags.push(t); b.setAttribute('aria-pressed', tags.includes(t)); ready(); });
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
$('sign').onclick = async () => {
  $('sign').disabled = true; st('Building the transaction…');
  try {
    const r = await fetch('/v1/tx/review', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ account: account.address, package: $('pkg').value.trim(), rating, tags, text: $('text').value.trim() }) });
    const j = await r.json(); if (!r.ok) { st(j.message || j.error); ready(); return; }
    st('Sign in your wallet…');
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

    private fun wallet(w: String): String {
        val j = views.wallet(w, false)
        val sb = StringBuilder("<p class=small><a href='/'>Undersigned</a></p><h1>${esc(if (j.isNull("skr_name")) short(w) else j.getString("skr_name"))}</h1><p class=meta><code>${esc(w)}</code></p><div class=card><div class=stats>")
        sb.append("<div class=stat><b>${if (j.optBoolean("attested_device")) "Yes" else "No"}</b><span>attested device</span></div><div class=stat><b>${j.opt("weight")}</b><span>review weight</span></div><div class=stat><b>${j.optInt("reviews")}</b><span>live reviews</span></div>")
        j.optJSONObject("standing")?.let { sb.append("<div class=stat><b>${it.optInt("credibility")}</b><span>credibility (published formula)</span></div>") }
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
    fun page(title: String, body: String, status: Int = 200, head: String = ""): Api.Response {
        val nonce = Base58.encode(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })
        val withNonce = body.replace("<script type=module>", "<script type=module nonce=\"$nonce\">")
        val html = "<!doctype html><html lang=en><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>${esc(title)}</title><style nonce=\"$nonce\">$CSS</style>$head</head><body><main>$withNonce<footer class=small>Undersigned is an open review ledger on Solana. Reviews are the opinions of individual Seeker owners. <a href='https://aardappvark.github.io/undersigned-legal/'>Terms and privacy</a>.</footer></main></body></html>"
        val csp = "default-src 'none'; script-src 'nonce-$nonce' https://cdn.jsdelivr.net; style-src 'nonce-$nonce' 'unsafe-inline'; img-src 'self' data: https:; " +
            "connect-src 'self' https://cdn.jsdelivr.net ws://localhost:* wss://localhost:* ws://127.0.0.1:* wss://127.0.0.1:*; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
        return Api.Response(status, JSONObject(), mapOf("Cache-Control" to "public, max-age=60", "Content-Security-Policy" to csp, "Referrer-Policy" to "no-referrer"), html.toByteArray(), "text/html; charset=utf-8")
    }

    private fun notFound() = page("Not found — Undersigned", "<h1>Not found</h1><p><a href='/'>Back to Undersigned</a></p>", 404)

    companion object {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun dec(s: String) = java.net.URLDecoder.decode(s, "UTF-8")
        fun short(w: String) = if (w.length > 10) w.take(4) + "…" + w.takeLast(4) else w
        fun day(t: Long) = java.time.Instant.ofEpochSecond(t).toString().take(10)
        /** English labels for the tag wire values (the app's own strings; the app localises them in 28 languages). */
        val TAGS = mapOf("works" to "Works", "crashes" to "Crashes", "wallet-ok" to "Wallet ok", "wallet-fails" to "Wallet fails", "fast" to "Fast", "slow" to "Slow",
            "battery" to "Battery", "ads" to "Ads", "paywall" to "Paywall", "fair-price" to "Fair price", "overpriced" to "Overpriced", "perms-ok" to "Perms ok",
            "perms-excess" to "Perms excess", "spam-reviews" to "Unnatural reviews", "misleading" to "Misleading", "suspected-scam" to "Suspected scam",
            "abandoned" to "Abandoned", "active-dev" to "Active dev", "onboarding-ok" to "Onboarding ok", "confusing" to "Confusing", "offline-ok" to "Offline ok",
            "needs-account" to "Needs account", "seeker-native" to "Seeker native", "webview" to "Webview")
        val USE = mapOf("d0" to "under a day", "d1" to "1–6 days", "w1" to "1–4 weeks", "m1" to "1–3 months", "m3" to "3+ months")
        val TIM = mapOf("t0" to "under 10 minutes", "t1" to "10–60 minutes", "t2" to "1–5 hours", "t3" to "5–20 hours", "t4" to "20+ hours")
        const val CSS = ":root{--bg:#000;--card:#120e22;--line:#2a2145;--p:#9945ff;--pl:#b98cff;--g:#14f195;--t:#f2eeff;--t2:#b7aed1;--t3:#8e85a8;--w:#ffc46b}" +
            "body{margin:0;background:var(--bg);color:var(--t);font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}main{max-width:820px;margin:0 auto;padding:20px 16px 48px}" +
            "h1{font-size:26px;margin:4px 0}h2{font-size:17px;margin:26px 0 8px}.sub,.meta{color:var(--t2)}.meta,.small{font-size:13px}.small{color:var(--t3)}a{color:var(--pl)}" +
            ".card{display:block;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:14px 16px;margin:10px 0;text-decoration:none;color:inherit}.card.warn{border-color:#5a4524}" +
            ".row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:4px 0}.grow{flex:1 1 auto;min-width:0}.name{font-weight:700}.score{font-size:22px;font-weight:700;text-align:right}.score small{display:block;font-size:11px;color:var(--t3);font-weight:400}" +
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
