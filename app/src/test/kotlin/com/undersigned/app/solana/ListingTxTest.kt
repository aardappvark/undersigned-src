package com.undersigned.app.solana

import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import com.undersigned.protocol.ReviewMemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * A paid listing: the fee transfer, UNS FIRST (the treasury scan sees only a transaction's first memo), then the
 * `trs` record. Dumped so the SAME bytes can be simulated on mainnet before any owner sees a sheet.
 */
class ListingTxTest {
    private val signer = Base58.decode("FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF")
    private val record = ReviewMemo.encode(ReviewMemo.Treasury("com.solsow.app", "SOW"))

    @Test fun `fleet price, and the reader floor accepts every price the app charges`() {
        assertEquals(60_700_000L, AppConfig.Payment.lamports(AppConfig.Payment.LIST_SOL))
        assertEquals(651_000_000L, AppConfig.Payment.skrRaw(AppConfig.Payment.LIST_SKR))
        assertTrue(AppConfig.Payment.isPaidListing(AppConfig.Payment.lamports(AppConfig.Payment.LIST_SOL), 0))
        assertTrue(AppConfig.Payment.isPaidListing(0, AppConfig.Payment.skrRaw(AppConfig.Payment.listSkr(true))))
        assertTrue(AppConfig.Payment.isPaidListing(0, AppConfig.Payment.skrRaw(AppConfig.Payment.listSkr(false))))
        // One unit short on either rail lists nothing; the network fee alone lists nothing.
        assertFalse(AppConfig.Payment.isPaidListing(AppConfig.Payment.lamports(AppConfig.Payment.LIST_SOL) - 1, 0))
        assertFalse(AppConfig.Payment.isPaidListing(0, AppConfig.Payment.skrRaw(AppConfig.Payment.LIST_SKR_SGT) - 1))
        assertFalse(AppConfig.Payment.isPaidListing(0, 0))
    }

    @Test fun `UNS comes first, the record second, and the listing fits and dumps`() {
        val sol = TxBuilder.proSolTransaction(signer, AppConfig.Payment.lamports(AppConfig.Payment.LIST_SOL), TxBuilder.ZERO_BLOCKHASH, record)
        val skr = TxBuilder.proSkrTransaction(signer, AppConfig.Payment.skrRaw(AppConfig.Payment.LIST_SKR), TxBuilder.ZERO_BLOCKHASH, createTreasuryAta = false, record = record)
        listOf(sol, skr).forEach { tx ->
            assertTrue("too large: ${tx.size}", tx.size <= AppConfig.Solana.MAX_TX_BYTES - 120)
            val text = String(tx, Charsets.ISO_8859_1)
            val uns = text.indexOf(AppConfig.Payment.PAYMENT_MEMO); val rec = text.indexOf(record)
            assertTrue("both memos present", uns >= 0 && rec >= 0)
            assertTrue("UNS must precede the record", uns < rec)
        }
        File("build").mkdirs()
        File("build/list-tx-dump.txt").writeText("sol ${Base64.getEncoder().encodeToString(sol)}\nskr ${Base64.getEncoder().encodeToString(skr)}")
    }
}
