// Undersigned API tools (docs/API.md): the server-side index — scores, bursts, store watch, payments, rankings, AI
// reading aids — for agents that should not sync the ledger themselves. Registered only when UNDERSIGNED_API_URL is set.
// Free endpoints need no key; some show more with a key; PASS ones need one (UNDERSIGNED_API_KEY: a pass or per-call
// credits). A 402 comes back as plain instructions to buy, never as an error an agent would retry. Nothing here signs
// or pays: paying is the wallet owner's own act. Every route of the API has a tool here (test/api-coverage.test.js).
import { z } from "zod";
import crypto from "node:crypto";

export const VERSION = "0.2.0";
const UA = `undersigned-mcp/${VERSION} (+https://undersigned.provenby.dev/api)`;
const text = (s) => ({ content: [{ type: "text", text: typeof s === "string" ? s : JSON.stringify(s, null, 2) }] });
const LANGS = ["en", "ar", "bn", "cs", "de", "el", "es", "fil", "fr", "hi", "hu", "id", "it", "ja", "ko", "nl", "pl", "pt", "ro", "ru", "sv", "th", "tr", "uk", "ur", "vi", "zh-CN", "zh-TW"];
export const EVENTS = ["review", "reply", "store_note", "flag", "claim", "burst", "version", "release", "certificate", "digest"];
const enc = encodeURIComponent;

/**
 * Checks one webhook delivery the way a receiver must: the V2 header `t=<unix>,v1=<hex HMAC-SHA256 of "<t>.<body>">`
 * within [toleranceSeconds] (replay guard), else the older `sha256=<hex HMAC of the body>` header. Constant-time compare.
 */
export function verifyWebhook({ secret, body, signatureV2, signature, now = Math.floor(Date.now() / 1000), toleranceSeconds = 300 }) {
  const same = (a, b) => { const x = Buffer.from(a, "hex"), y = Buffer.from(b, "hex"); return x.length === y.length && x.length > 0 && crypto.timingSafeEqual(x, y); };
  const mac = (s) => crypto.createHmac("sha256", secret).update(s).digest("hex");
  if (signatureV2) {
    const parts = Object.fromEntries(signatureV2.split(",").map((p) => p.trim().split("=")).filter((p) => p.length === 2));
    const t = Number(parts.t);
    if (!Number.isFinite(t) || !parts.v1) return { valid: false, reason: "malformed X-Undersigned-Signature-V2" };
    if (Math.abs(now - t) > toleranceSeconds) return { valid: false, reason: `timestamp ${now - t}s old: outside ${toleranceSeconds}s, possible replay` };
    return same(mac(`${t}.${body}`), parts.v1) ? { valid: true, scheme: "v2", timestamp: t } : { valid: false, reason: "v2 signature does not match this secret and body" };
  }
  if (signature) {
    const hex = signature.replace(/^sha256=/, "");
    return same(mac(body), hex) ? { valid: true, scheme: "v1", note: "v1 has no timestamp: prefer the V2 header, which guards against replays" } : { valid: false, reason: "signature does not match this secret and body" };
  }
  return { valid: false, reason: "no signature header given" };
}

export function registerApiTools(server, env = process.env) {
  // On by default against the public index; UNDERSIGNED_API_URL=off turns the API tools off, another URL points elsewhere.
  if ((env.UNDERSIGNED_API_URL || "").toLowerCase() === "off") return false;
  const base = (env.UNDERSIGNED_API_URL || "https://undersigned.provenby.dev").replace(/\/+$/, "");
  let key = env.UNDERSIGNED_API_KEY || "";
  const wallet = env.UNDERSIGNED_PRO_WALLET || (env.UNDERSIGNED_WALLETS || "").split(",")[0]?.trim() || "";

  async function call(path, { method = "GET", body, auth = true } = {}) {
    const headers = { accept: "application/json", "user-agent": UA };
    if (key && auth) headers.authorization = `Bearer ${key}`;
    if (body) headers["content-type"] = "application/json";
    let r;
    try { r = await fetch(base + path, { method, headers, body: body ? JSON.stringify(body) : undefined }); }
    catch (e) { return text({ error: "unreachable", message: `${base} did not answer: ${e.message}` }); }
    const j = await r.json().catch(() => ({ error: `HTTP ${r.status}` }));
    const limits = { remaining: r.headers.get("x-ratelimit-remaining"), pass_requests_left: r.headers.get("x-pass-requests-left") };
    if (r.status === 402) return text({ payment_required: j.message, accepts: j.accepts, how: j.how,
      next: "Ask the wallet owner to pay: api_checkout (or api_checkout with calls for per-call credits) gives Solana Pay URLs; after paying, api_redeem_pass with the payment signature and the secret. Then retry." });
    if (r.status === 429) return text({ rate_limited: j.message, retry_after_seconds: Number(r.headers.get("retry-after") || 60) });
    if (!r.ok) return text({ error: j.error || r.status, message: j.message });
    if (limits.remaining !== null || limits.pass_requests_left !== null) j._limits = limits;
    return text(j);
  }
  const q = (o) => { const s = new URLSearchParams(Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== "").map(([k, v]) => [k, String(v)])).toString(); return s ? `?${s}` : ""; };
  const pkg = z.string().describe("Android package name, e.g. com.solflare.mobile");
  const t = (name, desc, shape, fn) => server.tool(name, desc, shape, fn);

  // ---- Apps and reviews -------------------------------------------------------------------------------------------
  t("api_search_apps", "Search the dApp Store catalogue with signed scores. sort = name | signed | updated. Free: 20 rows; with a key up to 500.",
    { q: z.string().optional(), category: z.string().optional(), sort: z.enum(["name", "signed", "updated"]).optional(), limit: z.number().int().min(1).max(500).optional() },
    async (a) => call(`/v1/apps${q(a)}`));
  t("api_apps_batch", "Several apps in one call (signed score, store rating, publisher proof each). Free: 20 packages; with a key 100. Unknown packages are listed apart.",
    { packages: z.array(z.string()).min(1).max(100) }, async ({ packages }) => call(`/v1/apps?packages=${packages.map(enc).join(",")}`));
  t("api_app", "One dApp: signed score (count and mean; null when nothing is counted), Pro members' and promoted scores shown apart, store details (version, certificate, publisher), the store's own rating, publisher claims and proof, payments received.",
    { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}`));
  t("api_reviews", "Signed reviews of a dApp with every signal: attested device, weight, install age, usage time, stake as read when the review appeared (shown, never weighed), copy-paste findings, flags, helpful votes, replies. sort = newest | helpful | skr | sol. Free: 20; offset with a key.",
    { package: pkg, sort: z.enum(["newest", "helpful", "skr", "sol"]).optional(), limit: z.number().int().min(1).max(500).optional(), offset: z.number().int().min(0).optional() },
    async ({ package: p, ...a }) => call(`/v1/apps/${enc(p)}/reviews${q(a)}`));
  t("api_bursts", "Review bursts and store-rating bursts for a dApp: window, count against the usual pace, multiple, attested share, same-text count. A burst is a fact about timing, not a verdict. Free.",
    { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}/bursts`));
  t("api_summary", "A suggested summary, written by AI, of an app's COUNTED reviews: the points mentioned most, each with its count and review ids (needs 3 or more). Labelled a suggestion. Free.",
    { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}/summary`));
  t("api_versions", "PASS. Signed score per app version.", { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}/versions`));
  t("api_similar", "PASS. Reviews of a dApp with copy-paste or near-identical wording, plus AI-suggested paraphrase groups (labelled).", { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}/similar`));
  t("api_store_ratings", "PASS. Daily snapshots of a dApp's store star counts.", { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}/store-ratings`));

  // ---- AI reading aids (each labelled a suggestion; none changes a review, weight or score) -----------------------
  t("api_translate_record", "A suggested translation, by AI, of one review's or reply's public text. Needs a Pro wallet (wallet, or UNDERSIGNED_PRO_WALLET) or a key.",
    { id: z.string().describe("Record id (transaction signature)"), lang: z.enum(LANGS), wallet: z.string().optional() },
    async ({ id, lang, wallet: w }) => call(`/v1/records/${enc(id)}/translation${q({ lang, wallet: w || (key ? "" : wallet) })}`));
  t("api_translate_text", "A suggested translation, by AI, of a short public text such as a dApp Store review (up to 1,000 characters). Pro wallet or key.",
    { text: z.string().max(1000), lang: z.enum(LANGS), wallet: z.string().optional() },
    async ({ text: s, lang, wallet: w }) => call("/v1/translate", { method: "POST", body: { text: s, lang, wallet: w || wallet || undefined } }));
  t("api_translate_reply", "Put a publisher's reply DRAFT into the language of the review it answers, by AI, for the publisher to check and sign. Never signs or sends. Pro wallet or key.",
    { reply: z.string().max(600), review_id: z.string(), wallet: z.string().optional() },
    async ({ reply, review_id, wallet: w }) => call("/v1/translate/reply", { method: "POST", body: { reply, review_id, wallet: w || wallet || undefined } }));
  t("api_scoring_help", "Ask how reviews are collected, checked, weighted and shown: answered by AI from the Review Integrity Policy only, in the asker's language. Free.",
    { question: z.string().min(3).max(400), lang: z.enum(LANGS).optional() }, async (a) => call("/v1/help", { method: "POST", body: a }));
  t("api_ask_docs", "Ask a question about the API or MCP: answered by AI from the published developer docs only. Free.",
    { question: z.string().min(3).max(500) }, async (a) => call("/v1/ask", { method: "POST", body: a }));
  t("api_digest", "This week on the store: store-wide rating bursts and app updates in plain words, by AI, with the counts behind it. Pro wallet or key.",
    { wallet: z.string().optional() }, async ({ wallet: w }) => call(`/v1/digest${q({ wallet: w || (key ? "" : wallet) })}`));

  // ---- The store, wallets, rankings -------------------------------------------------------------------------------
  t("api_wallet", "A wallet: attested device (Seeker Genesis Token), staked SKR, standing counts, apps it publishes, treasury listings, Pro licence. full=true (key) lists its records.",
    { wallet: z.string(), full: z.boolean().optional() }, async ({ wallet: w, full }) => call(`/v1/wallets/${enc(w)}${full ? "?full=1" : ""}`));
  t("api_leaderboard", "Rankings by metric, overall or per category. Free: top 10.",
    { metric: z.enum(["signed_score", "signed_reviews", "store_rating", "store_reviews", "revenue", "payers", "growth"]).optional(), category: z.string().optional(), limit: z.number().int().min(1).max(500).optional() },
    async (a) => call(`/v1/leaderboard${q(a)}`));
  t("api_store_watch", "PASS. Every dApp Store app with a star-rating burst in the last N days, strongest first.", { days: z.number().int().min(1).max(30).optional() }, async (a) => call(`/v1/watch${q(a)}`));
  t("api_changes", "PASS. Changes across the store: new releases (any listing's date moved), version codes and signing certificates, each with an AI-suggested plain note. since = epoch seconds.",
    { since: z.number().int().optional() }, async (a) => call(`/v1/changes${q(a)}`));
  t("api_tags", "The review tags reviewers can choose, with what each means.", {}, async () => call("/v1/tags"));

  // ---- Payments ---------------------------------------------------------------------------------------------------
  t("api_payments_board", "The payments board: publisher-CONFIRMED treasuries ordered by USD received on-chain (on the day of each payment). Free: top 10. Not a statement about profit.",
    { limit: z.number().int().min(1).max(500).optional() }, async (a) => call(`/v1/payments${q(a)}`));
  t("api_app_payments", "PASS. Payments a publisher-confirmed treasury received for a dApp, with a monthly series (USD on the day of each payment).", { package: pkg }, async ({ package: p }) => call(`/v1/apps/${enc(p)}/payments`));
  t("api_found_treasuries", "Wallets FOUND IN APPS' CODE that received payments from 5+ wallets, NOT confirmed by the publisher: a separate list by app name, never ranked with the board. Always say 'found in the app's code, not confirmed' when mentioning one. Free: 20 rows.",
    { limit: z.number().int().min(1).max(500).optional() }, async (a) => call(`/v1/payments/found${q(a)}`));
  t("api_found_analytics", "Analytics for one app's found-in-code wallets from their FULL payment history: first and last payment, monthly series with new and returning payers and USD on the day paid, last 12 weeks, repeat payers, top-payer concentration, and how much history has been read. Free.",
    { package: pkg }, async ({ package: p }) => call(`/v1/payments/found/${enc(p)}`));
  t("api_found_trends", "Store-wide monthly payments into every wallet found in app code (each wallet once). Free.", {}, async () => call("/v1/payments/found/trends"));

  // ---- The ledger -------------------------------------------------------------------------------------------------
  t("api_records", "The raw decoded ledger, newest first. Filter by type (rev rvk rep clm flg clr up dsp use tim snt trs trx bst pro …), package, signer, since/until (epoch seconds); page with before = the last id. Free: 20 rows.",
    { type: z.string().optional(), package: z.string().optional(), signer: z.string().optional(), since: z.number().int().optional(), until: z.number().int().optional(), before: z.string().optional(), limit: z.number().int().min(1).max(500).optional() },
    async (a) => call(`/v1/records${q(a)}`));
  t("api_record", "One ledger record by id (transaction signature, or signature:n), decoded, with its fee and whether it was withdrawn.", { id: z.string() }, async ({ id }) => call(`/v1/records/${enc(id)}`));
  t("api_stats", "Ledger totals: records, live and counted reviews, signers, attested signers, apps, replies, claims, flags, listings, first and latest record.", {}, async () => call("/v1/stats"));
  t("api_dataset", "Where to download the whole ledger with scores as one JSON file (free), and how big it is now.", {}, async () => {
    const r = await fetch(`${base}/v1/dataset.json`, { method: "HEAD", headers: { "user-agent": UA } }).catch(() => null);
    return text({ url: `${base}/v1/dataset.json`, bytes: r ? Number(r.headers.get("content-length")) || null : null, licence: "The ledger is public on-chain data. Store data shown alongside it belongs to the Solana dApp Store." });
  });
  t("api_health", "The index's status: last ledger and store syncs, last error, snapshot days, hook failures.", {}, async () => call("/v1/health", { auth: false }));
  t("api_changelog", "What changed in the API and MCP, newest first.", {}, async () => call("/v1/changelog", { auth: false }));

  // ---- Keys, pay per call ------------------------------------------------------------------------------------------
  t("api_pricing", "What a pass and per-call credits cost (SOL or SKR; SKR about 10% below SOL; less again with a Seeker Genesis Token), what they buy, and how to redeem.", {}, async () => call("/v1/pricing"));
  t("api_checkout", "Start buying a pass, or per-call credits with calls = N: returns a secret and Solana Pay URLs whose memo commits to it. The wallet owner pays one URL; then call api_redeem_pass with the payment signature and this secret. This tool never pays.",
    { calls: z.number().int().min(1).max(10_000_000).optional() }, async ({ calls }) => call("/v1/checkout", { method: "POST", body: calls ? { calls } : {}, auth: false }));
  t("api_redeem_pass", "Turn a payment into an API key. signature = the payment transaction; then EITHER secret (from api_checkout) OR message_signature = the PAYING wallet's signature of \"Undersigned API key for <signature>\". Paying again tops up the same key. The key is used for the rest of this session; save it as UNDERSIGNED_API_KEY to keep it.",
    { signature: z.string(), secret: z.string().optional(), message_signature: z.string().optional() }, async (a) => {
      const r = await fetch(`${base}/v1/keys`, { method: "POST", headers: { "content-type": "application/json", "user-agent": UA }, body: JSON.stringify(a) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) return text({ refused: j.message || r.status });
      key = j.key;
      return text({ ...j, saved_for_this_session: true });
    });
  t("api_pass", "This session's key: requests (or calls) left and end date.", {}, async () => key ? call("/v1/keys/me") : text("No key. Set UNDERSIGNED_API_KEY or redeem a payment with api_redeem_pass."));

  // ---- Webhooks ---------------------------------------------------------------------------------------------------
  t("api_hook_create", `PASS. Create a webhook (https only) for watched apps. events: ${EVENTS.join(", ")} (digest = the weekly store digest). Returns its secret ONCE. Each delivery carries X-Undersigned-Signature-V2: t=<unix>,v1=<hex HMAC-SHA256 of "<t>.<body>">; check it with api_verify_webhook. Failed deliveries are retried six times over about a day.`,
    { url: z.string().url(), packages: z.array(z.string()).min(1).max(50), events: z.array(z.enum(EVENTS)).optional() },
    async (a) => call("/v1/hooks", { method: "POST", body: a }));
  t("api_hooks", "PASS. This key's webhooks.", {}, async () => call("/v1/hooks"));
  t("api_hook_test", "PASS. Send a signed test ping to a webhook now and report the receiver's answer.", { id: z.string() }, async ({ id }) => call(`/v1/hooks/${enc(id)}/test`, { method: "POST", body: {} }));
  t("api_hook_deliveries", "PASS. The last 100 delivery attempts for a webhook, with the receiver's status and the next retry.", { id: z.string() }, async ({ id }) => call(`/v1/hooks/${enc(id)}/deliveries`));
  t("api_hook_delete", "PASS. Delete a webhook by id.", { id: z.string() }, async ({ id }) => call(`/v1/hooks/${enc(id)}`, { method: "DELETE" }));
  t("api_verify_webhook", "Check a webhook delivery locally (nothing is sent anywhere): give the hook's secret, the raw body and the signature header(s). Rejects V2 timestamps older than 5 minutes.",
    { secret: z.string(), body: z.string(), signature_v2: z.string().optional(), signature: z.string().optional() },
    async (a) => text(verifyWebhook({ secret: a.secret, body: a.body, signatureV2: a.signature_v2, signature: a.signature })));

  // ---- Embeds -----------------------------------------------------------------------------------------------------
  t("api_badge", "The embeddable Undersigned badge for a dApp: the image URL, the page it links to, and ready HTML and Markdown for a website or README.",
    { package: pkg }, async ({ package: p }) => { const e = enc(p); const img = `${base}/v1/badge/${e}.svg`, page = `${base}/app/${e}`;
      return text({ image: img, page, html: `<a href="${page}"><img src="${img}" alt="Undersigned signed reviews"></a>`, markdown: `[![Undersigned](${img})](${page})` }); });
  return true;
}
