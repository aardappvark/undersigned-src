#!/usr/bin/env python3
"""Ship every dApp's icon inside the app (owner, 8 Oct 2026: icons "should be loaded and saved on the device").

The store's icons average ~330 KB (513 MB for 1,539 apps, measured 8 Oct 2026): on roaming LTE a screenful is
megabytes, and a 64 MB image cache holds ~190 of them. So each icon is fetched once here, cut to 128 px WebP
(~2.7 KB; 44 dp at 480 dpi is 132 px) and written to app/src/main/assets/icons/<package>.webp, with
icons/index.tsv mapping package -> the address it was made from. The app shows the shipped icon unless the
store's address has since changed.

Run after refreshing catalogue_snapshot.json:  python3 tools/bundle_icons.py [--cache DIR]
Needs ImageMagick (`magick`). Downloads are cached in DIR (default build/icon-cache) and only fetched once.
"""
import argparse, concurrent.futures as cf, json, os, pathlib, subprocess, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
SNAPSHOT = ROOT / "app/src/main/assets/catalogue_snapshot.json"
OUT = ROOT / "app/src/main/assets/icons"
SIZE = 128


def fetch(url: str, dest: pathlib.Path) -> bool:
    if dest.exists() and dest.stat().st_size > 0:
        return True
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Undersigned icon bundler"})
        with urllib.request.urlopen(req, timeout=30) as r:  # follows redirects (turbo-gateway answers 302)
            dest.write_bytes(r.read())
        return True
    except Exception as e:
        print(f"  fetch failed {url}: {e}")
        return False


def convert(src: pathlib.Path, dest: pathlib.Path) -> bool:
    r = subprocess.run(["magick", f"{src}[0]", "-resize", f"{SIZE}x{SIZE}^", "-gravity", "center", "-extent", f"{SIZE}x{SIZE}",
                        "-strip", "-quality", "80", "-define", "webp:method=6", str(dest)], capture_output=True)
    return r.returncode == 0 and dest.exists() and dest.stat().st_size > 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=str(ROOT / "build/icon-cache"))
    args = ap.parse_args()
    cache = pathlib.Path(args.cache); cache.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    data = json.loads(SNAPSHOT.read_text())
    apps = data if isinstance(data, list) else data["apps"]
    todo = [(a["p"], a["i"]) for a in apps if a.get("i")]

    def one(item):
        pkg, url = item
        raw = cache / pkg
        if not fetch(url, raw) or not convert(raw, OUT / f"{pkg}.webp"):
            (OUT / f"{pkg}.webp").unlink(missing_ok=True)
            return None  # dead at the store (404 / access disabled): the app shows the letter badge, as the store does
        return pkg, url

    with cf.ThreadPoolExecutor(8) as ex:
        done = sorted(r for r in ex.map(one, todo) if r)
    keep = {f"{p}.webp" for p, _ in done}
    for f in OUT.glob("*.webp"):
        if f.name not in keep:
            f.unlink()  # an app that left the list takes its icon with it
    (OUT / "index.tsv").write_text("".join(f"{p}\t{u}\n" for p, u in done))
    total = sum((OUT / k).stat().st_size for k in keep)
    print(f"{len(done)} of {len(todo)} icons shipped, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
