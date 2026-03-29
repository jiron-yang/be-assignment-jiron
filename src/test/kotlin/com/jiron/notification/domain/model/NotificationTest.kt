package com.jiron.notification.domain.model

import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NotificationTest {

    // 테스트용 알림 생성 헬퍼
    private fun createNotification(
        status: NotificationStatus = NotificationStatus.PENDING
    ): Notification {
        val notification = Notification(
            recipientId = "user-1",
            notificationType = NotificationType.EMAIL,
            channel = "EMAIL",
            title = "테스트 제목",
            content = "테스트 내용",
            referenceEventId = "event-1"
        )
        // 상태를 원하는 값으로 설정
        if (status == NotificationStatus.PROCESSING) {
            notification.startProcessing()
        }
        return notification
    }

    @Test
    @DisplayName("PENDING → PROCESSING 상태 전이 성공")
    fun startProcessing_success() {
        val notification = createNotification()

        notification.startProcessing()

        assertThat(notification.status).isEqualTo(NotificationStatus.PROCESSING)
    }

    @Test
    @DisplayName("PROCESSING → SENT 상태 전이 성공, sentAt 설정됨")
    fun markSent_success() {
        val notification = createNotification(NotificationStatus.PROCESSING)

        notification.markSent()

        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.sentAt).isNotNull()
    }

    @Test
    @DisplayName("PROCESSING → FAILED 상태 전이 성공")
    fun markFailed_success() {
        val notification = createNotification(NotificationStatus.PROCESSING)

        notification.markFailed()

        assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
    }

    @Test
    @DisplayName("PROCESSING → PENDING 재시도 스케줄링 성공, retryCount 증가")
    fun scheduleRetry_success() {
        val notification = createNotification(NotificationStatus.PROCESSING)
        val nextRetryAt = LocalDateTime.now().plusMinutes(5)

        notification.scheduleRetry(nextRetryAt)

        assertThat(notification.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(notification.retryCount).isEqualTo(1)
        assertThat(notification.nextRetryAt).isEqualTo(nextRetryAt)
    }

    @Test
    @DisplayName("SENT 상태에서 startProcessing 호출 시 예외 발생")
    fun startProcessing_fromSent_throwsException() {
        val notification = createNotification(NotificationStatus.PROCESSING)
        notification.markSent()

        assertThatThrownBy { notification.startProcessing() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("SENT")
    }

    @Test
    @DisplayName("PENDING 상태에서 markSent 호출 시 예외 발생")
    fun markSent_fromPending_throwsException() {
        val notification = createNotification()

        assertThatThrownBy { notification.markSent() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("PENDING")
    }
}
