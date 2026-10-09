# Undersigned: Pro, Promoted and scores vs consumer-review law (9 Oct 2026)

This memo checks one design against primary legal texts in four jurisdictions. It is not legal advice. The verdicts are
my reading of the texts, and the adviser questions at the end are where that reading is weakest.

Legend: **OK** = fine as designed · **DISCLOSE** = lawful with a specific disclosure or wording fix · **CHANGE** = change the
mechanism · **n/a** = provision does not reach it.

## Summary

| Element | UK (DMCC Sch 20 para 13) | EU (UCPD / DSA / P2B) | AU (ACL s18, s29) | US (16 CFR 465, 255) |
|---|---|---|---|---|
| Signed score: no payments, ranks app lists | DISCLOSE: "nothing paid" vs staked-SKR weight | DISCLOSE: ranking parameters (7(4a)), review sourcing (7(6)) | DISCLOSE: show count next to score | OK |
| "Pro member" mark | OK (but say it is a paid plan) | OK (but say it is a paid plan) | DISCLOSE: commercial relationship, prominently | OK |
| Separate "Pro members" score | DISCLOSE: label as paid-plan subset, with count | DISCLOSE | DISCLOSE | OK |
| Reviewer pays to promote own review | DISCLOSE plus anti-proxy controls | DISCLOSE ("Ad"); DSA Art 26 if not micro/small | DISCLOSE; it is an "advertisement" (s19(2)) | OK, except on the operator's own apps |
| Promoted review placed **first** | **CHANGE**: prominence by payment, not representativeness | DISCLOSE ("Ad", visually separate) | **CHANGE**: shade it apart from organic reviews | CHANGE on the operator's own apps (255.2(d)) |
| Payer is always the reviewer, traders barred | OK, but unenforceable without proxy detection | OK, same caveat | OK, same caveat | OK |
| Score-card explanation | DISCLOSE: also needs a published integrity policy | DISCLOSE: must cover the Recital 47 items | OK if beside the score, not in the T&Cs | DISCLOSE: must be visible without a tap |
| Hide text, keep rating; consensus collapse | OK if sentiment-neutral and logged | OK, plus a DSA Art 17 statement of reasons | OK | OK (465.7(b)(1)) |
| **Gap: operator publishes 28 of the reviewed apps** | **DISCLOSE** | **DISCLOSE** | **DISCLOSE**; s19/s38 shield likely lost | **DISCLOSE / CHANGE** (465.6, 255.2(d), 465.7(b)) |
| **Gap: publisher-gifted Pro seats** | **CHANGE**: they are incentivised reviews | **CHANGE** | **CHANGE** | **CHANGE** (255.5) |

**Things I found that contradict the brief**

1. The brief says the signed score is weighted by device attestation and review content only. The code also weights
   by **staked SKR**: `Ranking.weight` = `min(3, 1 + ln(1 + stakedSkr/1000))` in
   `protocol/.../Ranking.kt:50`. The score card says so ("weighted by staked SKR (up to 3×)"), but it also says
   "Nothing paid enters it". Anyone can buy SKR, stake it and get up to 3× weight.
2. UCPD Annex I **point 23a** is about reselling event tickets, not reviews. The review points are 23b and 23c.
3. The ACCC PDF URL in the brief 403s because the real filename uses an em dash ("reviews—a guide"). I took the text from
   the ACCC's own DOCX of the same publication.
4. `docs/MODERATION.md` mentions a **second hide path**: "the website operator's own hide list (`bin/api hide`)". The
   public `/moderation` list and the four-reason limit do not cover it.

## UK

**Sources:** DMCC Act 2024 Sch 20, legislation.gov.uk/ukpga/2024/13/schedule/20 (para 13 in force 6.4.2025, S.I.
2025/272). CMA208 *Fake reviews* (4 Apr 2025),
assets.publishing.service.gov.uk/media/67eeb64fe9c76fa33048c790/CMA208_-_Fake_reviews_guidance.pdf.

- 13(2): "Publishing consumer reviews, or consumer review information, in a misleading way."
- 13(5)(i): misleading "includes … (ii) giving greater prominence to positive consumer reviews over negative ones
  (or vice versa); (iii) omitting information that is relevant to the circumstances in which a consumer review has been
  written".
- 13(5)(c): a review "conceals the fact it has been incentivised" if the author was "commissioned" and that fact is not
  made apparent. 13(5)(g): "'commissioning' includes incentivising by any means".
- 13(3): a publisher must take "reasonable and proportionate steps" to prevent and remove fake reviews, concealed
  incentivised reviews and false or misleading review information.
- CMA208 3.5: an incentivised review must be labelled "prominently as incentivised, namely, as an advert. The label
  should not have an ambiguous meaning".
- CMA208 4.6: the duty to give circumstances covers a reviewer with "a commercial link with the trader".
- CMA208 5.1(b): misleading if review information is "influenced by accepting commissions from the trader who is being
  reviewed in return for greater prominence in rankings".
- CMA208 8.9: publishers "should all have published policies which prohibit fake reviews", setting out their approach to
  incentivised reviews.
- CMA208 8.19(a)(i): publishers should not block reviews "merely because they have not bought the relevant product".
- CMA208 8.23: publishers should explain temporary measures such as "marking them as pending or suspicious".

**Element by element**

- **Pro mark and score: OK.** Pro is bought from Undersigned, not the reviewed trader, and is not conditional on
  reviewing, so it is not "commissioning". Add "paid plan", because "Pro" alone reads as "expert".
- **Promotion: change.** The promoter is a consumer, so this is not a trader commission. The risk is 13(5)(i)(ii): the
  review shown first is picked by whoever pays, mostly fans, and a label does not cure skewed prominence. A trader using
  a proxy wallet makes it the CMA208 5.1(b) case.
- **Score card: add a policy.** 13(3) and CMA208 8.9 also need a published policy, a risk assessment, detection,
  investigation, sanctions and periodic re-evaluation.
- **Non-users:** there is no duty to exclude them (CMA208 2.7, 8.19(a)(i)), but a review falsely claiming use is fake.
- **Hiding text: OK.** The four reasons in MODERATION.md are sentiment-neutral and the rating still counts. Explain
  "collapsed" on the page (CMA208 8.23).

## EU

**Sources:** Directive 2005/29/EC consolidated text 28.05.2022 (as amended by 2019/2161), CELEX 02005L0029-20220528.
Commission Notice 2021/C 526/01 (OJ C 526, 29.12.2021), §4.2.3–4.2.4. DSA Reg. 2022/2065. P2B Reg. 2019/1150. All were
fetched from the EU Publications Office cellar because EUR-Lex returned a WAF challenge. Whether any amendment after
28.05.2022 touches these articles is [unverified].

- Art 7(6): "information about whether and how the trader ensures that the published reviews originate from consumers
  who have actually used or purchased the product shall be regarded as material."
- Notice §4.2.4, summarising Recital 47: this covers "whether all reviews are published, how they are sourced, how
  average review scores are calculated and if they are influenced by sponsored reviews". It must be available "from the
  same interface where reviews are published".
- Art 7(4a): when consumers search across traders' products, "the main parameters determining the ranking … and the
  relative importance of those parameters" are material, "in a specific section … directly and easily accessible from
  the page where the query results are presented".
- Annex I 11a: search results "without clearly disclosing any paid advertisement or payment specifically for achieving
  higher ranking". Notice: "indirect payments do not cover … membership subscriptions" not dedicated to ranking. Labels
  should read "'Ad', 'Advert', 'Advertisement'" rather than "sponsored".
- Annex I 23b: do not state that reviews come from people who used or bought the product "without taking reasonable and
  proportionate steps to check".
- Annex I 23c: prohibits "misrepresenting consumer reviews". The Notice's examples include ratings presented "on the
  basis of undisclosed and/or opaque criteria".
- DSA Art 3(r): an "advertisement" is information promoting "the message of a legal or natural person … presented …
  against remuneration specifically for promoting that information". A promoted review fits this.
- DSA Art 26(1) requires marking "that the information is an advertisement", showing on whose behalf and who paid.
  **Art 19(1) exempts micro and small enterprises from Arts 20–28**, so Arts 26 and 27 likely do not apply. The size
  test is [unverified] for the operator.
- DSA Art 17 applies to all hosting services. It requires a statement of reasons for "any restrictions of the
  visibility of specific items of information … including … demoting content".
- P2B Art 5(1) requires ranking parameters in the terms for business users, and Art 5(3) a description of any paid
  influence. It applies only to "online intermediation services", which need "contractual relationships" with business
  users and facilitation of "direct transactions". That is possible through publisher claims, treasury listings and store
  deep links, but uncertain.

**Element by element:** Pro, both scores and promotion are lawful if disclosed. App ordering never uses payment, so 11a
is not triggered, but its standard ("Ad", in "immediate affiliation") is the model for the promoted slot. The score card
must also state that all reviews are published, how they are sourced (on-chain plus the store mirror), that text can be
hidden but ratings never are, and that promoted reviews count once at ordinary weight. "Opinions of individual owners"
risks a 23b reading. Say "owners of attested Seeker devices".

## Australia

**Sources:** Competition and Consumer Act 2010 Sch 2 (ACL), compilation C167 of 16 Sep 2026, legislation.gov.au
(C2004A00109, vol 4). AustLII refuses AI crawlers, so I did not use it. ACCC, *Online reviews—a guide for business and
review platforms* (published 3 Dec 2013), DOCX from
accc.gov.au/about-us/publications/a-guide-to-online-reviews-for-business-and-review-platforms.

- s18(1): "A person must not, in trade or commerce, engage in conduct that is misleading or deceptive or is likely to
  mislead or deceive."
- s29(1)(e)–(f): no "false or misleading representation that purports to be a testimonial", or one "concerning … a
  testimonial by any person".
- s19(1) and s38(1) exempt an "information provider" publishing in the course of an information business. But
  s19(2)/s38(2): this "does not apply to a publication of an advertisement". And s19(3)(a): it does not apply where "the
  publicised goods or services were goods or services of a kind supplied by the information provider".
- ACCC Principle 1: "be open and transparent … about commercial relationships which impact on … consumer reviews."
- ACCC: distinguish "review results which are in any way promoted … through shading or other means so that their
  content is not confused with 'organic' review results".
- ACCC: prominent disclosure is "at least as prominent as the text of written reviews … Disclosure in a separate terms
  and conditions page is not prominent". It should be "proximate to the aggregated review result".
- ACCC: show "the total number of reviews that the rating is based on next to the aggregated rating".

**Element by element:** each promoted review is an advertisement and loses the s19/s38 shield. Because the operator
supplies apps of the kind reviewed, s19(3)(a) probably removes the shield for the operator's whole app catalogue (this is
my reading; an adviser should confirm). Being first is acceptable only if the review is visually set apart from organic
reviews. The Pro mark and score are commercial relationships with the platform that affect presentation, so disclose them
prominently. Hiding and collapsing fit the ACCC's note that hiding offensive or suspected-fake reviews "is not
misleading".

## US

**Sources:** 16 CFR Part 465 (89 FR 68077, 22 Aug 2024) and Part 255, eCFR as at 2026-10-01.

- 465.1(c)(4): "A disclosure is not clear and conspicuous if a consumer must take any action, such as clicking on a
  hyperlink or hovering over an icon, to see it."
- 465.1(d): consumer reviews "include consumer ratings regardless of whether they include any text".
- 465.2(d)(2): 465.2(b)–(c) do not apply to "merely engaging in consumer review hosting".
- 465.4 bars a business giving "compensation or other incentives … conditioned … on" reviews of a particular sentiment.
- 465.5(a): an officer or manager reviewing their own business's products must disclose the relationship.
- 465.6 bars misrepresenting that a site "it controls, owns, or operates provides independent reviews or opinions, other
  than consumer reviews", about a category that includes its own products.
- 465.7(b): it is not suppression if criteria are "applied equally to all reviews … without regard to sentiment". Its
  list includes "abusive" content and "the personal information … of another individual".
- 255.2(d): advertisers "boosting, organizing, publishing … consumer reviews of their products" must not distort "what
  consumers think of their products".
- 255.5(a): material connections include "free or discounted products". 255.5 Example 6(ii) says incentivised star
  ratings "included in an average star rating" can still deceive.

**Element by element:** Undersigned is a host and does not buy reviews, so Pro, both scores and promotion are generally
OK. The operator is also the seller of 28 reviewed apps, though. On those pages, 255.2(d) and 465.7(b) apply to
Undersigned as an advertiser, and selling promotions there is risky. 465.6 reaches any operator-authored labels about
competitors, such as "Unnatural reviews" or publisher responsiveness, if the site is presented as independent. Any
score-card text behind an info icon fails 465.1(c)(4).

## Gaps not in the brief

1. **Operator conflict.** The operator publishes 28 of the reviewed apps (docs/FLEET_SEEDING.md), sells Pro and
   promotions on their pages, lists them on the treasury board for a self-transfer, and its only moderator wallet
   (`HVXV…yDaD`) is a publisher wallet.
2. **Gifted Pro seats** are free products from the trader, so the recipient's reviews of that publisher's apps are
   incentivised (UK 13(5)(c), FTC 255.5, ACCC). Removing the Pro mark is not enough.
3. **Proxy promotion.** At about $0.60, a trader can buy prominence through its own wallets. The system cannot verify
   that the payer is a consumer.
4. **Records.** The chain holds reviews and payments. Investigations, sanctions and `bin/api hide` need their own log
   (CMA208 8.19(b)(i), 8.26(f)).
5. **Non-users** may review, but must not be described as users.

## Changes recommended (highest risk first)

1. **Take the promoted review out of first place in the review list.** Put it in a boxed, shaded slot labelled
   "Ad · promoted by the reviewer". Allow at most one per page, rotate if there are several, and always show the most
   helpful critical review beside it with equal prominence.
2. **Do not sell promotions on the operator's own apps.** Disclose on those pages and on About: "Undersigned's operator
   publishes this app." Bar operator and staff wallets from reviewing, flagging, voting on or promoting any app. Move
   moderation to a wallet that publishes nothing.
3. **Publisher-gifted Pro (including the operator's):** label the recipient's reviews of the gifting publisher's apps
   "Received Pro from this publisher". Exclude them from the signed score and block promotion on them.
4. **Anti-proxy controls.** Require a declaration at checkout: "I am not paid by, employed by or acting for this app's
   publisher or a competitor." Ban proxy promotion in the terms, flag promotions whose funding traces to verified
   publisher or treasury wallets or clusters on one app, and apply escalating sanctions.
5. **Reword the signed-score line:** "No payment to Undersigned enters it. Staked SKR, a token anyone can buy, raises a
   review's weight up to 3×." Alternatively, drop SKR weighting.
6. **Publish a review-integrity policy** and link it from every review page and list. It should cover: fake reviews
   prohibited; the approach to incentivised reviews; sourcing; that all reviews are published; hide and collapse rules;
   Pro; promotion; SKR weight; and ranking parameters, which must also be reachable from search results. Keep a dated
   risk assessment and review it each quarter.
7. **Labels visible without a tap:** "Pro member (paid plan)", the "Ad" wording above, and a review count beside every
   score.
8. **Bring `bin/api hide` under the four reasons and the `/moderation` list, or remove it.** Send a statement of reasons
   in-app for every text hide (DSA Art 17).
9. **Explain "collapsed" on the page** and say whether collapsed reviews still count. Publish hide and collapse rates by
   star rating each quarter.
10. **Label reviews without install evidence** and change "owners" to "owners of attested Seeker devices".

## Questions for a consumer-law adviser

1. Is a consumer paying to put their own review first "giving greater prominence" under DMCC 13(5)(i)(ii) even when
   labelled? Does a balanced adjacent slot cure it?
2. Is a reviewer-paid promotion the reviewer's commercial practice or Undersigned's, and an "advertisement" under ACL
   s19(2) and DSA Art 3(r)?
3. Does ACL s19(3)(a) remove the information-provider exemption for the whole catalogue?
4. Is the operator micro or small (DSA Art 19), and a "hosting service" when records live on Solana? Is P2B Art 5
   engaged by publisher claims, treasury listings and store deep links?
5. Which Member State's UCPD transposition applies, given where the operator is established? Is the UK targeted?
6. Is a staked-SKR-weighted score acceptable consumer review information if disclosed, given the financial-promotion
   copy rule in LEGAL_GATES.md?
7. For gifted Pro, is a label enough, or must those reviews leave the signed score?
8. Could FTC 465.6 reach operator-authored integrity labels about competitors' apps?
9. Is hiding text in the UI enough for erasure duties when the text stays on-chain?
