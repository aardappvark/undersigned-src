package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject

/** What changed in the API and MCP, newest first: served as /changelog and /v1/changelog. Add an entry with every release. */
object Changelog {
    data class Entry(val date: String, val title: String, val items: List<String>)

    val ENTRIES = listOf(
        Entry("2026-10-10", "Keys hashed; every redemption renews the key", listOf(
            "API keys are stored as hashes; a copy of the database opens nothing. Keys already issued keep working.",
            "Each redemption (a new pass, a top-up, or proving the same payment again) returns a NEW key for the pass and retires the previous one. Webhooks and the pass's allowance follow the new key. POST /v1/keys and POST /v1/publisher/plan say so in their answer.",
            "Quick reviews can be withdrawn: the relayer accepts a withdrawal (rvk) signed by the app key that wrote the review.",
            "Quick reviews count in no score, whatever the signing key holds; /v1/stats and the dataset apply the operator rule everywhere; found-wallet trends apply the list's filters; the store-sync time survives a restart.")),
        Entry("2026-10-10", "Publisher plan and data plan", listOf(
            "Publisher plan, per app per 30 days (0.0607 SOL, 369 SKR, 332 with a Genesis Token; up to 12 months a payment): POST /v1/checkout {publisher, months}, pay from the app's proven publisher wallet, POST /v1/publisher/plan. GET /v1/publisher/{pkg}/report: reviews waiting for an answer, the store rating's trend, bursts, certificate/version/permission changes, scores by version, the category benchmark. GET /v1/publisher/{pkg}/plan: whether a plan is live.",
            "Data plan, 30 days (0.607 SOL, 3,690 SKR, 3,320 with a Genesis Token): 2,000,000 requests at 3,000 a minute and GET /v1/export/{ratings|changes|apps|found} as CSV. POST /v1/checkout {plan: \"data\"}, then POST /v1/keys.",
            "/plans: buy either from a browser (Solana Pay links and a QR code), redeem, and read the publisher report. Local MCP: api_redeem_publisher_plan, api_publisher_plan, api_publisher_report, api_export.")),
        Entry("2026-10-10", "AI allowance per caller", listOf(
            "Fresh AI answers per UTC day: 200 per pass key, 50 per Pro wallet, 20 per IP; per-call keys pay one extra call per fresh answer. Cached answers are free. Over it: 429 ai_allowance with Retry-After.",
            "GET /v1/health reports today's AI calls, tokens and cost.",
            "Callers without a key share one budget of 300 fresh AI answers a day; cached answers stay free.")),
        Entry("2026-10-09", "Developer tools", listOf(
            "MCP: get_apps (several apps at once) and scoring_help (questions answered from the integrity policy).",
            "Local MCP (mcp/, 0.2.0): every API route as a tool (42), on by default against the public API; api_verify_webhook checks a delivery locally; npm test checks coverage against the live spec.",
            "Webhook verification code for Node and Python in the developer docs.")),
        Entry("2026-10-09", "Treasuries found in app code", listOf(
            "GET /v1/payments/found and MCP found_treasuries: wallets written into apps' store builds that received payments from 5+ wallets, labelled 'found in app code, not confirmed', separate from the board and never ranked.",
            "GET /v1/payments/found/{pkg} and /v1/payments/found/trends: analytics from each found wallet's full payment history (dates, monthly series, new and returning payers, concentration); MCP found_treasuries takes package or trends.")),
        Entry("2026-10-09", "Store-wide releases", listOf(
            "/v1/changes, app_changes and webhooks add kind 'release': any listing's updated date moved, across the whole store (version codes are still read for apps the ledger mentions).",
            "The weekly digest covers the seven days to the start of today (UTC) and is rewritten when its facts change.",
            "A daily crawl whose star counts mostly failed is retried within the hour.")),
        Entry("2026-10-09", "AI reading aids, labelled suggestions", listOf(
            "GET /v1/records/{id}/translation, POST /v1/translate, POST /v1/translate/reply: suggested translations for Pro wallets or keys.",
            "GET /v1/apps/{pkg}/summary: suggested summary of counted reviews (3 or more), each point with its count and review ids.",
            "GET /v1/digest: this week's digest of store-wide rating bursts and app updates; a weekly 'digest' webhook event.",
            "POST /v1/help (in-app help from the integrity policy) and POST /v1/ask (docs helper).",
            "/v1/apps/{pkg}/similar adds suggested_paraphrase_groups; /v1/changes adds suggested_note.",
            "MCP: translate_review, app_summary, weekly_digest.")),
        Entry("2026-10-09", "Pay per call, webhooks, spec", listOf(
            "Pay per call for agents: 0.00001 SOL or 0.06 SKR a call, from 0.001 SOL; POST /v1/checkout {\"calls\": N}. 402 answers carry an x402-shaped accepts list.",
            "SKR prices 10% below SOL: pass 369 SKR (332 with a Seeker Genesis Token).",
            "Webhooks: X-Undersigned-Signature-V2 (timestamped), seven attempts over about a day, POST /v1/hooks/{id}/test, GET /v1/hooks/{id}/deliveries.",
            "X-RateLimit-* headers on every metered answer; typed OpenAPI parameters and response schemas; GET /v1/apps?packages=a,b,c.",
            "A score with nothing counted is null, never 0.")),
        Entry("2026-10-09", "MCP server", listOf(
            "POST /mcp: Model Context Protocol (Streamable HTTP), read-only tools, resources and prompts; /llms.txt and /.well-known/mcp/server-card.json.")),
        Entry("2026-10-09", "One voice per device", listOf(
            "Staked SKR no longer changes weight; each review shows the SKR and SOL staked when it appeared (stake_at_review). sort=skr|sol on reviews.")),
    )

    fun json(): JSONObject = JSONObject().put("entries", JSONArray(ENTRIES.map { e -> JSONObject().put("date", e.date).put("title", e.title).put("items", JSONArray(e.items)) }))
}
