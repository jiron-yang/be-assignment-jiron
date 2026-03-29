package com.jiron.notification.domain

import java.time.Duration
import java.time.LocalDateTime

/**
 * 재시도 정책 (지수 백오프)
 */
object RetryPolicy {
    private val RETRY_INTERVALS = listOf(
        Duration.ofMinutes(1),   // 1차 재시도
        Duration.ofMinutes(5),   // 2차 재시도
        Duration.ofMinutes(30)   // 3차 재시도
    )
    const val MAX_RETRY_COUNT = 3

    /** 다음 재시도 시각 계산. 재시도 불가시 null 반환 */
    fun calculateNextRetryAt(currentRetryCount: Int, now: LocalDateTime): LocalDateTime? {
        if (currentRetryCount >= MAX_RETRY_COUNT) return null
        val interval = RETRY_INTERVALS.getOrElse(currentRetryCount) { RETRY_INTERVALS.last() }
        return now.plus(interval)
    }
}
