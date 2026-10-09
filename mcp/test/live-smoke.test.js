// Starts server.js over stdio, as Claude Desktop does, and calls every free API tool against the live index.
import { test } from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";

const BASE = process.env.UNDERSIGNED_API_URL || "https://undersigned.provenby.dev";
const PKG = "com.solflare.mobile";

test("every free tool answers from the live API", { timeout: 240_000 }, async () => {
  const client = new Client({ name: "smoke", version: "1" });
  await client.connect(new StdioClientTransport({ command: process.execPath, args: [fileURLToPath(new URL("../server.js", import.meta.url))],
    env: { ...process.env, UNDERSIGNED_API_URL: BASE, UNDERSIGNED_WATCH: "0", UNDERSIGNED_API_KEY: "" } }));
  const tools = (await client.listTools()).tools.map((t) => t.name);
  assert.ok(tools.length >= 50, `${tools.length} tools`);
  const rec = JSON.parse((await client.callTool({ name: "api_records", arguments: { type: "rev", limit: 1 } })).content[0].text).records?.[0]?.id;
  const calls = {
    api_search_apps: { q: "wallet", limit: 3 }, api_apps_batch: { packages: [PKG, "com.nope.zz"] }, api_app: { package: PKG },
    api_reviews: { package: PKG, sort: "helpful", limit: 2 }, api_bursts: { package: PKG }, api_summary: { package: PKG },
    api_scoring_help: { question: "Does staked SKR change a review's weight?" }, api_ask_docs: { question: "How do I sort reviews by newest?" },
    api_wallet: { wallet: "FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF" }, api_leaderboard: { metric: "store_rating", limit: 2 }, api_tags: {},
    api_payments_board: { limit: 2 }, api_found_treasuries: { limit: 2 }, api_found_trends: {}, api_records: { limit: 1 }, api_record: { id: rec },
    api_stats: {}, api_dataset: {}, api_health: {}, api_changelog: {}, api_pricing: {}, api_badge: { package: PKG }, api_pass: {},
  };
  const bad = [];
  for (const [name, args] of Object.entries(calls)) {
    assert.ok(tools.includes(name), `missing tool ${name}`);
    const out = await client.callTool({ name, arguments: args });
    const body = out.content?.[0]?.text ?? "";
    if (out.isError || /"error":/.test(body.slice(0, 200)) || /payment_required|rate_limited/.test(body.slice(0, 200))) bad.push(`${name}: ${body.slice(0, 160)}`);
  }
  // A PASS tool without a key answers with plain buying instructions, never an error to retry.
  const paid = JSON.parse((await client.callTool({ name: "api_store_watch", arguments: {} })).content[0].text);
  assert.ok(paid.payment_required && Array.isArray(paid.accepts), "402 shape");
  await client.close();
  assert.deepEqual(bad, []);
});
