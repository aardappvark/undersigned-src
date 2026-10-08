package com.undersigned.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/** Staked SOL is balance minus rent reserve per account, never the stale delegation figure; dead-rent accounts read 0. */
class StakeViewTest {
    private fun head(tag: Int, reserve: Long) = java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(tag).putLong(reserve).array()

    @Test fun `balance minus reserve, summed, dead rent reads zero`() {
        val live = 5_000_000_000L + 2_282_880L to head(2, 2_282_880L)          // 5 SOL staked over the old reserve
        val dead = 2_282_880L to head(2, 2_282_880L)                            // only its rent left
        val init = 1_000_000_000L + 2_282_880L to head(1, 2_282_880L)           // initialised, not delegated: still the wallet's stake account
        assertEquals(6.0, StakeView.solStaked(listOf(live, dead, init)), 1e-9)
        assertEquals(0.0, StakeView.solStaked(listOf(dead)), 1e-12)
        assertEquals(0.0, StakeView.solStaked(emptyList()), 1e-12)
    }

    @Test fun `an unreadable header falls back to today's rent floor, never below zero`() {
        assertEquals((3_000_000_000L - StakeView.RENT_FLOOR_LAMPORTS) / 1e9, StakeView.solStaked(listOf(3_000_000_000L to ByteArray(0))), 1e-12)
        assertEquals(0.0, StakeView.solStaked(listOf(1_000L to ByteArray(0))), 1e-12)
    }
}
