package com.undersigned.api

import com.undersigned.protocol.ReviewMemo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fleet batch: every memo lands, in order, each transaction leaves room for a wallet's fee instructions. */
class SeedBatchTest {
    @Test fun `56 fleet records split into transactions under the batch cap, none lost`() {
        val memos = (1..28).flatMap { listOf(ReviewMemo.encode(ReviewMemo.Claim("com.fleet.n${it}z")), ReviewMemo.encode(ReviewMemo.Treasury("com.fleet.n${it}z", "AZFbwj8hARyRpAVU2umxAQ2fqscS3CfwPYpYzYigTgQr"))) }
        val txs = Tx.memoBatches(ByteArray(32) { 1 }, memos, ByteArray(32) { 2 })
        assertTrue(txs.all { it.size <= Tx.BATCH_BYTES }, txs.map { it.size }.toString())
        val found = txs.sumOf { tx -> memos.count { m -> String(tx, Charsets.ISO_8859_1).contains(m) } }
        assertEquals(memos.size, found)
    }
}

