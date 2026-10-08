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

    /**
     * An UNKNOWN answer is never stored as "no Genesis Token" (debug 9 Oct 2026): when every endpoint refused the lookup
     * (Helius answered 429 on the emulator), the old code cached `false` for six hours and every review that wallet signed
     * read "not counted" until it expired. Now the last answer actually read stands, and nothing is cached, so the next
     * ranking asks again.
     */
    suspend fun facts(signer: String, force: Boolean = false): SignerFacts =
        factsOrNull(signer, force) ?: UserPreferences.cachedFacts(signer, stale = true) ?: SignerFacts(false, 0.0)

    /** The facts, or null when they could not be read this time (callers retry; the last answer read stands meanwhile). */
    suspend fun factsOrNull(signer: String, force: Boolean = false): SignerFacts? {
        if (!force) UserPreferences.cachedFacts(signer)?.let { return it }
        val att = attestationOf(signer)
        if (att is Lookup.Unknown) return null
        val id = (att as Lookup.Found).id
        val stake = if (id != null) (stakedSkr(signer) ?: return null) else 0.0
        // Staked SOL is shown for transparency only (never weighed): a failed read leaves it unknown, it never blocks the facts.
        val sol = if (id != null) rpc.getStakeAccounts(signer).getOrNull()?.let { com.undersigned.protocol.StakeView.solStaked(it) } else null
        val f = SignerFacts(id != null, stake, id, stakedSol = sol)
        UserPreferences.cacheFacts(signer, f)
        return f
    }

    /** A lookup that completed (with an attestation id, or null for none), or one that could not be completed. */
    private sealed class Lookup { data class Found(val id: String?) : Lookup(); object Unknown : Lookup() }

    /** First matching attestation id, or null. Genesis goes through the fleet library; other entries through a generic Token-2022 mint-authority check. */
    private suspend fun attestationOf(address: String): Lookup {
        var unknown = false
        for (a in attestations.ifEmpty { listOf(Attestation("seeker-genesis", "Seeker Genesis Token", com.midmightbit.sgt.SgtConstants.SGT_MINT_AUTHORITY, com.midmightbit.sgt.SgtConstants.SGT_METADATA_ADDRESS)) }) {
            val hit = if (a.id == "seeker-genesis") hasGenesisToken(address) else holdsSoulboundFrom(address, a.mintAuthority)
            if (hit == true) return Lookup.Found(a.id)
            if (hit == null) unknown = true
        }
        return if (unknown) Lookup.Unknown else Lookup.Found(null)
    }

    /**
     * Does [address] hold (amount ≥ 1) a Token-2022 token whose mint authority is [mintAuthority]? The generic form of
     * the Genesis check. Layouts: token account = mint[0..32] owner[32..64] amount u64 LE[64..72]; mint account =
     * COption<Pubkey> mint_authority = u32 tag[0..4] (1 = Some) + pubkey[4..36].
     */
    private suspend fun holdsSoulboundFrom(address: String, mintAuthority: String): Boolean? {
        val accounts = rpc.getToken2022AccountsBase64(address).getOrNull() ?: return null
        val mints = accounts.mapNotNull { (_, data) ->
            if (data.size < 72) return@mapNotNull null
            val amount = ByteBuffer.wrap(data, 64, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (amount <= 0L) null else Base58.encode(data.copyOfRange(0, 32))
        }.distinct()
        for (m in mints) {
            val read = rpc.getAccountDataBase64(m)
            if (read.isFailure) return null          // could not read: unknown
            val mintData = read.getOrNull() ?: continue   // no such account: not this one
            if (mintData.size < 36) continue
            val tag = ByteBuffer.wrap(mintData, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (tag == 1 && Base58.encode(mintData.copyOfRange(4, 36)) == mintAuthority) return true
        }
        return false
    }

    /** seeker-verify does its own RPC; try each endpoint of the pool in order (public pools may refuse getTokenAccountsByOwner). */
    private suspend fun hasGenesisToken(address: String): Boolean? = withContext(Dispatchers.IO) {
        for (url in AppConfig.Api.RPC_ENDPOINTS) {
            val r = SgtChecker.getWalletSgtInfo(address, url)
            if (r.isSuccess) return@withContext r.getOrThrow().hasSgt
            Log.w(TAG, "SGT lookup failed on ${url.substringBefore("?")}: ${r.exceptionOrNull()?.message?.take(80)}")
        }
        null   // every endpoint refused: unknown, never "no" (see facts)
    }

    /** Sum of UserStake positions across guardian pools (SeekerVerify layout), valued at the current share price. PDA fallback. */
    /** Null when the stake could not be read; 0.0 only when it was read and is empty. */
    private suspend fun stakedSkr(address: String): Double? {
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

    private suspend fun pdaFallback(address: String, sharePrice: Long): Double? {
        val seeds = listOf("user_stake".toByteArray(), Base58.decode(AppConfig.Solana.SKR_STAKE_CONFIG), Base58.decode(address), Base58.decode(AppConfig.Solana.GUARDIAN_POOL_SOLANA_MOBILE))
        val pda = runCatching { Pda.find(seeds, Base58.decode(AppConfig.Solana.SKR_STAKING_PROGRAM)) }.getOrNull() ?: return 0.0
        val read = rpc.getAccountDataBase64(Base58.encode(pda))
        if (read.isFailure) return null                  // could not read: unknown, not "no stake"
        val data = read.getOrNull() ?: return 0.0        // no stake account: nothing staked
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
