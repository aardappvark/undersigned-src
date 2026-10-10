#!/bin/sh
# A dApp screenshot from a Seeker, cropped to the app's own area: no status bar, no gesture bar (owner's rule, 10 Oct 2026:
# "for screenshots of dApp don't include the status bar"). Seeker is 1200x2670; the status strip is the top 120 px and the
# gesture strip the bottom 60 px, so the app area is 1200x2490 — the size every deck and video shot already uses.
#   sh tools/shot.sh <device-serial> <out.png>
set -e
D="$1"; OUT="$2"; [ -n "$D" ] && [ -n "$OUT" ] || { echo "usage: sh tools/shot.sh <serial> <out.png>"; exit 1; }
T=$(mktemp -t shot).png
adb -s "$D" exec-out screencap -p > "$T"
magick "$T" -crop 1200x2490+0+120 +repage "$OUT"
rm -f "$T"; echo "$OUT"
