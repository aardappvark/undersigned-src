#!/usr/bin/env python3
"""
Regenerate viewer/ledger.json — the snapshot the public page renders.

Why a snapshot at all, when the point of the page is "read it yourself": as of 20 Sep 2026 every public
Solana RPC refuses a browser read of the registry. Measured from a browser that day:
  api.mainnet-beta.solana.com  403 Access forbidden
  solana-rpc.publicnode.com    200 with an EMPTY signature list (silent, the worst failure mode)
  solana.drpc.org              400 "chain is not available on free plan"
  rpc.ankr.com/solana          403        onfinality 429        omniatech CORS refusal
A page that reads nothing renders an empty ledger and says the project has no reviews, which is worse
than saying where the numbers came from. So the page ships this snapshot, states its age, and offers
to re-read the whole thing live from any endpoint the reader pastes in — the raw memos are kept here,
so the page still decodes and derives every number itself rather than trusting a precomputed answer.

Run:  python3 viewer/refresh.py            (uses HELIUS_API_KEY_3 from ~/.gradle/gradle.properties)
      python3 viewer/refresh.py --rpc URL
"""
import json, os, re, sys, time, urllib.request, pathlib

REG = "BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs"
TREASURY = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
PAYMENT_MEMO = "UNS"
PRO_FLOOR_LAMPORTS, PRO_FLOOR_SKR = 60_700_000, 276_000_000
LIGHTHOUSE = "L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95"
COMPUTE = "ComputeBudget111111111111111111111111111111"

def endpoint():
    for i, a in enumerate(sys.argv):
        if a == "--rpc" and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    p = pathlib.Path.home() / ".gradle" / "gradle.properties"
    if p.exists():
        m = re.search(r"^HELIUS_API_KEY_3=(.+)$", p.read_text(), re.M)
        if m:
            return f"https://mainnet.helius-rpc.com/?api-key={m.group(1).strip()}"
    return "https://api.mainnet-beta.solana.com"

URL = endpoint()

def rpc(method, params):
    req = urllib.request.Request(URL, data=json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode(),
                                 headers={"Content-Type": "application/json"})
    r = json.load(urllib.request.urlopen(req, timeout=40))
    if "error" in r:
        raise SystemExit(f"RPC error: {r['error']}")
    return r["result"]

def main():
    sigs, before, rows = [], None, []
    while True:
        cfg = {"limit": 1000, "commitment": "confirmed"}
        if before:
            cfg["before"] = before
        page = rpc("getSignaturesForAddress", [REG, cfg])
        if not page:
            break
        sigs += page
        if len(page) < 1000:
            break
        before = page[-1]["signature"]
    # Oldest first, so the page's own ordering work is the same as the phone's.
    for s in reversed(sigs):
        if s.get("err") or not s.get("memo") or not ("SRL1|" in s["memo"] or PAYMENT_MEMO in s["memo"]):
            continue
        tx = rpc("getTransaction", [s["signature"], {"encoding": "jsonParsed", "maxSupportedTransactionVersion": 0, "commitment": "confirmed"}])
        if not tx or (tx.get("meta") or {}).get("err"):
            continue
        msg = tx["transaction"]["message"]
        keys = [k.get("pubkey", k) if isinstance(k, dict) else k for k in msg["accountKeys"]]
        ixs = msg.get("instructions", [])
        memos = [ix["parsed"] for ix in ixs if ix.get("program") == "spl-memo" and isinstance(ix.get("parsed"), str)]
        if not memos:
            continue
        # What this transaction credited the treasury, so the page can recognise a Pro payment (a licence is
        # the payment; its only memo is the fleet code) without trusting anything precomputed about the term.
        meta = tx.get("meta") or {}
        ti = keys.index(TREASURY) if TREASURY in keys else -1
        paid_sol = (meta["postBalances"][ti] - meta["preBalances"][ti]) if ti >= 0 else 0
        def _skr(arr):
            return sum(int(b["uiTokenAmount"]["amount"]) for b in (arr or [])
                       if b.get("owner") == TREASURY and b.get("mint") == SKR_MINT)
        paid_skr = _skr(meta.get("postTokenBalances")) - _skr(meta.get("preTokenBalances"))
        rows.append({
            "paidSol": max(paid_sol, 0), "paidSkr": max(paid_skr, 0),
            "sig": s["signature"], "signer": keys[0], "t": tx.get("blockTime") or s.get("blockTime"),
            "seeker": any(i.get("programId") == LIGHTHOUSE for i in ixs) and any(i.get("programId") == COMPUTE for i in ixs),
            "fee": (tx.get("meta") or {}).get("fee", 0), "memos": memos,
        })
    out = {"registry": REG, "generatedAt": int(time.time()), "transactions": len(sigs), "records": rows}
    path = pathlib.Path(__file__).with_name("ledger.json")
    path.write_text(json.dumps(out, separators=(",", ":")))
    print(f"wrote {path} — {len(rows)} carrier transactions, {sum(len(r['memos']) for r in rows)} records, from {len(sigs)} signatures")

main()
