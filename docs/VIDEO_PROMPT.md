# Demo video — the prompt (CLOCK IN, due Mon 12 Oct 13:59 Budapest)

Paste this whole file to Claude (this project, Code tab) when you have a Seeker in hand and 20 minutes. It makes the
video end to end; the only thing it cannot do is the Seed Vault double-tap and the one phone-camera shot of it.

---

## The prompt

Make the CLOCK IN demo video for Undersigned, under 3 minutes, from `docs/DEMO_SCRIPT.md`, on the Seeker that is
plugged in and has Undersigned 1.0.0 installed (check `adb devices`; airdropper SM02G40619126767 is the usual one,
wallet airdropper.skr). Rules: never screenshot or record while the Seeker Wallet or Seed Vault is in front; check
`mCurrentFocus` before every input; tell me the moment the wallet sheet appears and wait for my double-tap; no music
over the voice; no banned words (earn, reward, points, yield, invest, safe, scam, fraud, verified). Steps:

1. **Record the phone.** Start `adb shell screenrecord --bit-rate 12000000 --time-limit 180 /sdcard/u1.mp4` and drive
   the app over adb in this order, pausing about 2 s on each screen so the cuts land:
   Browse → All apps → Name, flick the A–Z rail to M and back (1,539 apps, every icon) → Installed → open AardAppvark
   Toolkit (store rating beside the signed score) → Write a signed review: 4 stars, Works + Fast, one line of text,
   tick the permanence consent, Sign → STOP driving and tell me; I double-tap on camera → confirmation → the review
   with the Attested and Seeker wallet pills → "1 signed" standing dialog → Publisher section → store reviews with the
   Genesis lens → Device facts (certificate matches) → Settings → Import my dApp Store reviews (scan only, do not sign)
   → back to Browse. Pull the file with `adb pull`. Record a second take if any screen was missed.
2. **Record the web.** In the built-in browser at 1080×1920 (portrait, so it cuts with the phone footage), open
   `https://undersigned.provenby.dev/app/com.adappvark.toolkit` and show the review that just landed, then the
   transaction on Solscan (`https://solscan.io/tx/<signature>`); capture as a short screen recording or 3 screenshots
   panned with ffmpeg.
3. **Voice.** Generate the narration from the "Say" column of the script with the TTS tool available in this session
   (one file per row, calm, British English, no music). If no TTS is available, write the rows to `assets/video/vo.txt`
   and tell me; I will record them.
4. **Edit with ffmpeg** (installed): cut the phone recording to the script's rows, insert my double-tap clip at 0:55
   (I will drop it in `assets/video/doubletap.mp4`), lay the narration over each row, title card from
   `assets/submission/pitch.html` slide 1 (render it with headless Chrome to a PNG) for 3 s at the start, end card with
   "undersigned.provenby.dev" for 3 s. 1080×1920 or 1920×1080 with the phone pillar-boxed on the brand background
   (#000, purple #9945FF accents, Manrope). Output `assets/video/undersigned-demo.mp4`, under 100 MB, H.264 + AAC.
5. **Check it**: play it back frame-sampled (ffmpeg thumbnails every 10 s) and read them; confirm every script row is
   on screen, the double-tap clip is in, the signature shown on Solscan matches the one the app showed, duration < 3:00,
   no wallet sheet or seed phrase frame anywhere. Then tell me where the file is, and nothing else to approve.

---

## What exists already (do not redo)
- Pitch deck: `assets/submission/Undersigned-pitch.pdf` (11 slides, checked). This is the "pitch deck or brief
  presentation" the form asks for; there is no live presentation.
- Form text: `docs/SUBMISSION.md`. APK: `assets/submission/Undersigned-1.0.0-vc10.apk`.
- Script: `docs/DEMO_SCRIPT.md` (timings and the words).

## GitHub for the judges
- Repo `aardappvark/Undersigned` is PRIVATE and in sync with origin/main (checked 8 Oct). It stays private: during the
  Align submission, the form offers "install the Radiants Align GitHub app"; install it on the `aardappvark` account and
  tick only this repository. That is the only step that needs the GitHub account owner. Judging reads commits up to
  the deadline, so every commit before Monday 13:59 Budapest counts.
- Upload the video to YouTube (unlisted) or Google Drive (anyone with the link) and paste the link in the form.
