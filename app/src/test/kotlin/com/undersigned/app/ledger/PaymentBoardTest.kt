package com.undersigned.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentBoardTest {
    /** The token accounts a real SKR and USDC payment to the fleet treasury credited (read from chain, 27 Sep 2026). */
    @Test fun derivesTheTreasuryTokenAccountsThatPaymentsActuallyHit() {
        assertEquals(
            listOf("DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q", "GcV5XyhuaScv5Rp77RjpsJiGUaSFjtGdA8c4nUrsDFLk", "CadZajGB5g6XCwhpbfKKh1LTmkN6PCA6Lxo2VKtVremK"),
            PaymentBoard.addressesOf("DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"),
        )
    }

    @Test fun usdAtTodaysPricesNeedsEveryPrice() {
        val q = mapOf(PaymentBoard.SOL_MINT to Prices.Quote(120.0, 9), "SKR" to Prices.Quote(0.02, 6))
        assertEquals(12.0 + 5.52, Prices.usd(100_000_000L, mapOf("SKR" to 276_000_000L), q)!!, 1e-9)
        assertEquals(null, Prices.usd(0L, mapOf("OTHER" to 1L), q))
    }
}
