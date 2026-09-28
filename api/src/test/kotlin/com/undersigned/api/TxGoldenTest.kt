package com.undersigned.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The review carrier's exact bytes for fixed inputs. The app's TxBuilder has the same golden in its own test
 * (ReviewTxGoldenTest), so the two builders cannot drift apart without one of them failing.
 */
class TxGoldenTest {
    @Test fun `review transaction bytes match the app's builder`() {
        val signer = Base58.decode("FFWDeEAh2kSLDUwd6fZzXtCadewYqehatSQKChr11jVF")
        val tx = Tx.reviewTransaction(signer, "SRL1|rev|com.solflare.mobile|5|works,wallet-ok|20250|", ByteArray(32) { 7 })
        val hex = tx.joinToString("") { "%02x".format(it) }
        assertEquals(GOLDEN, hex)
    }
    companion object { const val GOLDEN = "010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000304d3bac8ffb6feaf30c0aef8171bb8e323628dd9fe4eb0422fc444b3921cc3536e054a535a992921064d24e87160da387c7c35b5ddbc92bb81e41fa8404105448da1cc4e86c6694bea28a90131b9bb1a009a8767069e9116def9f1d4a80e2432fe000000000000000000000000000000000000000000000000000000000000000007070707070707070707070707070707070707070707070707070707070707070201003553524c317c7265767c636f6d2e736f6c666c6172652e6d6f62696c657c357c776f726b732c77616c6c65742d6f6b7c32303235307c03030000020c020000000000000000000000" }
}
