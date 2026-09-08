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
class SignerFactsService(private val rpc: SolanaRpcClient = SolanaRpcClient(), private val attestations: List<Attestation> = emptyList()) {

    suspend fun facts(signer: String, force: Boolean = false): SignerFacts {
        if (!force) UserPreferences.cachedFacts(signer)?.let { return it }
        val att = attestationOf(signer)
        val stake = if (att != null) stakedSkr(signer) else 0.0   // unattested wallets weigh zero; skip the second lookup
        val f = SignerFacts(att != null, stake, att)
        UserPreferences.cacheFacts(signer, f)
        return f
    }

    /** First matching attestation id, or null. Genesis goes through the fleet library; other entries through a generic Token-2022 mint-authority check. */
    private suspend fun attestationOf(address: String): String? {
        for (a in attestations.ifEmpty { listOf(Attestation("seeker-genesis", "Seeker Genesis Token", com.midmightbit.sgt.SgtConstants.SGT_MINT_AUTHORITY, com.midmightbit.sgt.SgtConstants.SGT_METADATA_ADDRESS)) }) {
            val hit = if (a.id == "seeker-genesis") hasGenesisToken(address) else holdsSoulboundFrom(address, a.mintAuthority)
            if (hit) return a.id
        }
        return null
    }

    /**
     * Does [address] hold (amount ≥ 1) a Token-2022 token whose mint authority is [mintAuthority]? The generic form of
     * the Genesis check. Layouts: token account = mint[0..32] owner[32..64] amount u64 LE[64..72]; mint account =
     * COption<Pubkey> mint_authority = u32 tag[0..4] (1 = Some) + pubkey[4..36].
     */
    private suspend fun holdsSoulboundFrom(address: String, mintAuthority: String): Boolean {
        val accounts = rpc.getToken2022AccountsBase64(address).getOrNull() ?: return false
        val mints = accounts.mapNotNull { (_, data) ->
            if (data.size < 72) return@mapNotNull null
            val amount = ByteBuffer.wrap(data, 64, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (amount <= 0L) null else Base58.encode(data.copyOfRange(0, 32))
        }.distinct()
        for (m in mints) {
            val mintData = rpc.getAccountDataBase64(m).getOrNull() ?: continue
            if (mintData.size < 36) continue
            val tag = ByteBuffer.wrap(mintData, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (tag == 1 && Base58.encode(mintData.copyOfRange(4, 36)) == mintAuthority) return true
        }
        return false
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
