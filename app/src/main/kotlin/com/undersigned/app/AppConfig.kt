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
        const val DEEP_LINK = "solanadappstore://details?id="
        /** Bundled catalogue snapshot (package, name, updated, category) refreshed in the background. */
        const val SNAPSHOT_ASSET = "catalogue_snapshot.json"
        const val REFRESH_INTERVAL_MS = 24L * 3600L * 1000L
        const val PAGE_SIZE = 20
    }

    object Features {
        const val WALLET_SCREENING_ENABLED = true
    }
}
