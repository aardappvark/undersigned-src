// Renders assets/video/motion/motion.html to MP4, frame by frame, with no packages: headless Chrome over the DevTools
// protocol (Node's built-in WebSocket) and ffmpeg. The page's draw(t) sets every element for time t, so frames are exact.
//   node tools/render_motion.mjs [out.mp4] [--fps 30] [--from 0] [--to <end>] [--scale 1]
// One MP4 for the whole timeline, plus one per scene with --scenes (motion-s1.mp4 …) for cutting into the edit.
import { spawn } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const out = resolve(args.find((a) => a.endsWith(".mp4")) ?? join(root, "assets/video/motion/undersigned-motion.mp4"));
const fps = +opt("--fps", 30), scale = +opt("--scale", 1), perScene = args.includes("--scenes");
const page = pathToFileURL(join(root, "assets/video/motion/motion.html")).href + "?render";
const CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

const profile = mkdtempSync(join(tmpdir(), "motion-"));
const chrome = spawn(CHROME, ["--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files", "--remote-debugging-port=0",
  `--user-data-dir=${profile}`, "--window-size=1920,1080", "--force-device-scale-factor=1", "about:blank"], { stdio: ["ignore", "ignore", "pipe"] });
const wsUrl = await new Promise((ok, no) => { let buf = ""; chrome.stderr.on("data", (d) => { buf += d; const m = buf.match(/ws:\/\/[^\s]+/); if (m) ok(m[0]); }); setTimeout(() => no(new Error("chrome did not start")), 15000); });

// Browser endpoint -> open a target -> attach.
let id = 0; const pending = new Map();
const ws = new WebSocket(wsUrl); await new Promise((r) => ws.addEventListener("open", r));
ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { const p = pending.get(m.id); pending.delete(m.id); m.error ? p.no(new Error(m.error.message)) : p.ok(m.result); } });
const send = (method, params = {}, sessionId) => new Promise((ok, no) => { const i = ++id; pending.set(i, { ok, no }); ws.send(JSON.stringify({ id: i, method, params, sessionId })); });
const { targetId } = await send("Target.createTarget", { url: "about:blank" });
const { sessionId } = await send("Target.attachToTarget", { targetId, flatten: true });
const S = (m, p) => send(m, p, sessionId);
await S("Page.enable");
await S("Emulation.setDeviceMetricsOverride", { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
await S("Page.navigate", { url: page });
const evalJs = async (expr) => (await S("Runtime.evaluate", { expression: expr, awaitPromise: true, returnByValue: true })).result.value;
for (let i = 0; i < 100 && !(await evalJs("typeof window.draw === 'function' && document.fonts.status === 'loaded'")); i++) await new Promise((r) => setTimeout(r, 100));
await evalJs("document.fonts.ready.then(() => true)");
await evalJs("Promise.all([...document.images].map(i => i.complete ? 1 : new Promise(r => { i.onload = i.onerror = r; })))");
const duration = await evalJs("window.DURATION"), scenes = await evalJs("window.SCENES");

async function render(file, from, to) {
  const w = Math.round(1920 * scale), h = Math.round(1080 * scale);
  const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(fps), "-c:v", "png", "-i", "-",
    ...(scale !== 1 ? ["-vf", `scale=${w}:${h}`] : []), "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "16", "-preset", "slow", "-movflags", "+faststart", file], { stdio: ["pipe", "inherit", "inherit"] });
  const frames = Math.round((to - from) * fps);
  for (let f = 0; f < frames; f++) {
    await evalJs(`draw(${from + f / fps}); new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))`);
    const { data } = await S("Page.captureScreenshot", { format: "png", clip: { x: 0, y: 0, width: 1920, height: 1080, scale: 1 } });
    if (!ff.stdin.write(Buffer.from(data, "base64"))) await new Promise((r) => ff.stdin.once("drain", r));
    if (f % (fps * 5) === 0) process.stdout.write(`\r${file.split("/").pop()}: ${(f / fps).toFixed(0)}s of ${(to - from).toFixed(0)}s`);
  }
  ff.stdin.end(); await new Promise((r) => ff.on("close", r)); process.stdout.write(`\r${file} done (${frames} frames)\n`);
}

try {
  if (perScene) for (const [sid, s, d] of scenes) await render(out.replace(/\.mp4$/, `-${sid}.mp4`), s, s + d);
  else await render(out, +opt("--from", 0), +opt("--to", duration));
} finally {
  ws.close(); chrome.kill();
  await new Promise((r) => { chrome.once("exit", r); setTimeout(r, 5000); });   // Chrome writes its profile until it exits
  try { rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }); } catch {}
}
