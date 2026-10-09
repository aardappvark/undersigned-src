#!/bin/bash
# Builds the voice-over for the motion video and muxes it. Re-run after any edit to the lines or the render.
#   bash tools/build_voice.sh            (needs macOS `say`, ffmpeg, node; reads undersigned-motion.mp4)
# Lines:  docs/MOTION_BRIEF.md, table "Scenes, timings and voice-over" (last column, in quotes).
# Timing: assets/video/motion/motion.html SCENES. Each line starts at its scene's start + LEAD (0.6 s) and must end
#         MARGIN (0.15 s) before the scene ends. A line that is too long is read faster (RATE up to RATE_MAX, `say -r`),
#         then started earlier (LEAD down to LEAD_MIN); every adjustment is printed. A line that still overruns fails.
# Out:    assets/video/motion/undersigned-motion-vo.mp4 (full, AAC) and undersigned-motion-60s.mp4 (scenes in CUT).
# Env:    VOICE (default Samantha), CUT (default "1 3 5 6 9 10"), LUFS (default -16).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; M="$ROOT/assets/video/motion"; BRIEF="$ROOT/docs/MOTION_BRIEF.md"
VOICE="${VOICE:-Samantha}"; CUT="${CUT:-1 3 5 6 9 10}"; LUFS="${LUFS:--16}"
RATE=180; RATE_MAX=200; LEAD=0.6; LEAD_MIN=0.2; MARGIN=0.15
W="$(mktemp -d -t undersigned-vo)"; trap 'rm -rf "$W"' EXIT
[ -f "$M/undersigned-motion.mp4" ] || { echo "render first: node tools/render_motion.mjs"; exit 1; }

# n <TAB> start <TAB> duration <TAB> spoken text   (scene times from motion.html; "dApp" spoken as one word, "dapp" (owner, 10 Oct 2026: "dapp not d app"; say reads "dapp" exactly as "dap"))
node -e '
const fs = require("fs"); const [html, md] = process.argv.slice(1).map((f) => fs.readFileSync(f, "utf8"));
const scenes = [...html.match(/const SCENES = \[([\s\S]*?)\];/)[1].matchAll(/\["s(\d+)",\s*([\d.]+),\s*([\d.]+)\]/g)];
const sec = md.split("## Scenes, timings and voice-over")[1].split("\n## ")[0];
const lines = {}; for (const row of sec.split("\n")) { const m = row.match(/^\|\s*(\d+)\s*\|.*\|\s*"(.+)"\s*\|\s*$/); if (m) lines[m[1]] = m[2]; }
for (const [, n, s, d] of scenes) { if (!lines[n]) throw new Error("no voice line for scene " + n);
  console.log([n, s, d, lines[n].replace(/\bd[Aa]pp/g, "dapp")].join("\t")); }
' "$M/motion.html" "$BRIEF" > "$W/lines.tsv"

speech_end() { # seconds until the last sound in an audio file
  local e; e=$(ffmpeg -hide_banner -i "$1" -af silencedetect=n=-45dB:d=0.15 -f null - 2>&1 | grep -oE 'silence_start: [0-9.]+' | tail -1 | cut -d' ' -f2 || true)
  [ -n "$e" ] && echo "$e" || ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
fits() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a <= b) }'; }

: > "$W/plan.tsv"   # n start dur lead
while IFS=$'\t' read -r n s d txt; do
  rate=$RATE; lead=$LEAD
  while :; do
    say -v "$VOICE" -r "$rate" -o "$W/l$n.aiff" "$txt"; len=$(speech_end "$W/l$n.aiff")
    budget=$(awk -v d="$d" -v l="$lead" -v m="$MARGIN" 'BEGIN { print d - l - m }')
    fits "$len" "$budget" && break
    if [ "$rate" -lt "$RATE_MAX" ]; then rate=$((rate + 5)); continue; fi
    if ! fits "$lead" "$LEAD_MIN"; then lead=$(awk -v l="$lead" 'BEGIN { printf "%.1f", l - 0.1 }'); continue; fi
    echo "scene $n: line does not fit (${len}s spoken, ${budget}s available at -r $RATE_MAX, lead $LEAD_MIN)"; exit 1
  done
  wpm=$(awk -v w="$(echo "$txt" | wc -w)" -v l="$len" 'BEGIN { printf "%.0f", w / l * 60 }')
  note=""; [ "$rate" != "$RATE" ] && note=" RATE-ADJUSTED -r $rate"; [ "$lead" != "$LEAD" ] && note="$note, starts at +${lead}s"
  printf 'scene %-2s %5.2fs spoken, %s wpm, ends %.2fs before the scene ends%s\n' "$n" "$len" "$wpm" \
    "$(awk -v d="$d" -v l="$lead" -v x="$len" 'BEGIN { print d - l - x }')" "$note"
  printf '%s\t%s\t%s\t%s\n' "$n" "$s" "$d" "$lead" >> "$W/plan.tsv"
done < "$W/lines.tsv"

mix() { # $1 out.wav, $2 total seconds, then n:offset pairs (offset = where the scene starts in this output)
  local out=$1 tot=$2; shift 2; local ins=() fc="" labels="" i=0 spec n off lead ms
  for spec in "$@"; do n=${spec%%:*}; off=${spec##*:}; lead=$(awk -F'\t' -v n="$n" '$1 == n { print $4 }' "$W/plan.tsv")
    ms=$(awk -v o="$off" -v l="$lead" 'BEGIN { printf "%d", (o + l) * 1000 }'); ins+=(-i "$W/l$n.aiff")
    fc="$fc[$i:a]aresample=48000,aformat=channel_layouts=stereo,adelay=${ms}|${ms}[a$i];"; labels="$labels[a$i]"; i=$((i + 1)); done
  ffmpeg -hide_banner -loglevel error -y "${ins[@]}" -filter_complex "${fc}${labels}amix=inputs=$i:normalize=0:dropout_transition=0,apad,atrim=0:$tot[m]" \
    -map "[m]" -c:a pcm_s16le "$W/raw.wav"
  local j; j=$(ffmpeg -hide_banner -i "$W/raw.wav" -af "loudnorm=I=$LUFS:TP=-1.5:LRA=11:print_format=json" -f null - 2>&1 | sed -n '/^{/,/^}/p')
  v() { echo "$j" | grep "\"$1\"" | grep -oE '\-?[0-9.]+'; }
  ffmpeg -hide_banner -loglevel error -y -i "$W/raw.wav" -af "loudnorm=I=$LUFS:TP=-1.5:LRA=11:measured_I=$(v input_i):measured_TP=$(v input_tp):measured_LRA=$(v input_lra):measured_thresh=$(v input_thresh):offset=$(v target_offset):linear=true" \
    -ar 48000 -c:a pcm_s16le "$out"; }

# Full video
args=(); total=0
while IFS=$'\t' read -r n s d lead; do args+=("$n:$s"); total=$(awk -v s="$s" -v d="$d" 'BEGIN { print s + d }'); done < "$W/plan.tsv"
mix "$W/vo_full.wav" "$total" "${args[@]}"
ffmpeg -hide_banner -loglevel error -y -i "$M/undersigned-motion.mp4" -i "$W/vo_full.wav" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest \
  -movflags +faststart "$M/undersigned-motion-vo.mp4"

# Short cut: each scene cut on its own boundaries from the full render (each already fades in and out), then joined
args=(); off=0; i=0; : > "$W/list.txt"
for n in $CUT; do IFS=$'\t' read -r _ s d _ < <(awk -F'\t' -v n="$n" '$1 == n' "$W/plan.tsv")
  ffmpeg -hide_banner -loglevel error -y -ss "$s" -i "$M/undersigned-motion.mp4" -t "$d" -an -c:v libx264 -pix_fmt yuv420p -crf 16 -preset slow -r 30 "$W/p$i.mp4"
  echo "file 'p$i.mp4'" >> "$W/list.txt"; args+=("$n:$off"); off=$(awk -v o="$off" -v d="$d" 'BEGIN { print o + d }'); i=$((i + 1)); done
ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i "$W/list.txt" -c copy "$W/v_cut.mp4"
mix "$W/vo_cut.wav" "$off" "${args[@]}"
ffmpeg -hide_banner -loglevel error -y -i "$W/v_cut.mp4" -i "$W/vo_cut.wav" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest \
  -movflags +faststart "$M/undersigned-motion-60s.mp4"

for f in undersigned-motion undersigned-motion-vo undersigned-motion-60s; do
  echo "$f.mp4: $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$M/$f.mp4") s ($(ffprobe -v error -show_entries stream=codec_name -of csv=p=0 "$M/$f.mp4" | tr '\n' ' '))"; done
for f in undersigned-motion-vo undersigned-motion-60s; do
  echo "$f.mp4 audio: $(ffmpeg -hide_banner -i "$M/$f.mp4" -map 0:a -af volumedetect -f null - 2>&1 | grep -oE '(mean|max)_volume: -?[0-9.]+ dB' | tr '\n' ' ')"; done
