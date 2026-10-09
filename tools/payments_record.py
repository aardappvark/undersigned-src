"""The historical record for docs/FINANCIAL_CASE.md: what wallets written into dApp Store apps actually received.

Reads only Undersigned's PUBLIC API (anyone can re-run it): /v1/payments/found/trends for the monthly series and
coverage, /v1/payments/found for the wallets, /v1/payments/found/{package} for each wallet's analytics.
Run: python3 -I tools/payments_record.py  (paced to the free tier: about two minutes)

What it is: payments of SOL, SKR or USDC INTO wallets found in apps' store builds, USD on the day paid. What it is not:
a confirmed treasury list, or an app's full income. Trading apps (Jupiter, Phantom and the like) earn through swap fees
that never reach such a wallet, so they are missing by construction.
"""
import json, os, time, urllib.request, datetime

BASE = "https://undersigned.provenby.dev"
UA = {"User-Agent": "undersigned-financial-case/1.0"}
# Without a key the found list returns 20 rows (the free tier); a pass or credits key (UNDERSIGNED_API_KEY) returns all.
if os.environ.get("UNDERSIGNED_API_KEY"):
    UA["Authorization"] = "Bearer " + os.environ["UNDERSIGNED_API_KEY"]


def get(path):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(BASE + path, headers=UA), timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(20 * (attempt + 1)); continue
            raise
    raise RuntimeError("rate limited: " + path)


def main():
    today = datetime.date.today()
    first = (today.replace(day=1) - datetime.timedelta(days=365)).replace(day=1).strftime("%Y-%m")
    trends = get("/v1/payments/found/trends")
    listed, read = trends["transactions_listed"], trends["transactions_read"]
    months = [m for m in trends["monthly"] if m["month"] >= first]
    found = get("/v1/payments/found?limit=200")
    rows = found["rows"]
    prog = found.get("progress", {})
    if len(rows) < found.get("total", len(rows)):
        print(f"WARNING: {len(rows)} of {found['total']} found apps returned (no key?); per-wallet figures are partial.\n")
    wallets = []
    for r in rows:
        time.sleep(2.1)
        a = get("/v1/payments/found/" + r["package"])
        for w in a.get("wallets", []):
            year = [m for m in w.get("monthly", []) if m["month"] >= first]
            wallets.append({
                "name": a.get("name") or r["package"], "usd_year": sum(m.get("usd", 0) for m in year),
                "payments_year": sum(m.get("payments", 0) for m in year), "payers": w.get("payers", 0),
                "repeat": w.get("repeat_payers", 0), "median": w.get("median_payment_usd", 0),
                "complete": w.get("coverage", {}).get("history_complete", False)})
    wallets.sort(key=lambda w: -w["usd_year"])
    total = sum(m["usd"] for m in months)
    out = []
    out.append(f"Measured {today.isoformat()} from the public API. Coverage: {read:,} of {listed:,} listed transactions read "
               f"({read / max(1, listed):.0%}); {prog.get('builds_read', 0):,} store builds read and "
               f"{prog.get('builds_skipped', 0):,} skipped; {len(wallets)} wallets kept. Figures below are a floor while "
               "coverage is under 100%.\n")
    out.append("| Month | Payments | Payers | Wallets paid | USD when paid |")
    out.append("|---|---:|---:|---:|---:|")
    for m in months:
        out.append(f"| {m['month']} | {m['payments']:,} | {m['payers']:,} | {m['wallets_paid']} | ${m['usd']:,.0f} |")
    out.append(f"| **12 months + this month** | **{sum(m['payments'] for m in months):,}** | | | **${total:,.0f}** |\n")
    top5 = sum(w["usd_year"] for w in wallets[:5])
    paid = [w for w in wallets if w["usd_year"] > 0]
    out.append("Gross receipts, not revenue: a game's or a market's wallet can take deposits it later pays back out; "
               "outflows are not netted here.\n")
    out.append(f"Concentration: the top 5 wallets took {top5 / max(1, total):.0%} of it. Wallets paid at all in the period: {len(paid)}. "
               f"Median single payment across wallets: ${sorted(w['median'] for w in paid)[len(paid) // 2] if paid else 0:,.2f}. "
               f"Wallets whose full history is read: {sum(w['complete'] for w in wallets)} of {len(wallets)}.\n")
    # Ranked, not named: these wallets are found in app code, not confirmed as anyone's treasury.
    out.append("| Rank | USD, period | Payments | Payers (all time) | Repeat payers |")
    out.append("|---|---:|---:|---:|---:|")
    for i, w in enumerate(wallets[:10], 1):
        out.append(f"| #{i} | ${w['usd_year']:,.0f} | {w['payments_year']:,} | {w['payers']:,} | {w['repeat']:,} |")
    print("\n".join(out))


if __name__ == "__main__":
    main()
