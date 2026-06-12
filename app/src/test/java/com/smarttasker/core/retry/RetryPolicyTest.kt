package com.smarttasker.core.retry

import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `default policy has 3 retries and 1s initial delay`() {
        val p = RetryPolicy.DEFAULT
        assertEquals(3, p.maxRetries)
        assertEquals(1000L, p.initialDelayMs)
        assertEquals(10000L, p.maxDelayMs)
        assertEquals(2.0, p.backoffMultiplier, 0.0)
    }

    @Test
    fun `exponential backoff 1s 2s 4s on default policy`() {
        val p = RetryPolicy.DEFAULT
        assertEquals(1000L, p.getDelayForRetry(1))
        assertEquals(2000L, p.getDelayForRetry(2))
        assertEquals(4000L, p.getDelayForRetry(3))
        assertEquals(8000L, p.getDelayForRetry(4))
    }

    @Test
    fun `delay is capped at maxDelayMs`() {
        val p = RetryPolicy(maxRetries = 20, initialDelayMs = 1000, maxDelayMs = 5000, backoffMultiplier = 2.0)
        // 1000 * 2^4 = 16000 > 5000
        assertEquals(5000L, p.getDelayForRetry(5))
        assertEquals(5000L, p.getDelayForRetry(20))
    }

    @Test
    fun `getDelayForRetry 0 returns 0`() {
        val p = RetryPolicy.DEFAULT
        assertEquals(0L, p.getDelayForRetry(0))
    }

    @Test
    fun `FAST policy has constant 500ms delay`() {
        val p = RetryPolicy.FAST
        assertEquals(500L, p.getDelayForRetry(1))
        assertEquals(500L, p.getDelayForRetry(2))
        assertEquals(500L, p.getDelayForRetry(3))
    }

    @Test
    fun `NONE policy has zero retries`() {
        val p = RetryPolicy.NONE
        assertEquals(0, p.maxRetries)
    }

    @Test
    fun `Exception is retryable by default`() {
        val p = RetryPolicy.DEFAULT
        assertTrue(p.isRetryable(RuntimeException("boom")))
        assertTrue(p.isRetryable(IllegalStateException("state")))
    }

    @Test
    fun `custom retryable set narrows exceptions`() {
        val p = RetryPolicy(retryableExceptions = setOf(IllegalStateException::class.java))
        assertTrue(p.isRetryable(IllegalStateException("x")))
        assertFalse(p.isRetryable(RuntimeException("y")))
    }
}
