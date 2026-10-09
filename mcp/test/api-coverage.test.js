// Every route the API describes has a tool here, so the local desk never falls behind the API again (it had 23 of 46).
import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const BASE = process.env.UNDERSIGNED_API_URL || "https://undersigned.provenby.dev";
// Not for agents: the MCP endpoint itself, the spec, unsigned-transaction builders for wallets, and the Solana Pay request.
const NOT_TOOLS = new Set(["/mcp", "/v1/openapi.json", "/v1/tx/review", "/v1/tx/record", "/v1/tx/seed", "/v1/pay/review"]);

test("every API path is called by a tool in api.js", async () => {
  const spec = await (await fetch(`${BASE}/v1/openapi.json`, { headers: { "user-agent": "undersigned-mcp-test" } })).json();
  const src = fs.readFileSync(new URL("../api.js", import.meta.url), "utf8");
  const missing = Object.keys(spec.paths).filter((p) => !NOT_TOOLS.has(p)).filter((p) => {
    const stem = p.replace(/\{[^}]+\}.*$/, "");               // "/v1/apps/{pkg}/reviews" -> "/v1/apps/"
    const tail = p.includes("}") ? p.slice(p.lastIndexOf("}") + 1) : "";   // -> "/reviews"
    const called = ["`", '"', "}"].some((c) => src.includes(c + stem));   // a literal path, or after ${base}
    return !called || (tail && !src.includes(tail));
  });
  assert.deepEqual(missing, []);
});
