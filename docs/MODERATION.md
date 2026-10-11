# Moderation (7 Oct 2026)

Moderation on Undersigned hides **text**, never a voice.

- **Who:** the wallets in `protocol/.../Moderation.kt` (`MODERATORS`). Today one: the operator, `HVXV…yDaD` (airdropper.skr),
  until a dedicated moderation wallet exists. Changing the list is a release, visible in the source.
- **What:** `SRL1|mod|<record id>|<reason>` hides a review's or reply's text in the app, on the website and in the API. The
  rating, tags and weight still count; the record stays on Solana. `SRL1|umd|<record id>` undoes it; the moderator's later
  record wins. The reason is shown in place of the text. There is no "show anyway".
- **The only four reasons:** illegal content; someone's personal data (names, addresses, phone numbers, keys, seed words);
  abuse or harassment aimed at a person; impersonating a publisher.
- **Not reasons:** a low rating, a harsh or negative opinion, criticism of an app or its publisher, disagreement. Spam and
  off-topic records are for community flags (`flg`), which any Seeker owner can sign.
- **Conflicts:** a moderator's record on a review of an app that moderator is the proven publisher of is ignored (and on
  the replies under it), as a publisher's flags on their own app are.
- **Appeals:** the author signs a dispute (`dsp`, shown beside the record) or writes to the contact on `/report` with the
  record id; a moderator looks again within 7 days and answers.
- **Transparency:** every moderation record is listed at `/moderation` and in `/v1/records?type=mod`.
- **Separate:** the website operator's own hide list (`bin/api hide`) affects only the website and API.


## Automatic hiding and exclusion (11 Oct 2026)

- **Abusive language is hidden automatically.** One list ships in the protocol module (`protocol/.../Abuse.kt`: slurs, strong
  profanity, threats and harassment phrases, matched on whole words after folding case, accents, leetspeak, masking and
  repeated letters). A note, reply, dispute, store note, fix note or own tag that matches is: not signed by the app (the words
  are named so the writer can edit); not relayed as a quick review; and, if it reaches the chain some other way, shown by every
  reader with its text hidden and the reason "abuse". The rating, tags and weight still count. It hides text, never a voice.
- **A wallet that keeps at it is excluded.** A moderator signs `SRL1|ban|<wallet>|<reason>` (one of the four reasons);
  every reader then drops that wallet's records before anything is scored or shown, the relayer refuses its key, and its
  wallet page says so. `SRL1|unb|<wallet>` lifts it; the moderator's later record wins. The records stay on Solana. Moderators
  and the operator's wallets can never be banned, so no key can lock the ledger's own keepers out.
- **Not automatic:** anything not on the list. A harsh review, criticism, a low rating, disagreement, sarcasm: none of it is
  touched. The list is short on purpose and public in the source; changing it is a release.
