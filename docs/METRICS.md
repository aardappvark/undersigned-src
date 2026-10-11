# Retention and engagement: what is measurable today, honestly

Undersigned went to the first commit on 7 September 2026 and has not been listed on the dApp Store yet, so there
are no independent users. Every one of the 101 mainnet records is from the founding team's own wallets, labelled
and counted nowhere. This page says what the instrumentation measures, so the same numbers can be read by anyone
once owners arrive.

## Measured now (12 Oct 2026)

| Metric | Value | Where it is read |
|---|---|---|
| Records on mainnet | 101 | `/v1/stats` |
| Live signed reviews / counted | 13 / 0 | `/v1/stats` (`reviews_counted` is 0 by rule: ours never count) |
| Publisher answered share (the Toolkit) | 1 of 2 signed reviews answered; first fix within the hour; follow-up 4★ → 5★ | the Toolkit page, `/v1/apps/com.adappvark.toolkit` → `publisher` |
| Feature request → decision → delivery | 45 min to "will build", delivered in version 100 within the hour | `/v1/apps/com.seekerverify.app` → `publisher.feature_requests` |
| Publisher claims proven | 28 apps | `/v1/stats` (`claims_proven`) |
| Store reviews read for analytics | 155,095 across 541 dApps | `/v1/reviews/analytics?source=store` |
| App opens (anonymous counts, since 12 Oct) | public on the fleet dashboard | `undersigned-analytics.aardappvark.workers.dev/public-stats` |

## What will be measurable, with no new code

| Question | How it is read |
|---|---|
| Weekly active reviewers | distinct signers of records per ISO week: `/v1/records?since=&until=` grouped by `signer` |
| Repeat reviewers | signers with reviews of two or more apps, or a second review after a fix: `/v1/wallets/{w}` → `standing.apps_reviewed`, `follow_ups` |
| Publisher response rate and speed | per app `publisher.answered_share`, `median_reply_hours`, fixes and follow-ups: `/v1/apps/{pkg}`; across apps `/v1/leaderboard`, the publisher desk |
| Follow-up rate after a fix | `fix` records with a later `fup` from the same reviewer: `/v1/records?type=fix` joined to `type=fup` |
| Notification → action | the App's notification kinds are local; the on-chain signal is the follow-up or reply signed after the alert (above). Open rates are not collected: there is no identifier to tie them to |
| Weekly engagement hooks | the Monday "new this week" list and the per-dApp update alerts are local; their effect shows as reviews of newly listed apps (`/v1/records?type=rev&since=`) |
| Developers acting on signed reviews | `fix` and `ack` records by proven publishers other than ours: `/v1/records?type=fix` filtered by signer |

All of it is public and recomputable from the chain; see `docs/ONCHAIN.md`.

## Conversion figures that do exist (all from the founding team's own activity, 12 Oct 2026)

| Measure | Value | Read from |
|---|---|---|
| Publisher claim conversion | 28 of 28 of our store apps claimed and proven; 0 publishers outside the team yet | `/v1/stats` `claims_proven` |
| Follow-up rate after a publisher fix | 2 fixes signed on mainnet, 2 follow-ups signed by the reviewer (4★ → 5★ both times) | `/v1/records?type=fix`, `type=fup` |
| Feature-request conversion | 1 request → "will build" in 45 minutes → delivered within the hour | `/v1/apps/com.seekerverify.app` → `publisher.feature_requests` |
| Publisher reply speed | median 17.3 h on the Toolkit's two signed reviews, 1 of 2 answered | `/v1/apps/com.adappvark.toolkit` → `publisher.responsiveness` |
| Notification open rates | not collected: there is no identifier to tie an open to; the on-chain follow-up or reply signed after an alert is the only trace we keep | `docs/PRIVACY_MODEL.md` |
| Weekly active reviewers | 3 wallets (ours) this week; the figure is public and will read the same way for strangers | `/v1/records?since=` grouped by signer |

Nothing here is independent usage, and it is not presented as such.

