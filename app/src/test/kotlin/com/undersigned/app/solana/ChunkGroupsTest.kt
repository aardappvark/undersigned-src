package com.undersigned.app.solana

import com.undersigned.app.util.Base58
import com.undersigned.protocol.ReviewMemo
import com.undersigned.protocol.UseBucket
import com.undersigned.protocol.UseTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Several reviews in one approval: each review shares its transaction with its own install-age and usage records. */
class ChunkGroupsTest {
    private val signer = Base58.decode("FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF")
    private fun group(pkg: String, text: String? = null) = listOf<ReviewMemo>(
        ReviewMemo.Review(pkg, 5, listOf(ReviewMemo.Tag.WORKS, ReviewMemo.Tag.FAST), 1234, text), ReviewMemo.Use(pkg, UseBucket.MONTHS), ReviewMemo.Time(pkg, UseTime.HOURS),
    ).map { ReviewMemo.encode(it) }

    @Test fun `a group never splits across transactions`() {
        val groups = (1..8).map { group("com.example.app$it") }
        val chunks = TxBuilder.chunkGroups(signer, groups)
        assertTrue("several transactions are expected for eight reviews", chunks.size > 1)
        assertEquals(groups.flatten(), chunks.flatten())                                   // nothing lost, order kept
        for (c in chunks) {
            // Every use/tim record sits in the same transaction as the review of the same package.
            val reviews = c.filter { it.startsWith("SRL1|rev|") }.map { it.split('|')[2] }.toSet()
            c.filter { it.startsWith("SRL1|use|") || it.startsWith("SRL1|tim|") }.forEach { assertTrue(it, it.split('|')[2] in reviews) }
            assertTrue(TxBuilder.batchTransaction(signer, c, TxBuilder.ZERO_BLOCKHASH).size <= 1232 - 120)
        }
        // The first transaction's bytes, for a mainnet simulation outside the test.
        java.io.File("build").mkdirs(); java.io.File("build/batch-tx-dump.txt").writeText(java.util.Base64.getEncoder().encodeToString(TxBuilder.batchTransaction(signer, chunks[0], TxBuilder.ZERO_BLOCKHASH)))
    }

    @Test fun `a group too big alone keeps its review and drops extras from the end`() {
        val long = "x".repeat(ReviewMemo.MAX_TEXT_CHARS)
        val big = group("com.example.a.very.long.package.name.that.takes.room.in.every.record", long)
        val chunks = TxBuilder.chunkGroups(signer, listOf(big))
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].first().startsWith("SRL1|rev|"))
        assertTrue(chunks[0].size <= big.size)
    }
}
