package com.smarttasker.core.retry

/**
 * 重试策略配置
 * 支持指数退避、最大重试次数、可重试异常类型
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Double = 2.0,
    val retryableExceptions: Set<Class<out Throwable>> = setOf(
        Exception::class.java
    )
) {
    companion object {
        /**
         * 默认策略：3次重试，指数退避 1s → 2s → 4s
         */
        val DEFAULT = RetryPolicy(
            maxRetries = 3,
            initialDelayMs = 1000,
            maxDelayMs = 10000,
            backoffMultiplier = 2.0
        )

        /**
         * 快速重试：2次重试，固定间隔 500ms
         */
        val FAST = RetryPolicy(
            maxRetries = 2,
            initialDelayMs = 500,
            maxDelayMs = 500,
            backoffMultiplier = 1.0
        )

        /**
         * 无重试
         */
        val NONE = RetryPolicy(maxRetries = 0)
    }

    /**
     * 计算第 n 次重试的延迟时间
     */
    fun getDelayForRetry(retryCount: Int): Long {
        if (retryCount <= 0) return 0
        val delay = initialDelayMs * Math.pow(backoffMultiplier, (retryCount - 1).toDouble())
        return delay.toLong().coerceAtMost(maxDelayMs)
    }

    /**
     * 判断异常是否可重试
     */
    fun isRetryable(exception: Throwable): Boolean {
        return retryableExceptions.any { it.isInstance(exception) }
    }
}
