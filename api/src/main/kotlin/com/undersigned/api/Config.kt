package com.undersigned.api

import com.undersigned.protocol.Registry

/**
 * Everything tunable in one place. Money constants follow the fleet rule (docs/API.md §2): re-peg only with
 * fleet-config/tools/reprice.py before a release, and never raise a leg without keeping the old amount as a floor.
 */
object Config {
    val PORT: Int = System.getenv("PORT")?.toIntOrNull() ?: 8817
    /**
     * Serving the public (behind a proxy or tunnel, or on a network): one explicit switch. It turns OFF everything that is
     * only right on a private machine: test prices, http://localhost webhooks. A proxy in front means TRUST_PROXY=1, which
     * implies public. HOST alone cannot tell (a tunnel forwards to 127.0.0.1).
     */
    val TRUST_PROXY: Boolean = System.getenv("TRUST_PROXY") == "1"
    val PUBLIC: Boolean = System.getenv("UNDERSIGNED_PUBLIC") == "1" || TRUST_PROXY
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
    const val PASS_SKR = 369.0
    const val PASS_SKR_SGT = 332.0
    val LEGACY_PASS_SOL: List<Double> = emptyList()
    val LEGACY_PASS_SKR: List<Double> = emptyList()
    const val PASS_REQUESTS = 100_000L
    /**
     * Pay per call for agents (owner, 9 Oct 2026: "keep pay per call for agents, they can pay if they use"): any payment
     * from the minimum up to just under the pass price buys calls at this rate, valid a year, kept apart from passes.
     * SKR is 10% below SOL at the 9 Oct 2026 rate (SOL $110.68, SKR $0.01639).
     */
    const val PER_CALL_SOL = 0.00001
    const val PER_CALL_SKR = 0.06
    const val CREDITS_MIN_SOL = 0.001
    const val CREDITS_MIN_SKR = 6.0
    const val CREDITS_DAYS = 365L
    val perCallLamports: Long get() = lamports(PER_CALL_SOL)
    val perCallSkrRaw: Long get() = skrRaw(PER_CALL_SKR)
    const val PASS_DAYS = 30L
    const val PASS_PER_MINUTE = 600
    const val FREE_PER_MINUTE = 30
    const val FREE_PER_DAY = 1_000
    /** Free endpoints that list things show this many rows; a pass shows all. */
    const val FREE_ROWS = 20
    /** Pages and badges: generous, because a badge on a busy site is fetched often (and cached an hour). */
    const val WEB_PER_MINUTE = 120
    const val WEB_PER_DAY = 20_000
    /** Who to write to about content shown on the pages (an address or a URL). Shown in every page footer and on /report. */
    val CONTACT: String = System.getenv("UNDERSIGNED_CONTACT") ?: "https://aardappvark.github.io/undersigned-legal/"
    /** Where this server is reached from outside, for links in pages and badge embed snippets. */
    /** Claude for the reading aids (Ai.kt): the key from the environment or the owner's private file; absent = features off. */
    val ANTHROPIC_KEY: String? = System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() } ?: runCatching {
        java.io.File(System.getProperty("user.home"), ".config/undersigned/anthropic.env").readLines().firstOrNull { it.startsWith("ANTHROPIC_API_KEY=") }?.substringAfter('=')?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
    const val AI_MODEL = "claude-haiku-5-5"
    /**
     * Translations try the local model first (owner, 9 Oct 2026: "better with ollama first"): free, about 8 s each on this
     * Mac (gemma4, measured), then Claude when it is busy or down. The Ollama server is shared with other work: never kill it.
     */
    val OLLAMA_URL: String = System.getenv("UNDERSIGNED_OLLAMA") ?: "http://127.0.0.1:11434"
    const val OLLAMA_MODEL = "gemma4:latest"
    /** A ceiling on model calls a day across everything, so no caller can run up the bill; answers are cached. */
    const val AI_DAILY_CALLS = 2_000
    /** A suggested summary needs at least this many counted reviews with text. */
    const val AI_SUMMARY_MIN = 3
    /** New change notes written per /v1/changes request at most (older ones come from the cache or stay without). */
    const val AI_NOTES_PER_REQUEST = 10
    /** Languages a suggested translation can target: the app's 28. */
    val AI_LANGS = setOf("en", "de", "fr", "es", "it", "pt", "nl", "sv", "pl", "cs", "hu", "ro", "ru", "uk", "el", "tr", "ar", "ur", "hi", "bn", "id", "vi", "th", "ja", "ko", "zh-CN", "zh-TW", "fil")
    val PUBLIC_URL: String = (System.getenv("PUBLIC_URL") ?: "http://127.0.0.1:${System.getenv("PORT")?.toIntOrNull() ?: 8817}").trimEnd('/')
    const val FREE_TOP = 10

    fun lamports(sol: Double): Long = Math.round(sol * 1_000_000_000.0)
    fun skrRaw(skr: Double): Long = Math.round(skr * 1_000_000.0)
    /**
     * Test prices for a local test run (owner, 28 Sep 2026: "one test run for 1 SKR and then 0.0001 SOL"): set
     * UNDERSIGNED_TEST_PASS_SOL / UNDERSIGNED_TEST_PASS_SKR. Honoured ONLY while the server listens on loopback, so a
     * server that serves a network always charges the real price; unset them and the real price is back.
     */
    fun testPrice(value: String?, host: String = HOST, public: Boolean = PUBLIC): Double? =
        if (!public && (host == "127.0.0.1" || host == "localhost")) value?.toDoubleOrNull()?.takeIf { it > 0 } else null
    val TEST_SOL: Double? = testPrice(System.getenv("UNDERSIGNED_TEST_PASS_SOL"))
    val TEST_SKR: Double? = testPrice(System.getenv("UNDERSIGNED_TEST_PASS_SKR"))
    /** Refuses to start public with test prices in the environment: the real price must be deliberate, not a leftover. */
    fun checkStartup() {
        if (PUBLIC && (System.getenv("UNDERSIGNED_TEST_PASS_SOL") != null || System.getenv("UNDERSIGNED_TEST_PASS_SKR") != null))
            error("UNDERSIGNED_TEST_PASS_* is set while serving the public; unset it")
    }
    /** http://localhost webhooks: only on a private loopback server, for testing. */
    val HOOK_LOCAL_OK: Boolean get() = !PUBLIC && (HOST == "127.0.0.1" || HOST == "localhost")

    /** Bounded reads of other people's bytes. */
    const val MAX_PROOF_BYTES = 64 * 1024
    const val MAX_APK_BYTES = 300L * 1024 * 1024
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
    /** Fresh Claude answers per UTC day: per pass key, per Pro wallet, per anonymous IP. Per-call keys pay one extra call each. */
    const val AI_PER_PASS_DAY = 200
    const val AI_PER_PRO_WALLET_DAY = 50
    const val AI_PER_IP_DAY = 20
    /** Claude Haiku 5.5 list prices, USD per million tokens (prompts up to 100K tokens), for the measured cost in /v1/health. */
    const val AI_PRICE_IN = 0.10
    const val AI_PRICE_OUT = 0.50
    const val CATALOGUE_EVERY_MS = 24L * 3600 * 1000
    const val LEDGER_EVERY_MS = 5L * 60 * 1000
    const val FACTS_TTL_MS = 6L * 3600 * 1000
}
