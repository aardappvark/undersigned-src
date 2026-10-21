# @aardappvark/undersigned-mcp

A Model Context Protocol server so a developer can work the Undersigned ledger from a Mac in Claude Desktop (or any MCP client): see unanswered signed reviews on their apps the moment they land, read profiles and the store's public reviews, star things, and reply — either by handing the reply to the Seeker to sign (the default) or, if they choose, by signing from a local keypair.

Runs straight from npm (Node 18+), nothing to clone:

```bash
npx -y @aardappvark/undersigned-mcp
```

Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{ "mcpServers": { "undersigned": {
  "command": "npx", "args": ["-y", "@aardappvark/undersigned-mcp"],
  "env": { "UNDERSIGNED_WALLETS": "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD", "UNDERSIGNED_RPC": "https://mainnet.helius-rpc.com/?api-key=…" }
} } }
```

Optional: `UNDERSIGNED_KEYPAIR=/path/to/publisher-keypair.json` enables `sign_reply` from the Mac. Leave it unset to keep every signature on the Seeker. `UNDERSIGNED_WATCH=0` disables the real-time watch (a `logsSubscribe` on the registry that posts macOS notifications for new reviews on your claimed apps, replies to your records, and flags).

Tools (local desk, 11): `ledger_sync`, `inbox`, `app_reviews`, `profile`, `desk`, `store_reviews`, `star`, `reply_handoff`, `action_handoff`, `sign_reply`, `watch_status`.

API tools (42, `mcp/api.js`): every route of the Undersigned API, on by default against `https://undersigned.provenby.dev`. Set `UNDERSIGNED_API_URL` to point elsewhere (for example `http://127.0.0.1:8817`) or to `off` to leave them out. `UNDERSIGNED_API_KEY` holds a pass or per-call key; `UNDERSIGNED_PRO_WALLET` (else the first of `UNDERSIGNED_WALLETS`) is used for the Pro-wallet AI tools.

- Apps and reviews: `api_search_apps`, `api_apps_batch`, `api_app`, `api_reviews` (sort newest, helpful, skr, sol), `api_bursts`, `api_summary`, `api_versions`, `api_similar`, `api_store_ratings`.
- AI reading aids, each labelled a suggestion: `api_translate_record`, `api_translate_text`, `api_translate_reply` (a reply draft into the reviewer's language; never signs), `api_scoring_help`, `api_ask_docs`, `api_digest`.
- Store, wallets, rankings: `api_wallet`, `api_leaderboard`, `api_store_watch`, `api_changes`, `api_tags`.
- Payments: `api_payments_board`, `api_app_payments`, `api_found_treasuries`, `api_found_analytics`, `api_found_trends` (wallets found in app code are never mixed with confirmed treasuries).
- Ledger: `api_records`, `api_record`, `api_stats`, `api_dataset`, `api_health`, `api_changelog`.
- Keys: `api_pricing`, `api_checkout` (a pass, or `calls` for per-call credits), `api_redeem_pass`, `api_pass`.
- Webhooks: `api_hook_create`, `api_hooks`, `api_hook_test`, `api_hook_deliveries`, `api_hook_delete`, and `api_verify_webhook` (checks a delivery's signature locally, with the 5-minute replay window).
- Embeds: `api_badge`.

These tools never sign and never pay. A tool that needs a key answers with how to buy one (the `accepts` list), never an error to retry.

Tests: `npm test` runs the webhook checks, a coverage check that every API route has a tool (against the live OpenAPI spec), and a live smoke test that starts the server over stdio and calls every free tool.

`sign_reply` is for a publisher who chooses to sign from this machine: it is off unless `UNDERSIGNED_KEYPAIR` points at a keypair file. It also needs `@solana/web3.js`, an optional peer dependency since 0.2.1 (`npm install @solana/web3.js`); nothing else uses it, so a plain install stays small.

`reply_handoff` copies an `undersigned://reply?target=…&text=…` link to the clipboard; with Universal Clipboard on, paste it on the Seeker and the app opens the reply pre-filled for the owner to check and sign with the double-tap. The Mac never sends anything in that mode.

## Licence

MIT. See [LICENSE](LICENSE).
