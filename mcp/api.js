// Undersigned API tools (docs/API.md): the server-side index — scores, bursts, store watch, payments, rankings —
// for agents that should not sync the ledger themselves. Registered only when UNDERSIGNED_API_URL is set.
// Free endpoints need no key; paid ones need a pass (UNDERSIGNED_API_KEY). A 402 comes back as plain instructions to
// buy one, never as an error an agent would retry. Nothing here signs or pays: paying is the operator's own act.
import { z } from "zod";

const text = (s) => ({ content: [{ type: "text", text: typeof s === "string" ? s : JSON.stringify(s, null, 2) }] });

export function registerApiTools(server, env = process.env) {
  const base = (env.UNDERSIGNED_API_URL || "").replace(/\/+$/, "");
  if (!base) return false;
  let key = env.UNDERSIGNED_API_KEY || "";

  async function call(path, { method = "GET", body } = {}) {
    const headers = { accept: "application/json" };
    if (key) headers.authorization = `Bearer ${key}`;
    if (body) headers["content-type"] = "application/json";
    const r = await fetch(base + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    const j = await r.json().catch(() => ({ error: `HTTP ${r.status}` }));
    if (r.status === 402) return text({ payment_required: j.message, how_to_buy: j.pricing,
      next: "Pay with the paying wallet (a Solana Pay URL is in how_to_buy.solana_pay), sign the message shown in how_to_buy.redeem with that wallet, then call api_redeem_pass." });
    if (r.status === 429) return text({ rate_limited: j.message, retry_after_seconds: Number(r.headers.get("retry-after") || 60) });
    if (!r.ok) return text({ error: j.error || r.status, message: j.message });
    return text(j);
  }
  const q = (o) => { const s = new URLSearchParams(Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== "").map(([k, v]) => [k, String(v)])).toString(); return s ? `?${s}` : ""; };

  server.tool("api_search_apps", "Search the dApp Store catalogue with signed scores. sort = name | signed | updated. Free shows 20 rows; a pass shows more.",
    { q: z.string().optional(), category: z.string().optional(), sort: z.enum(["name", "signed", "updated"]).optional(), limit: z.number().int().min(1).max(2000).optional() },
    async (a) => call(`/v1/apps${q(a)}`));
  server.tool("api_app", "One dApp: signed score (rank score + mean, counted reviews, stars, tags, install-age and usage-time mix), store details (version, certificate, publisher, website), store rating, publisher claims and proof, store notes, payments received.",
    { package: z.string() }, async ({ package: p }) => call(`/v1/apps/${encodeURIComponent(p)}`));
  server.tool("api_reviews", "Signed reviews of a dApp with every signal: attested device, weight, credibility, install age, usage time, season, paid for this app, copy-paste findings, flags, helpful weight, replies (publisher marked). Free: newest 20.",
    { package: z.string(), limit: z.number().int().min(1).max(500).optional(), offset: z.number().int().min(0).optional() },
    async ({ package: p, ...a }) => call(`/v1/apps/${encodeURIComponent(p)}/reviews${q(a)}`));
  server.tool("api_bursts", "PASS. Review bursts and store-rating bursts for a dApp: window, count against the usual pace, multiple, attested share, same-text count, season overlap, 30-day sparkline. A burst is a fact about timing, not a verdict.",
    { package: z.string() }, async ({ package: p }) => call(`/v1/apps/${encodeURIComponent(p)}/bursts`));
  server.tool("api_versions", "PASS. Signed score per app version.", { package: z.string() }, async ({ package: p }) => call(`/v1/apps/${encodeURIComponent(p)}/versions`));
  server.tool("api_store_ratings", "PASS. Daily snapshots of a dApp's store star counts.", { package: z.string() }, async ({ package: p }) => call(`/v1/apps/${encodeURIComponent(p)}/store-ratings`));
  server.tool("api_app_payments", "PASS. Payments a publisher-confirmed treasury received for a dApp, with a monthly series (USD on the day of each payment).", { package: z.string() }, async ({ package: p }) => call(`/v1/apps/${encodeURIComponent(p)}/payments`));
  server.tool("api_wallet", "A wallet: attested device (Seeker Genesis Token), staked SKR and review weight, credibility and standing, apps it publishes, treasury listings, Pro licence. full=true (pass) lists its records.",
    { wallet: z.string(), full: z.boolean().optional() }, async ({ wallet, full }) => call(`/v1/wallets/${wallet}${full ? "?full=1" : ""}`));
  server.tool("api_leaderboard", "Rankings by metric (signed_score, signed_reviews, store_rating, store_reviews, revenue, payers, growth), overall or per category. Free: top 10.",
    { metric: z.enum(["signed_score", "signed_reviews", "store_rating", "store_reviews", "revenue", "payers", "growth"]).optional(), category: z.string().optional(), limit: z.number().int().min(1).max(500).optional() },
    async (a) => call(`/v1/leaderboard${q(a)}`));
  server.tool("api_store_watch", "PASS. Every dApp Store app with a star-rating burst in the last N days, strongest first.", { days: z.number().int().min(1).max(30).optional() }, async (a) => call(`/v1/watch${q(a)}`));
  server.tool("api_changes", "PASS. Version and signing-certificate changes seen in the store (a changed certificate is the loudest signal a store can give). since = epoch seconds.",
    { since: z.number().int().optional() }, async (a) => call(`/v1/changes${q(a)}`));
  server.tool("api_payments_board", "The payments board: publisher-confirmed treasuries ordered by USD received on-chain. Free: top 10.", { limit: z.number().int().min(1).max(500).optional() }, async (a) => call(`/v1/payments${q(a)}`));
  server.tool("api_records", "PASS. The raw decoded ledger, newest first. Filter by type (rev rvk rep clm flg clr up dsp use tim snt trs trx …), package, signer, since/until (epoch seconds); page with before = the last id.",
    { type: z.string().optional(), package: z.string().optional(), signer: z.string().optional(), since: z.number().int().optional(), until: z.number().int().optional(), before: z.string().optional(), limit: z.number().int().min(1).max(1000).optional() },
    async (a) => call(`/v1/records${q(a)}`));
  server.tool("api_record", "One ledger record by id (transaction signature, or signature:n), decoded, with its fee and whether it was withdrawn.", { id: z.string() }, async ({ id }) => call(`/v1/records/${encodeURIComponent(id)}`));
  server.tool("api_stats", "Ledger totals: records, live and counted reviews, signers, attested signers, apps, replies, claims, flags, listings, first and latest record.", {}, async () => call("/v1/stats"));
  server.tool("api_pricing", "What a pass costs (SOL or SKR; less in SKR with a Seeker Genesis Token), what it buys, Solana Pay URLs, and how to redeem.", {}, async () => call("/v1/pricing"));
  server.tool("api_redeem_pass", "Turn a pass payment into an API key. signature = the payment transaction; message_signature = the PAYING wallet's signature of \"Undersigned API key for <signature>\" (base58, base64 or hex). The key is used for the rest of this session; save it as UNDERSIGNED_API_KEY to keep it.",
    { signature: z.string(), message_signature: z.string() }, async (a) => {
      const r = await fetch(`${base}/v1/keys`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(a) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) return text({ refused: j.message || r.status });
      key = j.key;
      return text({ ...j, saved_for_this_session: true });
    });
  server.tool("api_pass", "This session's pass: requests left and end date.", {}, async () => key ? call("/v1/keys/me") : text("No key. Set UNDERSIGNED_API_KEY or redeem a pass with api_redeem_pass."));
  return true;
}
