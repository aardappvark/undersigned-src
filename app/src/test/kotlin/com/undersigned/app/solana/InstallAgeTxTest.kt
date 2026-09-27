package com.undersigned.app.solana

import com.undersigned.app.util.Base58
import com.undersigned.protocol.ReviewMemo
import com.undersigned.protocol.UseBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallAgeTxTest {
    /** The largest review the codec allows, plus its install-age record, must share ONE transaction or the age attaches to nothing. */
    @Test fun worstCaseReviewAndInstallAgeFitOneTransaction() {
        val signer = Base58.decode("8oLtrvMnk7bLzjYzWQdo66kJsvkG4CYL1QKoeKbBHT8F")
        val pkg = "a." + "b".repeat(98)                                   // 100 chars
        val tags = listOf(ReviewMemo.Tag.SUSPECTED_SCAM, ReviewMemo.Tag.EXCESS_PERMISSIONS, ReviewMemo.Tag.WALLET_FAILS)
        val text = "ж".repeat(ReviewMemo.MAX_TEXT_BYTES / 2)              // 280 UTF-8 bytes, 140 chars
        val review = ReviewMemo.encode(ReviewMemo.Review(pkg, 5, tags, Long.MAX_VALUE, text))
        val use = ReviewMemo.encode(ReviewMemo.Use(pkg, UseBucket.MONTHS))
        assertEquals(1, TxBuilder.chunkForBatches(signer, listOf(review, use)).size)
        val size = TxBuilder.batchTransaction(signer, listOf(review, use), TxBuilder.ZERO_BLOCKHASH).size
        assertTrue("$size bytes", size <= com.undersigned.app.AppConfig.Solana.MAX_TX_BYTES - 120)
        // For a mainnet simulateTransaction by hand (sigVerify off): TX_OUT=<file> writes the unsigned bytes.
        System.getenv("TX_OUT")?.let { java.io.File(it).writeText(java.util.Base64.getEncoder().encodeToString(TxBuilder.batchTransaction(signer, listOf(review, use), TxBuilder.ZERO_BLOCKHASH))) }
    }

    /** A typical review carries both extras in one transaction; the largest one keeps at least its install age. */
    @Test fun extrasFitOrFallBackInOrder() {
        val signer = Base58.decode("8oLtrvMnk7bLzjYzWQdo66kJsvkG4CYL1QKoeKbBHT8F")
        val typical = ReviewMemo.encode(ReviewMemo.Review("com.solflare.mobile", 5, listOf(ReviewMemo.Tag.WALLET_CONNECTS, ReviewMemo.Tag.WORKS), 20250, "very quick to make trades at reasonable slippage"))
        val extras = { pkg: String -> listOf(ReviewMemo.encode(ReviewMemo.Use(pkg, UseBucket.WEEKS)), ReviewMemo.encode(ReviewMemo.Time(pkg, com.undersigned.protocol.UseTime.HOURS))) }
        assertEquals(1, TxBuilder.chunkForBatches(signer, listOf(typical) + extras("com.solflare.mobile")).size)
        val pkg = "a." + "b".repeat(98)
        val worst = ReviewMemo.encode(ReviewMemo.Review(pkg, 5, listOf(ReviewMemo.Tag.SUSPECTED_SCAM, ReviewMemo.Tag.EXCESS_PERMISSIONS, ReviewMemo.Tag.WALLET_FAILS), Long.MAX_VALUE, "ж".repeat(140)))
        var texts = listOf(worst) + extras(pkg)
        while (texts.size > 1 && TxBuilder.chunkForBatches(signer, texts).size != 1) texts = texts.dropLast(1)
        assertTrue("kept ${texts.size}", texts.size >= 2)
    }
}
