# Building on Undersigned

Undersigned has a public HTTP API and an MCP server for AI agents. Both read the same index of the on-chain ledger. The
API's own description is at [`/v1/openapi.json`](https://undersigned.provenby.dev/v1/openapi.json), and the human page is
[undersigned.provenby.dev/api](https://undersigned.provenby.dev/api). Field-by-field detail is in [API.md](API.md).

Base URL: `https://undersigned.provenby.dev`

## 1. Call it

You don't need a key or a sign-up to start. Every answer is JSON; add `?pretty=1` to read it.

```bash
curl "https://undersigned.provenby.dev/v1/apps?q=wallet&limit=5"
curl "https://undersigned.provenby.dev/v1/apps/com.solflare.mobile"
curl "https://undersigned.provenby.dev/v1/apps/com.solflare.mobile/reviews?sort=newest"
```

```js
const app = await (await fetch("https://undersigned.provenby.dev/v1/apps/com.solflare.mobile")).json();
console.log(app.signed.mean, app.signed.counted_reviews, app.store_rating);
```

```python
import requests
app = requests.get("https://undersigned.provenby.dev/v1/apps/com.solflare.mobile").json()
print(app["signed"]["mean"], app["signed"]["counted_reviews"])
```

From scripts, send a `User-Agent` header (`requests`, `fetch`, axios, Go and curl all do by default).

## 2. What you get

| Free, no key | Free within limits (20 rows) | With a pass |
|---|---|---|
| `/v1/apps/{pkg}`: signed score and count, Pro members' and promoted scores shown apart, the store's own rating, publisher proof, payments | `/v1/apps`: catalogue search | `/v1/watch`: store-wide rating bursts |
| `/v1/apps/{pkg}/bursts`: review and rating bursts | `/v1/apps/{pkg}/reviews`: every signal per review, `sort=newest\|helpful\|skr\|sol` | `/v1/changes`: version and certificate changes |
| `/v1/dataset.json`: the whole ledger with scores | `/v1/records`: decoded records, filterable | `/v1/apps/{pkg}/versions`, `/similar`, `/store-ratings`, `/payments` |
| `/v1/records/{id}`, `/v1/stats`, `/v1/tags`, `/v1/health` | `/v1/wallets/{address}`, `/v1/leaderboard`, `/v1/payments` | `/v1/hooks`: signed webhooks (review, reply, store_note, flag, claim, burst, version, certificate) |

Answers:
- **200**: the data.
- **402**: needs a pass. The body carries the price and Solana Pay links, so an agent can act on it.
- **429**: over the limit. A `Retry-After` header says when to try again.
- **404**: unknown.

How the numbers are made:
- One voice per attested device (Seeker Genesis Token). Each review is weighted only by what it carries: install time, usage, a written note.
- No payment and no token balance changes a score.
- Stake is shown beside each review for transparency and never weighed.
- Bursts and similarity are facts about timing and wording, not verdicts.
- The rules are in the [Review Integrity Policy](https://aardappvark.github.io/undersigned-legal/integrity.html).

## 3. Limits, the pass, and pay per call

| | Free | Pass | Pay per call (agents) |
|---|---|---|---|
| Requests | 30 a minute, 1,000 a day per IP | 100,000 over 30 days, 600 a minute | as many calls as you paid for, valid a year, 600 a minute |
| List rows | 20 (leaderboard 10) | up to 500, with paging | up to 500, with paging |
| Endpoints | free and limited | all, including webhooks | all, including webhooks |
| Price | free | 0.0607 SOL, or 369 SKR (332 SKR with a Seeker Genesis Token) | 0.00001 SOL or 0.06 SKR a call, from 0.001 SOL or 6 SKR |

SKR prices are 10% below SOL at the rate on the day they were set. The live figures are always at
[`/v1/pricing`](https://undersigned.provenby.dev/v1/pricing). Every metered answer carries:
- `X-RateLimit-Limit`, `X-RateLimit-Remaining` and `X-RateLimit-Reset`.
- For keys, `X-Pass-Requests-Left` and `X-Pass-Ends-At`.

To get a key:

1. **Checkout.** `POST /v1/checkout` returns a secret and ready Solana Pay URLs. For per-call credits, send `{"calls": N}` instead. The payment carries the API reference and memo `UNS:<commitment>`.
2. **Pay** one of the URLs from any wallet. The payment goes straight to the treasury.
3. **Redeem.** `POST /v1/keys` with `{"signature": "<payment tx>", "secret": "<from checkout>"}`. What you get depends on the amount:
   - the pass price or more buys a pass;
   - less buys calls at the per-call rate;
   - paying again tops up the same pass or credits.
4. **Call** with `Authorization: Bearer <key>`. `GET /v1/keys/me` shows what is left.

When a call needs a key, the 402 answer carries the pricing and an `accepts` list: both ways to pay, in the shape x402 uses. An agent can pay and retry without reading these docs.

Payers are screened against sanctions lists. If the screen cannot run, no key is issued until it can.

### Webhooks

`POST /v1/hooks {url, packages[], events[]}` creates a webhook (https only). Each delivery carries:
- **`X-Undersigned-Signature-V2: t=<unix>,v1=<hex HMAC-SHA256 of "<t>.<body>">`.** Reject a `t` more than 5 minutes old, so a captured delivery can't be replayed.
- **`X-Undersigned-Signature: sha256=<hex HMAC of the body>`,** for receivers built earlier.

Delivery:
- **Retries.** A receiver that is down or fails is retried six times over about 24 hours.
- **Duplicates.** Events carry ids, so drop repeats.
- **Testing.** `POST /v1/hooks/{id}/test` sends a signed ping now. `GET /v1/hooks/{id}/deliveries` lists the last 100 attempts with the receiver's status.

## 4. AI agents (MCP)

The API is also a [Model Context Protocol](https://modelcontextprotocol.io) server. You don't install a package; add the URL.

- **Claude Code**

  ```bash
  claude mcp add --transport http undersigned https://undersigned.provenby.dev/mcp
  ```

- **Cursor and other clients** (`mcp.json`). The `headers` line is only needed for the pass-only tools.

  ```json
  {
    "mcpServers": {
      "undersigned": {
        "url": "https://undersigned.provenby.dev/mcp",
        "headers": { "Authorization": "Bearer YOUR_KEY" }
      }
    }
  }
  ```

- **Claude (web or desktop)**: Settings → Connectors → add a custom connector with `https://undersigned.provenby.dev/mcp`.

Everything the server offers is read-only:

| Free | With a pass |
|---|---|
| `search_apps`, `get_app`, `get_reviews` (`sort` newest / helpful / staked SKR / staked SOL; `offset` with a pass), `get_bursts`, `get_wallet`, `get_record`, `list_records`, `leaderboard`, `payments_board`, `ledger_stats`, `review_tags`, `get_pricing` | `store_watch`, `app_changes`, `version_scores`, `similar_reviews`, `store_rating_history` |

Besides tools:

- **Resources:** `undersigned://policy` (the integrity rules, for the model to read before describing a score), `undersigned://pricing`, `undersigned://tags`, and the template `undersigned://app/{package}`.
- **Prompts:** `audit_app_reputation(package)` and `compare_apps(package_a, package_b)`. Both tell the model to quote counts and to treat bursts as facts, not verdicts.
- **Discovery:** [`/llms.txt`](https://undersigned.provenby.dev/llms.txt) and [`/.well-known/mcp/server-card.json`](https://undersigned.provenby.dev/.well-known/mcp/server-card.json).

How the server works:

- **Read-only by design.** No tool drafts, signs or submits a review, flag or vote. A review is a person's own signed opinion from their phone.
- **Transport.** Stateless Streamable HTTP: one JSON-RPC message per `POST /mcp`. Batches are refused, so every call is metered. Protocol versions `2025-06-18`, `2025-03-26` and `2024-11-05` are supported.

### Publishers: the local MCP server

The repository also ships a local stdio server for publishers (`mcp/`, Node 18+). Run it on your own machine:

```bash
cd mcp && npm install
UNDERSIGNED_API_URL=https://undersigned.provenby.dev node server.js
```

It gives your agent a publisher desk:

- **Inbox and watch.** New reviews of your apps and replies to them, with live watch and macOS notifications.
- **Profiles.** The store's reviews beside the signed ones.
- **Reply and action hand-offs.** These return an `undersigned://` link that opens on your Seeker for you to sign. Nothing is signed for you.
- **`sign_reply`.** Signs your own reply locally, and only if you set `UNDERSIGNED_KEYPAIR`.
- **The API as tools.** All of it, including pass checkout and webhooks.

Raw check:

```bash
curl -s https://undersigned.provenby.dev/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_app","arguments":{"package":"com.solflare.mobile"}}}'
```

## 5. Writing reviews (people, not agents)

A review is a memo transaction signed in the reviewer's own wallet. The API only builds the unsigned transaction, and the wallet signs and sends it:
- **Web wallets:** `POST /v1/tx/review`.
- **Phone wallets:** `/v1/pay/review` (Solana Pay transaction request).

Only wallets holding a Seeker Genesis Token count, one voice per device. The record format is in [SPEC.md](SPEC.md).

## 6. Data licence

The ledger is public on-chain data: use it freely. Store data shown alongside it (catalogue, star ratings, store reviews) belongs to the Solana dApp Store.
