package com.undersigned.app.solana

import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * The Pro payment carriers: fit the wire with headroom for the wallet's ComputeBudget rewrite, derive the
 * treasury's SKR account exactly as the chain reports it, and dump their bytes so the fleet gate can
 * simulate the SAME bytes on mainnet (must pass) and devnet (must fail) before any owner sees a sheet.
 */
class ProTxShapeTest {
    private val signer = Base58.decode("HVXVfQDD1jrt6kBFRNDFcftDE2usWbyWzEDWs367yDaD")

    @Test fun `treasury SKR account derives to the address mainnet reports`() {
        assertEquals("GcV5XyhuaScv5Rp77RjpsJiGUaSFjtGdA8c4nUrsDFLk", Base58.encode(TxBuilder.associatedTokenAddress(TxBuilder.TREASURY, TxBuilder.SKR_MINT)))
    }

    @Test fun `price rounding never lands one unit under the floor`() {
        assertTrue(AppConfig.Payment.lamports(AppConfig.Payment.PRO_SOL) >= AppConfig.Payment.floorLamports)
        assertTrue(AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR) >= AppConfig.Payment.floorSkrRaw)
        assertEquals(60_700_000L, AppConfig.Payment.lamports(0.0607))
        assertEquals(276_000_000L, AppConfig.Payment.skrRaw(276.0))
    }

    /**
     * The fleet convention, checked on the bytes that go to the wallet: one memo, the app's three-letter code,
     * and no protocol payload — because the treasury scan only ever sees a transaction's FIRST memo.
     */
    @Test fun `a payment carries the fleet memo once and no protocol record`() {
        listOf(
            "SOL" to TxBuilder.proSolTransaction(signer, AppConfig.Payment.lamports(AppConfig.Payment.PRO_SOL), TxBuilder.ZERO_BLOCKHASH),
            "SKR" to TxBuilder.proSkrTransaction(signer, AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR), TxBuilder.ZERO_BLOCKHASH, createTreasuryAta = false),
            "SKR+ATA" to TxBuilder.proSkrTransaction(signer, AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR), TxBuilder.ZERO_BLOCKHASH, createTreasuryAta = true),
        ).forEach { (rail, tx) ->
            val text = String(tx, Charsets.ISO_8859_1)
            assertEquals("$rail: the memo must appear exactly once", 1, Regex(AppConfig.Payment.PAYMENT_MEMO).findAll(text).count())
            assertTrue("$rail: a payment must carry no SRL1 record", !text.contains("SRL1"))
        }
    }

    @Test fun `carriers fit the wire and dump for the mainnet gate`() {
        val sol = TxBuilder.proSolTransaction(signer, AppConfig.Payment.lamports(AppConfig.Payment.PRO_SOL), TxBuilder.ZERO_BLOCKHASH)
        val skr = TxBuilder.proSkrTransaction(signer, AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR), TxBuilder.ZERO_BLOCKHASH, createTreasuryAta = false)
        val skrCreate = TxBuilder.proSkrTransaction(signer, AppConfig.Payment.skrRaw(AppConfig.Payment.PRO_SKR), TxBuilder.ZERO_BLOCKHASH, createTreasuryAta = true)
        listOf(sol, skr, skrCreate).forEach { assertTrue("too large: ${it.size}", it.size <= AppConfig.Solana.MAX_TX_BYTES - 120) }
        val dump = File("build/pro-tx-dump.txt")
        dump.parentFile.mkdirs()
        dump.writeText(listOf("sol" to sol, "skr" to skr, "skr-create" to skrCreate).joinToString("\n") { (k, v) -> "$k ${Base64.getEncoder().encodeToString(v)}" })
    }
}
