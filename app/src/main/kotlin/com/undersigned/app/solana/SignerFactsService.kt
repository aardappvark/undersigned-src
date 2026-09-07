package com.undersigned.app.solana

import android.util.Log
import com.midmightbit.sgt.SgtChecker
import com.undersigned.app.AppConfig
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.util.Base58
import com.undersigned.protocol.SignerFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * READ-ONLY facts about a signer, from the chain: does the wallet hold a Seeker Genesis Token, and
 * how much SKR does it have staked. Both are lookups; nothing is staked, moved or suggested.
 * Cached per signer for [AppConfig.Ledger.FACTS_TTL_MS] so ranking a ledger of N reviews costs
 * O(distinct signers) RPC calls, not O(N).
 */
class SignerFactsService(private val rpc: SolanaRpcClient = SolanaRpcClient()) {

    suspend fun facts(signer: String, force: Boolean = false): SignerFacts {
        if (!force) UserPreferences.cachedFacts(signer)?.let { return it }
        val sgt = hasGenesisToken(signer)
        val stake = if (sgt) stakedSkr(signer) else 0.0   // no-token wallets weigh zero; skip the second lookup
        val f = SignerFacts(sgt, stake)
        UserPreferences.cacheFacts(signer, f)
        return f
    }

    /** seeker-verify does its own RPC; try each endpoint of the pool in order (public pools may refuse getTokenAccountsByOwner). */
    private suspend fun hasGenesisToken(address: String): Boolean = withContext(Dispatchers.IO) {
        for (url in AppConfig.Api.RPC_ENDPOINTS) {
            val r = SgtChecker.getWalletSgtInfo(address, url)
            if (r.isSuccess) return@withContext r.getOrThrow().hasSgt
            Log.w(TAG, "SGT lookup failed on ${url.substringBefore("?")}: ${r.exceptionOrNull()?.message?.take(80)}")
        }
        false   // unknown → not counted (conservative); the cache TTL retries later
    }

    /** Sum of UserStake positions across guardian pools (SeekerVerify layout), valued at the current share price. PDA fallback. */
    private suspend fun stakedSkr(address: String): Double {
        val sharePrice = sharePrice()
        val filters = JSONArray()
            .put(JSONObject().put("dataSize", AppConfig.Solana.USER_STAKE_SIZE))
            .put(JSONObject().put("memcmp", JSONObject().put("offset", AppConfig.Solana.USER_STAKE_USER_OFFSET).put("bytes", address)))
        rpc.getProgramAccountsBase64(AppConfig.Solana.SKR_STAKING_PROGRAM, filters).onSuccess { accounts ->
            if (accounts.isEmpty()) return pdaFallback(address, sharePrice)
            return accounts.sumOf { (_, data) -> valueOf(data, sharePrice) }
        }
        return pdaFallback(address, sharePrice)
    }

    private suspend fun pdaFallback(address: String, sharePrice: Long): Double {
        val seeds = listOf("user_stake".toByteArray(), Base58.decode(AppConfig.Solana.SKR_STAKE_CONFIG), Base58.decode(address), Base58.decode(AppConfig.Solana.GUARDIAN_POOL_SOLANA_MOBILE))
        val pda = runCatching { Pda.find(seeds, Base58.decode(AppConfig.Solana.SKR_STAKING_PROGRAM)) }.getOrNull() ?: return 0.0
        val data = rpc.getAccountDataBase64(Base58.encode(pda)).getOrNull() ?: return 0.0
        return valueOf(data, sharePrice)
    }

    private fun valueOf(data: ByteArray, sharePrice: Long): Double {
        if (data.size < AppConfig.Solana.USER_STAKE_SIZE) return 0.0
        val shares = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(AppConfig.Solana.USER_STAKE_SHARES_OFFSET)   // u128 LE low word
        if (shares <= 0L || sharePrice <= 0L) return 0.0
        val raw = (shares.toBigInteger() * sharePrice.toBigInteger() / AppConfig.Solana.SHARE_PRICE_PRECISION.toBigInteger()).toLong()
        return raw / AppConfig.Solana.SKR_DECIMALS_DIVISOR
    }

    private suspend fun sharePrice(): Long {
        val data = rpc.getAccountDataBase64(AppConfig.Solana.SKR_STAKE_CONFIG).getOrNull() ?: return AppConfig.Solana.SHARE_PRICE_PRECISION
        if (data.size < AppConfig.Solana.STAKE_CONFIG_SHARE_PRICE_OFFSET + 16) return AppConfig.Solana.SHARE_PRICE_PRECISION
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(AppConfig.Solana.STAKE_CONFIG_SHARE_PRICE_OFFSET).takeIf { it > 0 } ?: AppConfig.Solana.SHARE_PRICE_PRECISION
    }

    companion object { private const val TAG = "SignerFacts" }
}
