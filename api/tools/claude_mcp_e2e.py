"""End-to-end MCP check: Claude, through Anthropic's MCP connector, uses the hosted Undersigned MCP server.
Key: ANTHROPIC_API_KEY=… in ~/.config/undersigned/anthropic.env (chmod 600). Never commit a key.
Usage: python3 api/tools/claude_mcp_e2e.py [model] [prompt]"""
import json, os, sys, urllib.request, pathlib
env = pathlib.Path.home()/".config/undersigned/anthropic.env"
key = next((l.split("=",1)[1].strip() for l in env.read_text().splitlines() if l.startswith("ANTHROPIC_API_KEY=")), "")
if not key: sys.exit("no key in file")
model = sys.argv[1] if len(sys.argv) > 1 else "claude-haiku-5-5"
prompt = sys.argv[2] if len(sys.argv) > 2 else ("Using the undersigned tools: find the Solflare wallet app, then tell me its signed score with the counted-review count, "
          "the dApp Store's own rating, and which review headings its reviews fall under. Keep it to five lines.")
body = {"model": model, "max_tokens": 900, "messages": [{"role": "user", "content": prompt}],
        "mcp_servers": [{"type": "url", "url": "https://undersigned.provenby.dev/mcp", "name": "undersigned"}]}
req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(body).encode(), method="POST",
      headers={"x-api-key": key, "anthropic-version": "2023-06-01", "anthropic-beta": "mcp-client-2025-04-04",
               "content-type": "application/json", "User-Agent": "undersigned-mcp-e2e/1.0"})
try:
    r = json.load(urllib.request.urlopen(req, timeout=180))
except urllib.error.HTTPError as e:
    sys.exit(f"HTTP {e.code}: {e.read()[:400].decode(errors='replace')}")
for b in r.get("content", []):
    t = b.get("type")
    if t == "mcp_tool_use": print(f"[tool call] {b.get('name')} {json.dumps(b.get('input'))[:160]}")
    elif t == "mcp_tool_result": print(f"[tool result] error={b.get('is_error')} {str(b.get('content'))[:160]}")
    elif t == "text": print("[claude] " + b.get("text", "").strip())
u = r.get("usage", {}); print(f"\nmodel={r.get('model')} stop={r.get('stop_reason')} input_tokens={u.get('input_tokens')} output_tokens={u.get('output_tokens')}")
