package com.jiron.notification.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RetryPolicyTest {

    private val now = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    @Test
    @DisplayName("1차 재시도: 1분 후")
    fun firstRetry_1minuteLater() {
        val result = RetryPolicy.calculateNextRetryAt(0, now)

        assertThat(result).isEqualTo(now.plusMinutes(1))
    }

    @Test
    @DisplayName("2차 재시도: 5분 후")
    fun secondRetry_5minutesLater() {
        val result = RetryPolicy.calculateNextRetryAt(1, now)

        assertThat(result).isEqualTo(now.plusMinutes(5))
    }

    @Test
    @DisplayName("3차 재시도: 30분 후")
    fun thirdRetry_30minutesLater() {
        val result = RetryPolicy.calculateNextRetryAt(2, now)

        assertThat(result).isEqualTo(now.plusMinutes(30))
    }

    @Test
    @DisplayName("maxRetryCount 초과 시 null 반환")
    fun exceedsMaxRetryCount_returnsNull() {
        val result = RetryPolicy.calculateNextRetryAt(3, now)

        assertThat(result).isNull()
    }
}
