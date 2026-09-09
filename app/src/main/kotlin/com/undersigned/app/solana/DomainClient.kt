package com.undersigned.app.solana

import com.undersigned.app.data.UserPreferences
import com.undersigned.app.util.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A wallet's `.skr` name, read from the AllDomains MainDomain PDA: seeds ["main_domain", wallet] under
 * the TLD House program, layout 8 disc · 32 nameAccount · borsh tld · borsh domain. Cached on-device,
 * including "no name", so a bare wallet is not re-queried on every list scroll.
 *
 * **Names only appear for wallets that have SET a main domain, and most have not.** An owned-name scan
 * was tried as a fallback and removed, because it cannot produce a readable string (measured against
 * airdropper's wallet, 9 Sep 2026):
 *
 *  - the wallet owns exactly one ANS name account (owner at offset 40, so the filter was right);
 *  - that account is exactly 200 bytes — the header, with no name string after it;
 *  - its reverse-lookup record, the PDA of SHA-256("ALT Name Service" + name account) under ANS, does
 *    not exist, so there is nothing to decode.
 *
 * The dApp Store shows `.skr` names on its own reviews because its backend indexes them; that is not
 * derivable on-chain from the client. Resolving names for arbitrary signers would need an indexer, so
 * the app shows a shortened address instead, which is honest and costs one lookup rather than three.
 */
class DomainClient(private val rpc: SolanaRpcClient = SolanaRpcClient()) {

    suspend fun mainDomain(wallet: String): String? {
        UserPreferences.cachedDomain(wallet)?.let { return it.takeIf { d -> d.isNotEmpty() } }
        val name = runCatching { lookupMain(wallet) }.getOrNull()
        UserPreferences.cacheDomain(wallet, name ?: "")
        return name
    }

    private suspend fun lookupMain(wallet: String): String? {
        val pda = Pda.find(listOf("main_domain".toByteArray(), Base58.decode(wallet)), Base58.decode(TLD_HOUSE))
        val data = rpc.getAccountDataBase64(Base58.encode(pda)).getOrNull() ?: return null
        if (data.size < 44) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val tldLen = buf.getInt(40)
        if (tldLen <= 0 || tldLen > 32 || 44 + tldLen > data.size) return null
        val tld = String(data, 44, tldLen)
        val dOff = 44 + tldLen
        if (dOff + 4 > data.size) return null
        val dLen = buf.getInt(dOff)
        if (dLen <= 0 || dLen > 64 || dOff + 4 + dLen > data.size) return null
        return String(data, dOff + 4, dLen) + "." + tld
    }

    companion object {
        const val TLD_HOUSE = "TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S"
    }
}
