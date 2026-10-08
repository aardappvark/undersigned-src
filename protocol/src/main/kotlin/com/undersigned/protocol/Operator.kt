package com.undersigned.protocol

/**
 * Undersigned's own wallets (compliance review, 9 Oct 2026, docs/COMPLIANCE_REVIEWS.md). The operator also publishes apps
 * in the store this ledger reviews, so its wallets are insiders everywhere: what they sign is shown, labelled "Undersigned
 * operator", and never counted. No score, no flag, no vote, no clear, no hide. Ships with the app and the server; changing
 * it is a release, visible in the source.
 */
object Operator {
    val WALLETS: Set<String> = setOf(
        "HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD",   // airdropper.skr: publisher of the fleet, moderator
        "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q",   // the fleet treasury and publisher
        "8oLtrvMnk7bLzjYzWQdo66kJsvkG4CYL1QKoeKbBHT8F",   // vitalik.skr, the operator's own Seeker
        "FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF",   // popcat.skr, the operator's own Seeker
    )

    fun isOperator(wallet: String): Boolean = wallet in WALLETS

    /** The facts every SCORE, flag, vote and hide reads: an operator wallet weighs nothing, whatever it holds. */
    fun gate(facts: (String) -> SignerFacts): (String) -> SignerFacts = { w -> if (w in WALLETS) SignerFacts(false, 0.0) else facts(w) }

    /** True when the operator is a proven publisher of [pkg]: the app's page must say so. */
    fun publishes(pkg: String, verifiedPublishers: Map<String, Set<String>>): Boolean = verifiedPublishers[pkg].orEmpty().any { it in WALLETS }
}
