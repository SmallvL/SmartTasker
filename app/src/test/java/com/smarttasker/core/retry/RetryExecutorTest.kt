package com.smarttasker.core.retry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryExecutorTest {

    @Test
    fun `execute returns value on first success`() = runTest {
        val executor = RetryExecutor(RetryPolicy.NONE)
        val result = executor.execute { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `execute retries on failure and succeeds`() = runTest {
        var attempt = 0
        val executor = RetryExecutor(RetryPolicy(maxRetries = 2, initialDelayMs = 0, maxDelayMs = 0, backoffMultiplier = 1.0))
        val result = executor.execute {
            attempt++
            if (attempt < 3) throw RuntimeException("fail") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempt)
    }

    @Test(expected = RuntimeException::class)
    fun `execute throws after max retries`() = runTest {
        val executor = RetryExecutor(RetryPolicy(maxRetries = 1, initialDelayMs = 0, maxDelayMs = 0, backoffMultiplier = 1.0))
        executor.execute<String> { throw RuntimeException("always fail") }
    }

    @Test
    fun `execute does not retry non-retryable exception`() = runTest {
        var attempt = 0
        val policy = RetryPolicy(maxRetries = 3, initialDelayMs = 0, maxDelayMs = 0, backoffMultiplier = 1.0,
            retryableExceptions = setOf(IllegalStateException::class.java))
        val executor = RetryExecutor(policy)
        try {
            executor.execute {
                attempt++
                throw RuntimeException("not retryable")
            }
        } catch (_: RuntimeException) {
            // expected
        }
        assertEquals(1, attempt) // no retry
    }

    @Test
    fun `executeCatching returns success on first try`() = runTest {
        val executor = RetryExecutor(RetryPolicy.NONE)
        val result = executor.executeCatching { 99 }
        assertTrue(result.isSuccess)
        assertEquals(99, result.getOrNull())
    }

    @Test
    fun `executeCatching returns failure after retries exhausted`() = runTest {
        val executor = RetryExecutor(RetryPolicy(maxRetries = 1, initialDelayMs = 0, maxDelayMs = 0, backoffMultiplier = 1.0))
        val result = executor.executeCatching<String> { throw RuntimeException("fail") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `onRetry callback is invoked on each retry`() = runTest {
        val retryInfo = mutableListOf<Triple<Int, String, Long>>()
        val executor = RetryExecutor(RetryPolicy(maxRetries = 2, initialDelayMs = 0, maxDelayMs = 0, backoffMultiplier = 1.0))
        var attempt = 0
        executor.execute(
            operation = {
                attempt++
                if (attempt < 3) throw RuntimeException("retry me") else "done"
            },
            onRetry = { count, e, delayMs ->
                retryInfo.add(Triple(count, e.message ?: "", delayMs))
            }
        )
        assertEquals(2, retryInfo.size)
        assertEquals(1, retryInfo[0].first)
        assertEquals("retry me", retryInfo[0].second)
    }

    @Test
    fun `RetryResult success factory`() {
        val r = RetryResult.success("hello", 2)
        assertEquals("hello", r.value)
        assertNull(r.exception)
        assertEquals(2, r.retryCount)
        assertTrue(r.success)
    }

    @Test
    fun `RetryResult failure factory`() {
        val err = RuntimeException("boom")
        val r = RetryResult.failure<String>(err, 3)
        assertNull(r.value)
        assertEquals(err, r.exception)
        assertEquals(3, r.retryCount)
        assertFalse(r.success)
    }
}
