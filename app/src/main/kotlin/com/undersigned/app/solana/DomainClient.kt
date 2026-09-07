package com.undersigned.app.solana

import com.undersigned.app.data.UserPreferences
import com.undersigned.app.util.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A wallet's main `.skr` name, read from the AllDomains MainDomain PDA
 * (seeds ["main_domain", wallet] under the TLD House program) — the SeekerVerify `DomainRpcClient`
 * layout: 8 disc · 32 nameAccount · borsh string tld · borsh string domain. Cached on-device; null
 * (no main domain) is cached too so a bare wallet is not re-queried every list scroll.
 */
class DomainClient(private val rpc: SolanaRpcClient = SolanaRpcClient()) {
    suspend fun mainDomain(wallet: String): String? {
        UserPreferences.cachedDomain(wallet)?.let { return it.takeIf { d -> d.isNotEmpty() } }
        val name = runCatching { lookup(wallet) }.getOrNull()
        UserPreferences.cacheDomain(wallet, name ?: "")
        return name
    }

    private suspend fun lookup(wallet: String): String? {
        val pda = Pda.find(listOf("main_domain".toByteArray(), Base58.decode(wallet)), Base58.decode(TLD_HOUSE))
        val data = rpc.getAccountDataBase64(Base58.encode(pda)).getOrNull() ?: return null
        if (data.size < 44) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val tldLen = buf.getInt(40); if (tldLen <= 0 || tldLen > 32 || 44 + tldLen > data.size) return null
        val tld = String(data, 44, tldLen)
        val dOff = 44 + tldLen; if (dOff + 4 > data.size) return null
        val dLen = buf.getInt(dOff); if (dLen <= 0 || dLen > 64 || dOff + 4 + dLen > data.size) return null
        return String(data, dOff + 4, dLen) + "." + tld
    }

    companion object { const val TLD_HOUSE = "TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S" }
}
