package com.undersigned.api

import com.undersigned.protocol.Registry

/**
 * Everything tunable in one place. Money constants follow the fleet rule (docs/API.md §2): re-peg only with
 * fleet-config/tools/reprice.py before a release, and never raise a leg without keeping the old amount as a floor.
 */
object Config {
    val PORT: Int = System.getenv("PORT")?.toIntOrNull() ?: 8797
    /** Loopback by default: only this machine can call it. Set HOST=0.0.0.0 deliberately to serve a network. */
    val HOST: String = System.getenv("HOST") ?: "127.0.0.1"
    val DB_PATH: String = System.getenv("UNDERSIGNED_DB") ?: "undersigned-api.db"
    /** Comma-separated RPC URLs, keyed provider first. Public pools refuse some methods; the client fails over. */
    val RPC: List<String> = System.getenv("UNDERSIGNED_RPC")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        ?: listOf("https://api.mainnet-beta.solana.com", "https://solana-rpc.publicnode.com")
    /** Background indexing on/off (tests and one-shot runs turn it off). */
    val INDEX: Boolean = System.getenv("UNDERSIGNED_INDEX") != "0"

    const val REGISTRY = Registry.REGISTRY_REFERENCE
    const val MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    const val TREASURY = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
    const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
    const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    const val SGT_MINT_AUTHORITY = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"
    const val PAYMENT_MEMO = "UNS"
    /**
     * The API pass's own reference account (a bare address; nobody holds its key, none is needed). Payments carrying it
     * are API passes and nothing else: the app reads only registry-referenced transactions, so a pass is never a Pro
     * licence or a listing fee. Minted 28 Sep 2026. FROZEN once a pass is sold.
     */
    const val API_REFERENCE = "8j4SnnJaR5DUb7cqvqRf7Bq7PTwKQg1m223VsZf1avtf"

    // ---- The pass: the fleet's paid-tier price (owner, 28 Sep 2026) -------------------------------------------
    const val PASS_SOL = 0.0607
    const val PASS_SKR = 651.0
    const val PASS_SKR_SGT = 586.0
    val LEGACY_PASS_SOL: List<Double> = emptyList()
    val LEGACY_PASS_SKR: List<Double> = emptyList()
    const val PASS_REQUESTS = 100_000L
    const val PASS_DAYS = 30L
    const val PASS_PER_MINUTE = 600
    const val FREE_PER_MINUTE = 30
    const val FREE_PER_DAY = 1_000
    /** Free endpoints that list things show this many rows; a pass shows all. */
    const val FREE_ROWS = 20
    /** Pages and badges: generous, because a badge on a busy site is fetched often (and cached an hour). */
    const val WEB_PER_MINUTE = 120
    const val WEB_PER_DAY = 20_000
    /** Where this server is reached from outside, for links in pages and badge embed snippets. */
    val PUBLIC_URL: String = (System.getenv("PUBLIC_URL") ?: "http://127.0.0.1:${System.getenv("PORT")?.toIntOrNull() ?: 8797}").trimEnd('/')
    const val FREE_TOP = 10

    fun lamports(sol: Double): Long = Math.round(sol * 1_000_000_000.0)
    fun skrRaw(skr: Double): Long = Math.round(skr * 1_000_000.0)
    /**
     * Test prices for a local test run (owner, 28 Sep 2026: "one test run for 1 SKR and then 0.0001 SOL"): set
     * UNDERSIGNED_TEST_PASS_SOL / UNDERSIGNED_TEST_PASS_SKR. Honoured ONLY while the server listens on loopback, so a
     * server that serves a network always charges the real price; unset them and the real price is back.
     */
    private fun testPrice(name: String): Double? = if (HOST == "127.0.0.1" || HOST == "localhost") System.getenv(name)?.toDoubleOrNull()?.takeIf { it > 0 } else null
    val TEST_SOL: Double? = testPrice("UNDERSIGNED_TEST_PASS_SOL")
    val TEST_SKR: Double? = testPrice("UNDERSIGNED_TEST_PASS_SKR")
    val testPrices: Boolean get() = TEST_SOL != null || TEST_SKR != null
    val passSol: Double get() = TEST_SOL ?: PASS_SOL
    val passSkr: Double get() = TEST_SKR ?: PASS_SKR
    val passSkrSgt: Double get() = TEST_SKR ?: PASS_SKR_SGT
    val passFloorLamports: Long get() = (LEGACY_PASS_SOL + passSol).minOf { lamports(it) }
    /** The full SKR price; the SGT price is accepted only from a wallet that holds a Seeker Genesis Token. */
    val passFloorSkrRaw: Long get() = (LEGACY_PASS_SKR + passSkr).minOf { skrRaw(it) }
    val passFloorSkrSgtRaw: Long get() = (LEGACY_PASS_SKR + passSkrSgt).minOf { skrRaw(it) }

    // ---- Store --------------------------------------------------------------------------------------------------
    const val STORE_GRAPHQL = "https://dappstore.solanamobile.com/graphql"
    /** The store's CDN blocks a whole network for minutes after a burst of reads; every store request is paced. */
    const val STORE_PACE_MS = 1_500L
    const val STORE_BATCH = 20
    const val CATALOGUE_EVERY_MS = 24L * 3600 * 1000
    const val LEDGER_EVERY_MS = 5L * 60 * 1000
    const val FACTS_TTL_MS = 6L * 3600 * 1000
}
