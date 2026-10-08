package com.undersigned.app

import com.undersigned.protocol.Registry

/**
 * Compile-time configuration for Undersigned: a sybil-resistant, on-chain review and trust layer
 * for the Solana dApp Store. No server, no token, no fees, no custody. Every review is a memo
 * transaction the reviewer signs in their own wallet; ranking is deterministic from the chain.
 */
object AppConfig {
    const val APP_NAME = "Undersigned"

    object Identity {
        // Trailing slash matters: the wallet resolves ICON_PATH relative to URI. ICON_PATH must stay
        // RELATIVE — an absolute iconUri has broken MWA sign-in in this fleet. Legal site is pushed LAST.
        const val URI = "https://aardappvark.github.io/undersigned-legal/"
        const val ICON_PATH = "favicon.png"
        const val NAME = "Undersigned"
        const val HOST = "aardappvark.github.io"
    }

    object Api {
        private val PUBLIC_ENDPOINTS = listOf(
            "https://api.mainnet-beta.solana.com",
            "https://solana-rpc.publicnode.com",
        )
        /** Own keyed provider, then public pools, then the fleet last-resort key appended LAST. */
        @Volatile var RPC_ENDPOINTS: List<String> = PUBLIC_ENDPOINTS
            private set
        fun configure(primaryKeyed: String?, lastResortKeyed: String?) {
            RPC_ENDPOINTS = listOfNotNull(primaryKeyed) + PUBLIC_ENDPOINTS + listOfNotNull(lastResortKeyed)
        }
        const val EXPLORER_TX = "https://solscan.io/tx/"
    }

    object Ledger {
        const val REGISTRY = Registry.REGISTRY_REFERENCE
        const val PROTOCOL = Registry.PROTOCOL
        /** Signatures per getSignaturesForAddress page (RPC max). */
        const val PAGE = 1000
        /** Signer facts (Genesis Token, staked SKR) are re-read after this long. */
        const val FACTS_TTL_MS = 6L * 3600L * 1000L
        /** How long we poll for confirmation after the wallet reports a signature. */
        const val CONFIRM_TIMEOUT_MS = 45_000L
    }

    object Solana {
        const val MEMO_PROGRAM = Registry.MEMO_PROGRAM
        /** The Seeker wallet appends a Lighthouse assertion + ComputeBudget to every tx it signs (observed on the owner's reviews, 8 Sep 2026). */
        const val LIGHTHOUSE_PROGRAM = "L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95"
        const val COMPUTE_BUDGET_PROGRAM = "ComputeBudget111111111111111111111111111111"
        const val SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"
        /** SKR staking program + config + Solana Mobile guardian pool (SeekerVerify `StakingRpcClient` layout). */
        const val SKR_STAKING_PROGRAM = "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ"
        const val SKR_STAKE_CONFIG = "4HQy82s9CHTv1GsYKnANHMiHfhcqesYkK6sB3RDSYyqw"
        const val GUARDIAN_POOL_SOLANA_MOBILE = "DPJ58trLsF9yPrBa2pk6UaRkvqW8hWUYjawe788WBuqr"
        const val SKR_DECIMALS_DIVISOR = 1_000_000.0
        const val SHARE_PRICE_PRECISION = 1_000_000_000L
        const val USER_STAKE_SIZE = 169
        const val USER_STAKE_USER_OFFSET = 41
        const val USER_STAKE_SHARES_OFFSET = 105
        const val STAKE_CONFIG_SHARE_PRICE_OFFSET = 137
        const val MAX_TX_BYTES = 1232
    }

    object Store {
        const val GRAPHQL = "https://dappstore.solanamobile.com/graphql"
        /** The store's publisher portal records, public on Arweave: finds listings that sit in no category. */
        const val ARWEAVE_GRAPHQL = "https://arweave.net/graphql"
        const val DEEP_LINK = "solanadappstore://details?id="
        /** Bundled catalogue snapshot (package, name, updated, category) refreshed in the background. */
        const val SNAPSHOT_ASSET = "catalogue_snapshot.json"
        const val REFRESH_INTERVAL_MS = 24L * 3600L * 1000L
        const val PAGE_SIZE = 20
    }

    /**
     * Pro: a 12-month software licence for tools around the signing (copy reviews to the store, import all
     * store reviews in one signature, the publisher desk, ledger export, hourly certificate watch). It buys
     * tools, never standing: weight comes from the attested device and staked SKR alone. Fleet payment shape:
     * transfer to the shared treasury with a reference account on the transfer and the app's 3-letter memo
     * appended LAST; the licence record (`SRL1|pro|12`) rides in the same transaction.
     */
    object Payment {
        /** Shared MidMightBit treasury. The SOL rail pays the wallet; the SKR rail pays its associated token account. */
        const val TREASURY = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val ATA_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
        const val SKR_MINT = Solana.SKR_MINT
        const val SKR_DECIMALS = 6
        /** The registry PDA doubles as this app's fleet payment reference, so a licence is found by the same read as every other record. */
        const val PAYMENT_REFERENCE = Ledger.REGISTRY
        /** Fleet registry code UNS (fleet-config/MEMOS.md). Appended LAST. FROZEN once a payment is taken. */
        const val PAYMENT_MEMO = "UNS"
        /**
         * One 12-month tier, fleet standard pair (SOL = USD 5.99 equivalent, SKR 10% under the SOL leg as charged).
         * Re-peg ONLY with fleet-config/tools/reprice.py immediately before a release build; never hand-edit.
         * Raising either leg later REVOKES buyers unless the old amount goes into the LEGACY lists first.
         */
        const val PRO_SOL = 0.0607
        /** 651 SKR from 28 Sep 2026 (owner: the fleet price). 276 was the price until then and stays a floor, so the licence bought at it on 15 Sep reads. */
        const val PRO_SKR = 651.0
        val LEGACY_PRO_SOL: List<Double> = emptyList()
        val LEGACY_PRO_SKR: List<Double> = listOf(276.0)
        const val TERM_MONTHS = 12
        /** Math.round, never toLong(): a truncated transfer would be one unit short of its own floor. */
        fun lamports(sol: Double): Long = Math.round(sol * 1_000_000_000.0)
        fun skrRaw(skr: Double): Long = Math.round(skr * 1_000_000.0)
        /**
         * Whether a transaction's treasury credit buys a term. This is the whole licence test: a payment at or
         * above the floor on either rail, carrying this app's memo and reference. Nothing in a memo says it.
         */
        fun isPaidLicence(paidLamports: Long, paidSkr: Long): Boolean =
            paidLamports >= floorLamports || paidSkr >= floorSkrRaw

        /** The lowest amount a reader accepts as a paid term: today's price or any legacy price, whichever is lower. Floors are never raised. */
        val floorLamports: Long get() = (LEGACY_PRO_SOL + PRO_SOL).minOf { lamports(it) }
        val floorSkrRaw: Long get() = (LEGACY_PRO_SKR + PRO_SKR).minOf { skrRaw(it) }
        /** Fee plus a little headroom, so a wallet holding exactly the price is not sent to a failing sheet. */
        const val FEE_HEADROOM_LAMPORTS = 1_000_000L
        const val CONFIRM_TIMEOUT_MS = 90_000L
        fun solLabel(): String = "$PRO_SOL SOL"
        fun skrLabel(): String = String.format(java.util.Locale.US, "%,.0f SKR", PRO_SKR)

        /**
         * Listing a treasury on the Payments board: the fleet's standard paid-tier pair (owner, 28 Sep 2026 — "the same
         * SKR SOL price as the rest of our dApps": 0.0607 SOL, 651 SKR, 586 SKR for Seeker Genesis Token holders). Paid
         * once per (treasury, package), in the SAME transaction as the `trs` record, to [TREASURY] with the registry as
         * the reference and UNS as the first memo (so fleet ledger tools attribute it). A transaction carrying an SRL1
         * record is never read as a Pro licence (see LedgerReader), so the two cannot be confused.
         * Same rule as Pro: re-peg only with fleet-config/tools/reprice.py, and never raise a leg without moving the old
         * amount into the LEGACY list first, or earlier listings stop counting.
         */
        const val LIST_SOL = 0.0607
        const val LIST_SKR = 651.0
        const val LIST_SKR_SGT = 586.0
        val LEGACY_LIST_SOL: List<Double> = emptyList()
        val LEGACY_LIST_SKR: List<Double> = emptyList()
        /** The reader's floor is the lowest price ever charged on each rail, SGT price included. */
        val listFloorLamports: Long get() = (LEGACY_LIST_SOL + LIST_SOL).minOf { lamports(it) }
        val listFloorSkrRaw: Long get() = (LEGACY_LIST_SKR + LIST_SKR + LIST_SKR_SGT).minOf { skrRaw(it) }
        fun isPaidListing(paidLamports: Long, paidSkr: Long): Boolean = paidLamports >= listFloorLamports || paidSkr >= listFloorSkrRaw
        /**
         * A boost on one's own review, Pro members only (owner, 8 Oct 2026): a tenth of the Pro price. It adds ×1.2 to that
         * review's weight on top of Pro's ×1.25, the two capped at ×1.5 (protocol Weighting); shown as "Boosted" on the review.
         */
        const val BOOST_SOL = 0.00607
        const val BOOST_SKR = 65.0
        val boostFloorLamports: Long get() = lamports(BOOST_SOL)
        val boostFloorSkrRaw: Long get() = skrRaw(BOOST_SKR)
        fun isPaidBoost(paidLamports: Long, paidSkr: Long): Boolean = paidLamports >= boostFloorLamports || paidSkr >= boostFloorSkrRaw
        fun listSkr(holdsGenesisToken: Boolean): Double = if (holdsGenesisToken) LIST_SKR_SGT else LIST_SKR
    }

    /** The public website and API (served from the owner's Mac by Cloudflare Tunnel since 7 Oct 2026). Badges and share links point here. */
    object Site {
        const val URL = "https://undersigned.provenby.dev"
        fun badgeEmbed(pkg: String) = "<a href=\"$URL/app/$pkg\"><img src=\"$URL/v1/badge/$pkg.svg\" alt=\"Undersigned signed reviews\"></a>"
    }

    object Features {
        const val WALLET_SCREENING_ENABLED = true
    }
}
