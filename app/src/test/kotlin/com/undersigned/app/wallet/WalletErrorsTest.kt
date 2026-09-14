package com.undersigned.app.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class WalletErrorsTest {
    @Test fun `the exact failure seen on device becomes a sentence`() {
        val m = WalletErrors.humanise(TimeoutException("Timed out waiting for response with id=1"))
        assertFalse(m.contains("java"))
        assertFalse(m.contains("Exception"))
        assertEquals(true, m.startsWith("The wallet didn't get your approval in time"))
    }
    @Test fun `a wrapped timeout is still recognised`() {
        val m = WalletErrors.humanise(ExecutionException(TimeoutException("Timed out waiting for response with id=3")))
        assertEquals(true, m.startsWith("The wallet didn't get your approval in time"))
    }
    @Test fun `our own plain messages are kept`() {
        val own = "The wallet returned a different account (HVXV…yDaD) from the one signed in. Switch accounts in your wallet and try again."
        assertEquals(own, WalletErrors.humanise(Exception(own)))
    }
    @Test fun `an unknown java exception falls back`() {
        assertEquals("fallback", WalletErrors.humanise(IllegalStateException("java.lang.IllegalStateException: boom"), "fallback"))
    }
}
