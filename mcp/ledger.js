// Ledger reader shared by the MCP tools. Same rules as protocol/Ranking.kt and Reputation.kt, in JS.
import fs from "node:fs";
import path from "node:path";
import os from "node:os";

export const REGISTRY = "BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs";
export const MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr";
export const LIGHTHOUSE = "L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95";
export const RPCS = (process.env.UNDERSIGNED_RPC ? [process.env.UNDERSIGNED_RPC] : []).concat(["https://api.mainnet-beta.solana.com", "https://solana-rpc.publicnode.com"]);
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
    default: return null;
  }
}
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
      if (s.err || known.has(s.signature) || !s.memo || !s.memo.includes("SRL1|")) continue;
      const tx = await rpc("getTransaction", [s.signature, { encoding: "jsonParsed", maxSupportedTransactionVersion: 0 }]); if (!tx || tx.meta.err) continue;
      const keys = tx.transaction.message.accountKeys; const signer = keys[0].pubkey || keys[0];
      const ixs = tx.transaction.message.instructions;
      const seeker = ixs.some((i) => i.programId === LIGHTHOUSE) && ixs.some((i) => i.programId === "ComputeBudget111111111111111111111111111111");
      let i = 0;
      for (const ix of ixs) if (ix.program === "spl-memo" && typeof ix.parsed === "string") { const m = decode(ix.parsed); if (m) fresh.push({ id: i ? `${s.signature}:${i}` : s.signature, signer, t: tx.blockTime || s.blockTime, seeker, fee: tx.meta.fee, m }); i++; }
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
  return { bySig, revoked, replies, claims, flags, clears, ups, disputes, live };
}

export function stars() { try { return new Set(JSON.parse(fs.readFileSync(STARS, "utf8"))); } catch { return new Set(); } }
export function setStars(s) { fs.writeFileSync(STARS, JSON.stringify([...s])); }

export function macNotify(title, body) {
  if (process.platform !== "darwin") return;
  import("node:child_process").then(({ execFile }) => execFile("osascript", ["-e", `display notification ${JSON.stringify(body)} with title ${JSON.stringify(title)}`]));
}
