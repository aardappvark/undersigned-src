"""What the review layer would bring in if the dApp Store ran it, at Undersigned's own prices (owner, 10 Oct 2026).

Inputs are measured where they can be (on-chain and from the store's public feed), and every adoption rate is an
ASSUMPTION, stated in the table. Run: python3 tools/revenue_model.py  (prints the markdown used in docs/REVENUE_MODEL.md)
"""

# ---- Measured (10 Oct 2026) -------------------------------------------------------------------------------------
GENESIS_TOKENS = 121_161        # Seeker Genesis Token group size, read on-chain (GT22s89n…, tokenGroup.size), 10 Oct
GENESIS_18_SEP = 120_915        # same read, 18 Sep: +246 in 22 days
DEVICES_SHIPPED = 200_000       # Solana Mobile's own claim, March 2026 ("200K+ devices"); not independently confirmed
# ---- Historical: what store treasuries actually received, last 365 days (server pay_rows, 10 Oct 2026) -----------
HIST_TREASURIES = 63            # wallets watched: 14 proven fleet references + 49 found in app code (unconfirmed)
HIST_USD_YEAR = 14_750          # all payments of 0.001 SOL / 1 SKR / 0.10 USDC or more into them, at 10 Oct prices
HIST_TOP_USD = 3_240            # the best-paid single wallet; its distinct payers: 13
HIST_MAX_PAYERS = 23            # the most distinct payers any one watched wallet had in the year
APPS = 1_543                    # dApps in the store catalogue, 9 Oct
STORE_RATINGS = 478_289         # star ratings across 1,475 rated dApps, store snapshot 9 Oct
RATINGS_PER_DAY = (452_062 - 450_210) / 9   # same ~1,450 apps, 28 Sep -> 7 Oct snapshots
SOL_USD = 109.57                # 9 Oct, price history held by the server
# ---- Undersigned's prices (api Config.kt, Indexer.kt) ------------------------------------------------------------
UNIT_SOL = 0.0607               # Pro (12 months), publisher treasury listing, API pass (30 days)
PROMOTE_SOL = 0.00607           # one promoted review
PER_CALL_SOL = 0.00001          # one API/MCP call on credits
# ---- Running costs per year (measured or list price) -------------------------------------------------------------
HOSTING_USD = 7.09 * 12         # Hetzner CX23
RPC_USD = 49 * 12               # a keyed RPC plan at scale (Helius Developer, list price 18 Sep); free tier today
RELAY_USD = 300 * 365 * 10_000 / 1e9 * SOL_USD   # quick reviews at the daily cap, 10,000 lamports each
AI_USD = 50                     # Claude answers under the daily allowances; measured $0.00003 an answer

SCEN = ["low", "mid", "high"]
# ---- Assumptions (not data) --------------------------------------------------------------------------------------
A = {
    "owner_pro_share":      (0.01, 0.03, 0.05),     # of Genesis holders buying Pro for a year
    "publisher_share":      (0.10, 0.25, 0.50),     # of listed dApps whose publisher lists a treasury AND takes Pro
    "promoted_share":       (0.01, 0.02, 0.05),     # of a year's new reviews promoted by their own authors
    "api_passes_month":     (5, 20, 50),            # 30-day API passes active each month
    "agent_calls_year":     (1_000_000, 5_000_000, 20_000_000),  # paid API/MCP calls
}


def year(genesis, apps, reviews_year):
    out = {}
    for i, s in enumerate(SCEN):
        sol = {
            "Owner Pro": genesis * A["owner_pro_share"][i] * UNIT_SOL,
            "Publisher listing + Pro": apps * A["publisher_share"][i] * UNIT_SOL * 2,
            "Promoted reviews": reviews_year * A["promoted_share"][i] * PROMOTE_SOL,
            "API passes": A["api_passes_month"][i] * 12 * UNIT_SOL,
            "Agent calls (API/MCP)": A["agent_calls_year"][i] * PER_CALL_SOL,
        }
        out[s] = sol
    return out


def table(title, res):
    lines = [f"**{title}**", "", "| Stream | Low | Mid | High |", "|---|---:|---:|---:|"]
    for k in res["low"]:
        lines.append(f"| {k} | " + " | ".join(f"{res[s][k]:,.0f} SOL (${res[s][k] * SOL_USD:,.0f})" for s in SCEN) + " |")
    tot = {s: sum(res[s].values()) for s in SCEN}
    lines.append("| **Total** | " + " | ".join(f"**{tot[s]:,.0f} SOL (${tot[s] * SOL_USD:,.0f})**" for s in SCEN) + " |")
    return "\n".join(lines), tot


if __name__ == "__main__":
    reviews_year = RATINGS_PER_DAY * 365
    past, tp = table("Last 12 months, had it run at today's scale", year(GENESIS_TOKENS, APPS, reviews_year))
    # Next year: the measured device growth (+11 a day) and adoption one step further along (low->mid, mid->high, high x1.5).
    g = GENESIS_TOKENS + (GENESIS_TOKENS - GENESIS_18_SEP) / 22 * 365
    nxt_raw = year(g, APPS * 1.2, reviews_year * 1.2)
    nxt = {"low": nxt_raw["mid"], "mid": nxt_raw["high"], "high": {k: v * 1.5 for k, v in nxt_raw["high"].items()}}
    nxt_t, tn = table("Next 12 months: measured device growth, 20% more dApps and reviews, adoption one step on", nxt)
    # Pro alone, by device base and share (the owner's target is 10-20% of devices shipped).
    print("**Owner Pro alone, per year, by base and share** (0.0607 SOL each)\n")
    shares = [0.001, 0.01, 0.03, 0.10, 0.20]
    print("| Base | " + " | ".join(f"{x:.1%}" for x in shares) + " |"); print("|---|" + "---:|" * len(shares))
    for name, base in (("Genesis Tokens on-chain (121,161)", GENESIS_TOKENS), ("Devices shipped, Solana Mobile's claim (200,000)", DEVICES_SHIPPED)):
        print(f"| {name} | " + " | ".join(f"{base * x:,.0f} buyers · ${base * x * UNIT_SOL * SOL_USD:,.0f}" for x in shares) + " |")
    print(f"\nHistorical check: the {HIST_TREASURIES} store treasuries watched received about ${HIST_USD_YEAR:,} in the last 365 days "
          f"all together; the best-paid one ${HIST_TOP_USD:,}; no one wallet had more than {HIST_MAX_PAYERS} distinct payers.\n")
    cost = HOSTING_USD + RPC_USD + RELAY_USD + AI_USD
    print(past, "\n\n", nxt_t, sep="")
    print(f"\nRunning costs a year: about ${cost:,.0f} (hosting ${HOSTING_USD:,.0f}, keyed RPC ${RPC_USD:,.0f}, "
          f"quick-review fees at the cap ${RELAY_USD:,.0f}, AI ${AI_USD:,.0f}).")
    print(f"Inputs: {GENESIS_TOKENS:,} Genesis Tokens; {APPS:,} dApps; {STORE_RATINGS:,} store ratings, "
          f"{RATINGS_PER_DAY:,.0f} new a day ({reviews_year:,.0f} a year); SOL ${SOL_USD}; next-year devices {g:,.0f}.")
