package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject

/** What changed in the API and MCP, newest first: served as /changelog and /v1/changelog. Add an entry with every release. */
object Changelog {
    data class Entry(val date: String, val title: String, val items: List<String>)

    val ENTRIES = listOf(
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
