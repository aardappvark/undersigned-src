#!/usr/bin/env node
// Undersigned MCP server — the publisher's and reviewer's desk on a Mac.
// Reads the on-chain ledger and the store's public reviews; never touches the store's write API.
// Signing: (a) hand-off — returns an undersigned://reply?... link, copied to the clipboard, that the Seeker
// app opens pre-filled so the phone signs (Seed Vault double-tap); or (b) optional local signing from a
// keypair file the operator configures (UNDERSIGNED_KEYPAIR). Claude never sees the key.
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import fs from "node:fs";
import { execFile } from "node:child_process";
import WebSocket from "ws";
import * as L from "./ledger.js";

const STORE = "https://dappstore.solanamobile.com/graphql";
const SC = { locale: "en-US", platformSdk: 35, pixelDensity: 420, model: "Seeker" };
async function store(op, query, variables) {
  const r = await fetch(STORE, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ operationName: op, variables, query }) });
  const j = await r.json(); if (j.errors) throw new Error(JSON.stringify(j.errors).slice(0, 200)); return j.data;
}
const Q_REVIEWS = "query DAppReviews($systemContext: SystemContext!, $androidPackage: String!, $first: Int!, $after: String) { dAppReviews(systemContext: $systemContext, androidPackage: $androidPackage, first: $first, after: $after) { edges { node { id createdAt rating review walletAddress domain reply { reply } } } pageInfo { hasNextPage endCursor } } }";
const Q_SUMMARY = "query DAppByPackageSummary($systemContext: SystemContext!, $androidPackage: String!) { dAppByAndroidPackage(systemContext: $systemContext, androidPackage: $androidPackage) { androidPackage rating { rating reviewsByRating } lastRelease(systemContext: $systemContext) { displayName androidDetails { versionCode certFingerprint } publisherDetails { name website supportEmail } } } }";

const myWallets = (process.env.UNDERSIGNED_WALLETS || "").split(",").map((s) => s.trim()).filter(Boolean);
const text = (s) => ({ content: [{ type: "text", text: typeof s === "string" ? s : JSON.stringify(s, null, 2) }] });
const who = (e) => e.signer.slice(0, 4) + "…" + e.signer.slice(-4);

const server = new McpServer({ name: "undersigned", version: "0.1.0" });

server.tool("ledger_sync", "Read new records from the Undersigned registry on Solana into the local cache. Returns counts.", {}, async () => {
  const entries = await L.sync(); const d = L.derive(entries);
  return text({ records: entries.length, liveReviews: d.live.length, replies: entries.filter((e) => e.m.t === "rep").length, claims: Object.keys(d.claims).length, flags: entries.filter((e) => e.m.t === "flg").length, syncedAt: new Date().toISOString() });
});

server.tool("inbox", "Publisher and reviewer queues for the operator's wallets (UNDERSIGNED_WALLETS or the wallets given): unanswered signed reviews on apps they claimed, replies to their records, starred records.", { wallets: z.array(z.string()).optional() }, async ({ wallets }) => {
  const ws = wallets?.length ? wallets : myWallets; if (!ws.length) return text("No wallets configured. Set UNDERSIGNED_WALLETS=addr1,addr2 or pass wallets.");
  const entries = L.loadCache().entries; const d = L.derive(entries); const st = L.stars();
  const myApps = Object.entries(d.claims).filter(([, s]) => ws.some((w) => s.has(w))).map(([p]) => p);
  const unanswered = d.live.filter((e) => myApps.includes(e.m.pkg) && !ws.includes(e.signer) && !(d.replies[e.id] || []).some((r) => ws.includes(r.signer)))
    .map((e) => ({ id: e.id, app: e.m.pkg, rating: e.m.rating, tags: e.m.tags, text: e.m.text, by: who(e), seekerWallet: e.seeker, at: new Date(e.t * 1000).toISOString(), starred: st.has(e.id) }));
  const mine = new Set(entries.filter((e) => ws.includes(e.signer)).map((e) => e.id));
  const repliesToMe = entries.filter((e) => e.m.t === "rep" && !ws.includes(e.signer) && mine.has(e.m.target)).map((e) => ({ id: e.id, on: e.m.target, by: who(e), text: e.m.text, at: new Date(e.t * 1000).toISOString() }));
  const starred = entries.filter((e) => st.has(e.id)).map((e) => ({ id: e.id, kind: e.m.t, app: e.m.pkg, text: e.m.text, by: who(e) }));
  return text({ claimedApps: myApps, unanswered, repliesToMe, starred, note: "claims are verified by the phone app against the store-listed website; this list trusts on-chain claims by your own wallets" });
});

server.tool("app_reviews", "Signed reviews of one package from the ledger, with replies, flags and withdrawal state.", { package: z.string() }, async ({ package: pkg }) => {
  const entries = L.loadCache().entries; const d = L.derive(entries);
  const rows = d.live.filter((e) => e.m.pkg === pkg).map((e) => ({ id: e.id, rating: e.m.rating, tags: e.m.tags, text: e.m.text, version: e.m.vc, by: who(e), signer: e.signer, seekerWallet: e.seeker, at: new Date(e.t * 1000).toISOString(), flags: d.flags[e.id]?.size || 0, clears: d.clears[e.id]?.size || 0, helpful: d.ups[e.id]?.size || 0, dispute: d.disputes[e.id] || null, replies: (d.replies[e.id] || []).map((r) => ({ id: r.id, by: who(r), text: r.m.text, at: new Date(r.t * 1000).toISOString() })) }));
  const mean = rows.length ? rows.reduce((s, r) => s + r.rating, 0) / rows.length : null;
  return text({ package: pkg, signed: rows.length, unweightedMean: mean, verifiedClaimsOnChain: [...(d.claims[pkg] || [])], reviews: rows });
});

server.tool("profile", "A wallet's public standing on the ledger: counts, replies, flags, apps it claims, responsiveness (median hours from review to its reply).", { wallet: z.string() }, async ({ wallet }) => {
  const entries = L.loadCache().entries; const d = L.derive(entries);
  const mine = entries.filter((e) => e.signer === wallet);
  const claimed = Object.entries(d.claims).filter(([, s]) => s.has(wallet)).map(([p]) => p);
  const responsiveness = claimed.map((pkg) => { const revs = d.live.filter((e) => e.m.pkg === pkg); const delays = revs.map((r) => { const first = (d.replies[r.id] || []).filter((x) => x.signer === wallet && x.t >= r.t).sort((a, b) => a.t - b.t)[0]; return first ? (first.t - r.t) / 3600 : null; }).filter((x) => x !== null).sort((a, b) => a - b); const med = delays.length ? delays[Math.floor(delays.length / 2)] : null; return { package: pkg, answered: delays.length, total: revs.length, medianHours: med, bucket: !revs.length ? "no reviews" : !delays.length ? "never" : med < 24 ? "hours" : med < 168 ? "days" : med < 720 ? "weeks" : "months" }; });
  const lic = (d.licences[wallet] || []).map((l) => ({ id: l.id, months: l.months, at: new Date(l.at * 1000).toISOString(), runsTo: new Date((l.at + l.months * 365 * 86400 / 12) * 1000).toISOString() }));
  return text({ wallet, reviews: mine.filter((e) => e.m.t === "rev" && !d.revoked.has(e.id)).length, withdrawn: mine.filter((e) => e.m.t === "rvk").length, repliesGiven: mine.filter((e) => e.m.t === "rep").length, flagsGiven: mine.filter((e) => e.m.t === "flg").length, recordsFlaggedByOthers: Object.entries(d.flags).filter(([id]) => d.bySig[id]?.signer === wallet).length, claimedApps: claimed, responsiveness, firstSeen: mine.filter((e) => e.m.t !== "pro" && e.m.t !== "shr" && e.m.t !== "shx").length ? new Date(Math.min(...mine.filter((e) => e.m.t !== "pro" && e.m.t !== "shr" && e.m.t !== "shx").map((e) => e.t)) * 1000).toISOString() : null, proLicences: lic, sharedSeats: (d.shares[wallet] || []).length, note: lic.length ? "licence records are shown as written; the phone app honours one only if that transaction paid the treasury" : undefined });
});

server.tool("desk", "The publisher desk for the operator's wallets (UNDERSIGNED_WALLETS or the wallets given): every app they claim with its unanswered count and reply cadence, the unanswered queue oldest first, and the median reply gap readers see. Mirrors the phone's Pro publisher desk.", { wallets: z.array(z.string()).optional() }, async ({ wallets }) => {
  const ws = wallets?.length ? wallets : myWallets; if (!ws.length) return text("No wallets configured. Set UNDERSIGNED_WALLETS=addr1,addr2 or pass wallets.");
  const entries = L.loadCache().entries; const d = L.derive(entries);
  const myApps = Object.entries(d.claims).filter(([, s]) => ws.some((w) => s.has(w))).map(([p]) => p);
  const reviews = d.live.filter((e) => myApps.includes(e.m.pkg) && !ws.includes(e.signer));
  const gapOf = (r) => { const first = (d.replies[r.id] || []).filter((x) => ws.includes(x.signer) && x.t >= r.t).sort((a, b) => a.t - b.t)[0]; return first ? (first.t - r.t) / 3600 : null; };
  const gaps = reviews.map(gapOf).filter((x) => x !== null).sort((a, b) => a - b);
  const median = gaps.length ? (gaps.length % 2 ? gaps[(gaps.length - 1) / 2] : (gaps[gaps.length / 2 - 1] + gaps[gaps.length / 2]) / 2) : null;
  const queue = reviews.filter((r) => gapOf(r) === null).sort((a, b) => a.t - b.t);
  const bucket = (h) => h === null ? "never" : h < 24 ? "hours" : h < 168 ? "days" : h < 720 ? "weeks" : "months";
  const apps = myApps.map((pkg) => { const rs = reviews.filter((e) => e.m.pkg === pkg); const g = rs.map(gapOf).filter((x) => x !== null).sort((a, b) => a - b); return { package: pkg, signed: rs.length, answered: g.length, waiting: rs.length - g.length, medianHours: g.length ? g[Math.floor(g.length / 2)] : null, bucket: rs.length ? bucket(g.length ? g[Math.floor(g.length / 2)] : null) : "no reviews" }; });
  return text({ apps, unanswered: queue.length, medianReplyHours: median, answeredPercent: reviews.length ? Math.round(100 * gaps.length / reviews.length) : null,
    oldestWaiting: queue.slice(0, 20).map((e) => ({ id: e.id, app: e.m.pkg, rating: e.m.rating, tags: e.m.tags, text: e.m.text, by: who(e), signer: e.signer, ageDays: Math.floor((Date.now() / 1000 - e.t) / 86400), at: new Date(e.t * 1000).toISOString() })),
    note: "responsiveness is read from the ledger: the gap between a signed review and your signed reply, as readers see it on your app page. Use reply_handoff to answer from here." });
});

server.tool("store_reviews", "The native dApp Store's PUBLIC rating summary and latest reviews for a package (read-only; Solana Mobile owns these).", { package: z.string(), first: z.number().int().min(1).max(50).default(10) }, async ({ package: pkg, first }) => {
  const s = await store("DAppByPackageSummary", Q_SUMMARY, { systemContext: SC, androidPackage: pkg });
  const r = await store("DAppReviews", Q_REVIEWS, { systemContext: SC, androidPackage: pkg, first });
  return text({ summary: s.dAppByAndroidPackage, reviews: r.dAppReviews.edges.map((e) => e.node) });
});

server.tool("star", "Star or unstar a record id (local to this Mac).", { id: z.string(), on: z.boolean().default(true) }, async ({ id, on }) => { const s = L.stars(); on ? s.add(id) : s.delete(id); L.setStars(s); return text({ starred: [...s] }); });

server.tool("reply_handoff", "Prepare a signed reply on the Seeker: builds an undersigned://reply link with the text, copies it to the clipboard (Universal Clipboard reaches the phone), and returns it. The phone app opens it pre-filled; the owner reviews and double-taps to sign. Nothing is sent from the Mac.", { id: z.string(), text: z.string() }, async ({ id, text: t }) => {
  L.encodeReply(id, t);   // validates
  const link = `undersigned://reply?target=${encodeURIComponent(id)}&text=${L.b64uEnc(t.trim())}`;
  if (process.platform === "darwin") await new Promise((res) => { const p = execFile("pbcopy"); p.stdin.end(link); p.on("exit", res); });
  return text({ link, copiedToClipboard: process.platform === "darwin", next: "Open the link on the Seeker (paste into Chrome or tap it from Notes); Undersigned opens the reply pre-filled for you to sign." });
});

server.tool("action_handoff", "Prepare a helpful vote, a flag or a dispute to sign on the Seeker: returns an undersigned:// link (copied to the clipboard). kind = up | unv | flg | unf | clr | dsp; reason for flg = spam|abuse|offtopic|misleading; text for dsp.", { id: z.string(), kind: z.enum(["up", "unv", "flg", "unf", "clr", "dsp"]), reason: z.enum(["spam", "abuse", "offtopic", "misleading"]).optional(), text: z.string().optional() }, async ({ id, kind, reason, text: t }) => {
  const q = kind === "flg" ? `&reason=${reason || "spam"}` : kind === "dsp" ? `&text=${L.b64uEnc((t || "").trim())}` : "";
  const link = `undersigned://${({ up: "vote", unv: "unvote", flg: "flag", unf: "unflag", clr: "clear", dsp: "dispute" })[kind]}?target=${encodeURIComponent(id)}${q}`;
  if (process.platform === "darwin") await new Promise((res) => { const p = execFile("pbcopy"); p.stdin.end(link); p.on("exit", res); });
  return text({ link, copiedToClipboard: process.platform === "darwin" });
});

server.tool("sign_reply", "Sign and send a reply from THIS machine using the keypair file in UNDERSIGNED_KEYPAIR (a publisher wallet). Not available unless the operator configured that file. A reply from a non-Genesis wallet still shows the Publisher badge if the wallet's claim is verified.", { id: z.string(), text: z.string() }, async ({ id, text: t }) => {
  const kp = process.env.UNDERSIGNED_KEYPAIR; if (!kp || !fs.existsSync(kp)) return text("UNDERSIGNED_KEYPAIR is not set or the file does not exist. Use reply_handoff to sign on the Seeker instead.");
  const web3 = await import("@solana/web3.js");
  const secret = Uint8Array.from(JSON.parse(fs.readFileSync(kp, "utf8"))); const signer = web3.Keypair.fromSecretKey(secret);
  const memo = L.encodeReply(id, t);
  const conn = new web3.Connection(L.RPCS[0], "confirmed");
  const tx = new web3.Transaction();
  tx.add(new web3.TransactionInstruction({ programId: new web3.PublicKey(L.MEMO_PROGRAM), keys: [], data: Buffer.from(memo, "utf8") }));
  tx.add(web3.SystemProgram.transfer({ fromPubkey: signer.publicKey, toPubkey: signer.publicKey, lamports: 0 }));
  tx.instructions[1].keys.push({ pubkey: new web3.PublicKey(L.REGISTRY), isSigner: false, isWritable: false });
  const sim = await conn.simulateTransaction(tx, [signer]); if (sim.value.err) return text({ error: "simulation failed", err: sim.value.err, logs: sim.value.logs });
  const sig = await web3.sendAndConfirmTransaction(conn, tx, [signer], { commitment: "confirmed" });
  return text({ signature: sig, explorer: `https://solscan.io/tx/${sig}`, from: signer.publicKey.toBase58() });
});

server.tool("watch_status", "Whether the real-time watch is running and what it has seen since start.", {}, async () => text(watch));

// ---- real-time watch: logsSubscribe on the registry; macOS notification per new record touching the operator's wallets ----
const watch = { running: false, seen: 0, lastAt: null, errors: 0 };
function startWatch() {
  if (process.env.UNDERSIGNED_WATCH === "0") return;
  const url = (process.env.UNDERSIGNED_WSS || L.RPCS[0].replace(/^http/, "ws"));
  let ws; const connect = () => {
    ws = new WebSocket(url);
    ws.on("open", () => { watch.running = true; ws.send(JSON.stringify({ jsonrpc: "2.0", id: 1, method: "logsSubscribe", params: [{ mentions: [L.REGISTRY] }, { commitment: "confirmed" }] })); });
    ws.on("message", async (buf) => {
      try { const j = JSON.parse(buf.toString()); if (j.method !== "logsNotification") return;
        watch.seen++; watch.lastAt = new Date().toISOString();
        const entries = await L.sync(); const d = L.derive(entries); const newest = entries.filter((e) => Date.now() / 1000 - e.t < 120);
        for (const e of newest) {
          const myApps = Object.entries(d.claims).filter(([, s]) => myWallets.some((w) => s.has(w))).map(([p]) => p);
          if (e.m.t === "rev" && myApps.includes(e.m.pkg) && !myWallets.includes(e.signer)) L.macNotify("Undersigned: new signed review", `${e.m.pkg} · ${e.m.rating}★ · ${e.m.tags.join(", ")}`);
          if (e.m.t === "rep" && d.bySig[e.m.target] && myWallets.includes(d.bySig[e.m.target].signer) && !myWallets.includes(e.signer)) L.macNotify("Undersigned: reply to your record", e.m.text);
          if (e.m.t === "flg" && d.bySig[e.m.target] && myWallets.includes(d.bySig[e.m.target].signer)) L.macNotify("Undersigned: one of your records was flagged", e.m.reason);
        }
      } catch (err) { watch.errors++; }
    });
    ws.on("close", () => { watch.running = false; setTimeout(connect, 5000); });
    ws.on("error", () => { watch.errors++; });
  };
  connect();
}
startWatch();

const transport = new StdioServerTransport();
await server.connect(transport);
