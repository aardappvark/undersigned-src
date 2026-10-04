# undersigned-mcp

A Model Context Protocol server so a developer can work the Undersigned ledger from a Mac in Claude Desktop (or any MCP client): see unanswered signed reviews on their apps the moment they land, read profiles and the store's public reviews, star things, and reply — either by handing the reply to the Seeker to sign (the default) or, if they choose, by signing from a local keypair.

```bash
cd mcp && npm install
```

Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{ "mcpServers": { "undersigned": {
  "command": "node", "args": ["/ABSOLUTE/PATH/Undersigned/mcp/server.js"],
  "env": { "UNDERSIGNED_WALLETS": "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD", "UNDERSIGNED_RPC": "https://mainnet.helius-rpc.com/?api-key=…" }
} } }
```

Optional: `UNDERSIGNED_KEYPAIR=/path/to/publisher-keypair.json` enables `sign_reply` from the Mac. Leave it unset to keep every signature on the Seeker. `UNDERSIGNED_WATCH=0` disables the real-time watch (a `logsSubscribe` on the registry that posts macOS notifications for new reviews on your claimed apps, replies to your records, and flags).

Tools (local desk, 11): `ledger_sync`, `inbox`, `app_reviews`, `profile`, `desk`, `store_reviews`, `star`, `reply_handoff`, `action_handoff`, `sign_reply`, `watch_status`.

With `UNDERSIGNED_API_URL` set (for example `http://127.0.0.1:8797`), 23 more tools read the server's index (`mcp/api.js`): `api_search_apps`, `api_app`, `api_reviews`, `api_bursts`, `api_versions`, `api_store_ratings`, `api_app_payments`, `api_wallet`, `api_leaderboard`, `api_store_watch`, `api_changes`, `api_payments_board`, `api_records`, `api_record`, `api_stats`, `api_pricing`, `api_checkout`, `api_redeem_pass`, `api_pass`, `api_hook_create`, `api_hooks`, `api_hook_delete`, `api_badge`. `UNDERSIGNED_API_KEY` holds a pass key. These tools never sign and never pay.

`sign_reply` is for a publisher who chooses to sign from this machine: it is off unless `UNDERSIGNED_KEYPAIR` points at a keypair file.

`reply_handoff` copies an `undersigned://reply?target=…&text=…` link to the clipboard; with Universal Clipboard on, paste it on the Seeker and the app opens the reply pre-filled for the owner to check and sign with the double-tap. The Mac never sends anything in that mode.
