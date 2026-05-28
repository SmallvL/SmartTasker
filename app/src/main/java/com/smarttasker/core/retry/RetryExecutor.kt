package com.smarttasker.core.retry

import android.util.Log
import kotlinx.coroutines.delay

/**
 * 重试执行器
 * 执行带有重试逻辑的操作
 */
class RetryExecutor(private val policy: RetryPolicy = RetryPolicy.DEFAULT) {

    companion object {
        private const val TAG = "RetryExecutor"
    }

    /**
     * 执行操作，失败时自动重试
     * @param operation 要执行的操作
     * @param onRetry 重试时的回调（可选）
     * @return 操作结果
     * @throws Exception 最后一次重试仍然失败时抛出异常
     */
    suspend fun <T> execute(
        operation: suspend () -> T,
        onRetry: ((retryCount: Int, exception: Throwable, delayMs: Long) -> Unit)? = null
    ): T {
        var lastException: Throwable? = null

        for (retryCount in 0..policy.maxRetries) {
            try {
                return operation()
            } catch (e: Throwable) {
                lastException = e

                // 检查是否可重试
                if (!policy.isRetryable(e)) {
                    Log.w(TAG, "Exception not retryable: ${e.javaClass.simpleName}")
                    throw e
                }

                // 检查是否还有重试次数
                if (retryCount >= policy.maxRetries) {
                    Log.e(TAG, "Max retries (${policy.maxRetries}) reached")
                    throw e
                }

                // 计算延迟时间
                val delayMs = policy.getDelayForRetry(retryCount + 1)
                Log.w(TAG, "Retry ${retryCount + 1}/${policy.maxRetries} after ${delayMs}ms: ${e.message}")

                // 回调
                onRetry?.invoke(retryCount + 1, e, delayMs)

                // 等待
                delay(delayMs)
            }
        }

        // 不应该到这里，但为了编译器
        throw lastException ?: IllegalStateException("Unknown error in retry executor")
    }

    /**
     * 执行操作，返回 Result 包装
     */
    suspend fun <T> executeCatching(
        operation: suspend () -> T,
        onRetry: ((retryCount: Int, exception: Throwable, delayMs: Long) -> Unit)? = null
    ): Result<T> {
        return try {
            Result.success(execute(operation, onRetry))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

/**
 * 带重试的操作结果
 */
data class RetryResult<T>(
    val value: T?,
    val exception: Throwable?,
    val retryCount: Int,
    val success: Boolean
) {
    companion object {
        fun <T> success(value: T, retryCount: Int) = RetryResult(
            value = value,
            exception = null,
            retryCount = retryCount,
            success = true
        )

        fun <T> failure(exception: Throwable, retryCount: Int) = RetryResult(
            value = null,
            exception = exception,
            retryCount = retryCount,
            success = false
        )
    }
}
