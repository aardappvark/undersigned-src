package com.undersigned.app.solana

import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import com.undersigned.protocol.ReviewMemo
import com.undersigned.protocol.UseBucket
import com.undersigned.protocol.UseTime
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tip and the tipper's longest possible review (note, install age, usage time) must fit ONE transaction on both rails,
 * the developer's SKR account creation included; the tip record must be the FIRST memo (the RPC's one-memo summary is how
 * readers find the transaction); and the transfer goes to the developer, never the treasury.
 */
class TipTxTest {
    private val signer = Base58.decode("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
    private val dev = "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"
    private val pkg = "com.example.someverylongpackagename.withsegments.app"
    private val memos = listOf(
        ReviewMemo.Tip(pkg, dev),
        ReviewMemo.Review(pkg, 5, listOf(ReviewMemo.Tag.WORKS, ReviewMemo.Tag.FAST, ReviewMemo.Tag.FAIR_PRICE), Long.MAX_VALUE / 2, "é".repeat(ReviewMemo.MAX_TEXT_BYTES / 2).take(ReviewMemo.MAX_TEXT_CHARS)),
        ReviewMemo.Use(pkg, UseBucket.entries.last()), ReviewMemo.Time(pkg, UseTime.entries.last()),
    ).map { ReviewMemo.encode(it) }

    private fun bytes(b: ByteArray) = String(b, Charsets.ISO_8859_1)

    @Test fun `a tip with the longest review fits one transaction on both rails`() {
        val sol = TxBuilder.tipSolTransaction(signer, Base58.decode(dev), 10_000_000L, TxBuilder.ZERO_BLOCKHASH, memos)
        val skr = TxBuilder.tipSkrTransaction(signer, Base58.decode(dev), 10_000_000L, TxBuilder.ZERO_BLOCKHASH, createAta = true, memos = memos)
        println("tip tx sizes: SOL ${sol.size}, SKR+ATA ${skr.size} (limit ${AppConfig.Solana.MAX_TX_BYTES})")
        assertTrue("SOL ${sol.size}", sol.size <= AppConfig.Solana.MAX_TX_BYTES)
        assertTrue("SKR ${skr.size}", skr.size <= AppConfig.Solana.MAX_TX_BYTES)
    }

    @Test fun `trimming keeps Seeker Wallet headroom and never drops the tip or the review`() {
        for (skr in listOf(false, true)) {
            val m = TxBuilder.fitTipMemos(signer, Base58.decode(dev), skr, memos)
            assertTrue(m.size >= 2 && m[0].startsWith("SRL1|tip|") && m[1].startsWith("SRL1|rev|"))
            val size = if (skr) TxBuilder.tipSkrTransaction(signer, Base58.decode(dev), 1L, TxBuilder.ZERO_BLOCKHASH, true, m).size else TxBuilder.tipSolTransaction(signer, Base58.decode(dev), 1L, TxBuilder.ZERO_BLOCKHASH, m).size
            println("trimmed tip tx: skr=$skr memos=${m.size} size=$size")
            assertTrue(size <= AppConfig.Solana.MAX_TX_BYTES - 120)
        }
    }

    @Test fun `the tip record is the first memo and the money goes to the developer, not the treasury`() {
        val tx = bytes(TxBuilder.tipSolTransaction(signer, Base58.decode(dev), 10_000_000L, TxBuilder.ZERO_BLOCKHASH, memos))
        assertTrue(tx.indexOf("SRL1|tip|") in 1 until tx.indexOf("SRL1|rev|"))
        assertTrue(tx.contains(bytes(Base58.decode(dev))))
        assertTrue(!tx.contains(bytes(TxBuilder.TREASURY)))
        assertTrue(!tx.contains(AppConfig.Payment.PAYMENT_MEMO + "\u0000") && !tx.contains("|" + AppConfig.Payment.PAYMENT_MEMO))
    }
}
