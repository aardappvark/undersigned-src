// Ledger reader shared by the MCP tools. Same rules as protocol/Ranking.kt and Reputation.kt, in JS.
import fs from "node:fs";
import path from "node:path";
import os from "node:os";

export const REGISTRY = "BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs";
export const MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr";
export const LIGHTHOUSE = "L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95";
export const RPCS = (process.env.UNDERSIGNED_RPC ? [process.env.UNDERSIGNED_RPC] : []).concat(["https://api.mainnet-beta.solana.com", "https://solana-rpc.publicnode.com"]);
export const TREASURY = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q";
export const SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3";
export const PAYMENT_MEMO = "UNS";          // this app's fleet code; a payment's only memo
export const PRO_FLOOR_LAMPORTS = 60_700_000, PRO_FLOOR_SKR = 276_000_000, PRO_MONTHS = 12;
export const HOME = path.join(os.homedir(), ".undersigned");
fs.mkdirSync(HOME, { recursive: true });
const CACHE = path.join(HOME, "ledger.json");
const STARS = path.join(HOME, "starred.json");

export const TAGS = ["works","crashes","wallet-ok","wallet-fails","fast","slow","battery","ads","paywall","fair-price","overpriced","perms-ok","perms-excess","spam-reviews","misleading","suspected-scam","abandoned","active-dev","onboarding-ok","confusing","offline-ok","needs-account","seeker-native","webview"];
const SIG = /^[1-9A-HJ-NP-Za-km-z]{86,88}(:[1-9][0-9]?)?$/;
const PKG = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;

export async function rpc(method, params) {
  let last;
  for (const url of RPCS) {
    try {
      const r = await fetch(url, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, params }) });
      const j = await r.json(); if (j.error) throw new Error(j.error.message); return j.result;
    } catch (e) { last = e; }
  }
  throw last;
}
const b64u = (s) => { s = s.replace(/-/g, "+").replace(/_/g, "/"); while (s.length % 4) s += "="; return Buffer.from(s, "base64").toString("utf8"); };
export const b64uEnc = (s) => Buffer.from(s, "utf8").toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

export function decode(raw) {
  const f = raw.split("|"); if (f[0] !== "SRL1") return null;
  switch (f[1]) {
    case "rev": { if (f.length !== 7 || !PKG.test(f[2])) return null; const rating = +f[3]; if (!(rating >= 1 && rating <= 5)) return null; const tags = f[4].split(","); if (!tags.every((t) => TAGS.includes(t))) return null; return { t: "rev", pkg: f[2], rating, tags, vc: f[5], text: f[6] ? b64u(f[6]) : null }; }
    case "rvk": return f.length === 3 && SIG.test(f[2]) ? { t: "rvk", target: f[2] } : null;
    case "rep": return f.length === 4 && SIG.test(f[2]) && f[3] ? { t: "rep", target: f[2], text: b64u(f[3]) } : null;
    case "clm": return f.length === 3 && PKG.test(f[2]) ? { t: "clm", pkg: f[2] } : null;
    case "unv": return f.length === 3 && SIG.test(f[2]) ? { t: "unv", target: f[2] } : null;
    case "unf": return f.length === 3 && SIG.test(f[2]) ? { t: "unf", target: f[2] } : null;
    case "clr": return f.length === 3 && SIG.test(f[2]) ? { t: "clr", target: f[2] } : null;
    case "up": return f.length === 3 && SIG.test(f[2]) ? { t: "up", target: f[2] } : null;
    case "dsp": return f.length === 4 && SIG.test(f[2]) && f[3] ? { t: "dsp", target: f[2], text: b64u(f[3]) } : null;
    case "flg": return f.length === 4 && SIG.test(f[2]) && ["spam","abuse","offtopic","misleading"].includes(f[3]) ? { t: "flg", target: f[2], reason: f[3] } : null;
    // Pro licence term (months) — rides in the same tx as the payment; this reader does NOT verify the payment, the phone does (balances).
    // Legacy: read, never written. A licence is now the payment itself — see sync().
    case "pro": { const m = +f[2]; return f.length === 3 && Number.isInteger(m) && m >= 1 && m <= 120 ? { t: "pro", months: m, legacy: true } : null; }
    // A shared Pro seat, named by the pair hash (SHA-256(granter:grantee)[:16]); shx takes it back.
    case "shr": return f.length === 3 && /^[0-9a-f]{32}$/.test(f[2]) ? { t: "shr", tag: f[2] } : null;
    case "shx": return f.length === 3 && /^[0-9a-f]{32}$/.test(f[2]) ? { t: "shx", tag: f[2] } : null;
    // The reviewer's device report riding in the review's own transaction (install age, usage time): shown, never scored.
    case "use": return f.length === 4 && PKG.test(f[2]) && USE[f[3]] ? { t: "use", pkg: f[2], b: f[3] } : null;
    case "tim": return f.length === 4 && PKG.test(f[2]) && TIM[f[3]] ? { t: "tim", pkg: f[2], b: f[3] } : null;
    // A verified publisher's signed note on a dApp Store review of their app.
    case "snt": return f.length === 5 && PKG.test(f[2]) && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(f[3]) && f[4] ? { t: "snt", pkg: f[2], rid: f[3], text: b64u(f[4]) } : null;
    case "trs": return f.length === 4 && PKG.test(f[2]) ? { t: "trs", pkg: f[2], code: f[3] } : null;
    case "trx": return f.length === 3 && PKG.test(f[2]) ? { t: "trx", pkg: f[2] } : null;
    default: return null;
  }
}
export const USE = { d0: "under a day", d1: "1–6 days", w1: "1–4 weeks", m1: "1–3 months", m3: "3+ months" };
export const TIM = { t0: "under 10 minutes", t1: "10–60 minutes", t2: "1–5 hours", t3: "5–20 hours", t4: "20+ hours" };
export function encodeReply(target, text) {
  if (!SIG.test(target)) throw new Error("bad record id");
  text = text.trim(); if (!text || text.length > 140 || Buffer.byteLength(text) > 280 || /[|\n\r]/.test(text)) throw new Error("reply text must be 1–140 chars, ≤280 bytes, no '|' or newlines");
  return `SRL1|rep|${target}|${b64uEnc(text)}`;
}

export function loadCache() { try { return JSON.parse(fs.readFileSync(CACHE, "utf8")); } catch { return { entries: [] }; } }
export function saveCache(c) { fs.writeFileSync(CACHE, JSON.stringify(c)); }

/** Incremental sync: newest-first signatures until the newest cached one. */
export async function sync() {
  const cache = loadCache();
  const known = new Set(cache.entries.map((e) => e.id.split(":")[0]));
  const newest = cache.entries[0]?.id.split(":")[0];
  let before; const fresh = [];
  for (;;) {
    const cfg = { limit: 1000, commitment: "confirmed" }; if (before) cfg.before = before; if (newest) cfg.until = newest;
    const page = await rpc("getSignaturesForAddress", [REGISTRY, cfg]); if (!page.length) break;
    for (const s of page) {
      // A Pro payment's only memo is the fleet code, so the protocol tag alone would skip it.
      if (s.err || known.has(s.signature) || !s.memo || !(s.memo.includes("SRL1|") || s.memo.includes(PAYMENT_MEMO))) continue;
      const tx = await rpc("getTransaction", [s.signature, { encoding: "jsonParsed", maxSupportedTransactionVersion: 0 }]); if (!tx || tx.meta.err) continue;
      const keys = tx.transaction.message.accountKeys; const signer = keys[0].pubkey || keys[0];
      const ixs = tx.transaction.message.instructions;
      const seeker = ixs.some((i) => i.programId === LIGHTHOUSE) && ixs.some((i) => i.programId === "ComputeBudget111111111111111111111111111111");
      let i = 0, records = 0;
      for (const ix of ixs) if (ix.program === "spl-memo" && typeof ix.parsed === "string") { const m = decode(ix.parsed); if (m) { fresh.push({ id: i ? `${s.signature}:${i}` : s.signature, signer, t: tx.blockTime || s.blockTime, seeker, fee: tx.meta.fee, m }); records++; } i++; }
      // A licence IS its payment: treasury credit at or above the floor, carrying this app's memo and reference.
      // The term comes from the amount, not from anything written in the memo. Pre-20 Sep 2026 licences carry a
      // legacy `pro` record instead and are decoded above.
      if (!records) {
        const ti = keys.findIndex((k) => (k.pubkey || k) === TREASURY);
        const sol = ti >= 0 ? tx.meta.postBalances[ti] - tx.meta.preBalances[ti] : 0;
        const sum = (arr) => (arr || []).filter((b) => b.owner === TREASURY && b.mint === SKR_MINT)
          .reduce((n, b) => n + Number(b.uiTokenAmount.amount || 0), 0);
        const skr = sum(tx.meta.postTokenBalances) - sum(tx.meta.preTokenBalances);
        if (sol >= PRO_FLOOR_LAMPORTS || skr >= PRO_FLOOR_SKR)
          fresh.push({ id: s.signature, signer, t: tx.blockTime || s.blockTime, seeker, fee: tx.meta.fee, m: { t: "pro", months: PRO_MONTHS, paid: sol >= PRO_FLOOR_LAMPORTS ? `${sol / 1e9} SOL` : `${skr / 1e6} SKR` } });
      }
    }
    if (page.length < 1000) break; before = page[page.length - 1].signature;
  }
  cache.entries = [...fresh, ...cache.entries].sort((a, b) => b.t - a.t);
  cache.syncedAt = Date.now(); saveCache(cache); return cache.entries;
}

export function derive(entries) {
  const bySig = Object.fromEntries(entries.map((e) => [e.id, e]));
  const revoked = new Set(entries.filter((e) => e.m.t === "rvk" && bySig[e.m.target] && bySig[e.m.target].signer === e.signer).map((e) => e.m.target));
  const replies = {}; for (const e of entries.filter((e) => e.m.t === "rep")) (replies[e.m.target] = replies[e.m.target] || []).push(e);
  const claims = {}; for (const e of entries.filter((e) => e.m.t === "clm")) (claims[e.m.pkg] = claims[e.m.pkg] || new Set()).add(e.signer);
  // latest flag / withdrawal per wallet wins; clears offset
  const flagState = {}; for (const e of [...entries].sort((a, b) => a.t - b.t)) { if (e.m.t === "flg" || e.m.t === "unf") { (flagState[e.m.target] = flagState[e.m.target] || {})[e.signer] = e.m.t === "flg"; } }
  const flags = {}; for (const [target, m] of Object.entries(flagState)) { const author = bySig[target]?.signer; const s = new Set(Object.entries(m).filter(([w, on]) => on && w !== author).map(([w]) => w)); if (s.size) flags[target] = s; }
  const clears = {}; for (const e of entries.filter((e) => e.m.t === "clr")) { if (bySig[e.m.target]?.signer === e.signer) continue; (clears[e.m.target] = clears[e.m.target] || new Set()).add(e.signer); }
  const ups = {}; for (const e of entries.filter((e) => e.m.t === "up")) { const author = bySig[e.m.target]?.signer; if (author === e.signer) continue; (ups[e.m.target] = ups[e.m.target] || new Set()).add(e.signer); }
  const disputes = {}; for (const e of entries.filter((e) => e.m.t === "dsp")) if (bySig[e.m.target]?.signer === e.signer && !disputes[e.m.target]) disputes[e.m.target] = e.m.text;
  const live = entries.filter((e) => e.m.t === "rev" && !revoked.has(e.id));
  // Install age / usage time attach to the review in the SAME transaction, same signer, same app.
  const extras = {}; for (const e of entries) { if (e.m.t !== "use" && e.m.t !== "tim") continue; const r = bySig[e.id.split(":")[0]];
    if (!r || r.m.t !== "rev" || r.signer !== e.signer || r.m.pkg !== e.m.pkg) continue; (extras[r.id] = extras[r.id] || {})[e.m.t] = e.m.t === "use" ? USE[e.m.b] : TIM[e.m.b]; }
  // Licence records per wallet (unverified here: the memo says a term was bought; only balances prove it) and active share seats per granter.
  const licences = {}; for (const e of entries.filter((e) => e.m.t === "pro")) (licences[e.signer] = licences[e.signer] || []).push({ id: e.id, months: e.m.months, at: e.t });
  const shares = {}; for (const e of [...entries].sort((a, b) => a.t - b.t)) { if (e.m.t !== "shr" && e.m.t !== "shx") continue; const seats = (shares[e.signer] = shares[e.signer] || []); if (e.m.t === "shx") { const i = seats.indexOf(e.m.tag); if (i >= 0) seats.splice(i, 1); } else if (!seats.includes(e.m.tag)) { seats.push(e.m.tag); if (seats.length > 2) seats.shift(); } }
  return { bySig, revoked, replies, claims, flags, clears, ups, disputes, live, licences, shares, extras };
}

export function stars() { try { return new Set(JSON.parse(fs.readFileSync(STARS, "utf8"))); } catch { return new Set(); } }
export function setStars(s) { fs.writeFileSync(STARS, JSON.stringify([...s])); }

export function macNotify(title, body) {
  if (process.platform !== "darwin") return;
  import("node:child_process").then(({ execFile }) => execFile("osascript", ["-e", `display notification ${JSON.stringify(body)} with title ${JSON.stringify(title)}`]));
}
