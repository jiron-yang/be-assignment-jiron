package com.jiron.notification.domain.model

import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import com.jiron.notification.domain.vo.RecipientId
import com.jiron.notification.domain.vo.ReferenceEventId
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
            recipientId = RecipientId("user-1"),
            notificationType = NotificationType.EMAIL,
            title = "테스트 제목",
            content = "테스트 내용",
            referenceEventId = ReferenceEventId("event-1")
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
    @DisplayName("발송 실패 시 재시도 가능하면 PENDING으로 전이, retryCount 증가")
    fun handleSendFailure_retryAvailable() {
        val notification = createNotification(NotificationStatus.PROCESSING)
        val now = LocalDateTime.now()

        notification.handleSendFailure(now)

        assertThat(notification.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(notification.retryCount).isEqualTo(1)
        assertThat(notification.nextRetryAt).isAfter(now)
    }

    @Test
    @DisplayName("발송 실패 시 재시도 불가하면 FAILED로 전이")
    fun handleSendFailure_maxRetriesExceeded() {
        val notification = Notification(
            recipientId = RecipientId("user-1"),
            notificationType = NotificationType.EMAIL,
            title = "테스트 제목",
            content = "테스트 내용",
            referenceEventId = ReferenceEventId("event-1"),
            retryCount = 3
        )
        notification.startProcessing()

        notification.handleSendFailure(LocalDateTime.now())

        assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
    }

    @Test
    @DisplayName("canRetry는 retryCount < maxRetryCount일 때 true")
    fun canRetry_returnsTrue() {
        val notification = createNotification()

        assertThat(notification.canRetry()).isTrue()
    }

    @Test
    @DisplayName("canRetry는 retryCount >= maxRetryCount일 때 false")
    fun canRetry_returnsFalse() {
        val notification = Notification(
            recipientId = RecipientId("user-1"),
            notificationType = NotificationType.EMAIL,
            title = "테스트 제목",
            content = "테스트 내용",
            referenceEventId = ReferenceEventId("event-1"),
            retryCount = 3
        )

        assertThat(notification.canRetry()).isFalse()
    }

    // === 생성 시 비즈니스 정책 검증 ===

    @Test
    @DisplayName("빈 제목으로 생성 시 예외 발생")
    fun create_blankTitle_throwsException() {
        assertThatThrownBy {
            Notification(
                recipientId = RecipientId("user-1"),
                notificationType = NotificationType.EMAIL,
                title = "  ",
                content = "내용",
                referenceEventId = ReferenceEventId("event-1")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Title")
    }

    @Test
    @DisplayName("빈 내용으로 생성 시 예외 발생")
    fun create_blankContent_throwsException() {
        assertThatThrownBy {
            Notification(
                recipientId = RecipientId("user-1"),
                notificationType = NotificationType.EMAIL,
                title = "제목",
                content = "",
                referenceEventId = ReferenceEventId("event-1")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Content")
    }

    @Test
    @DisplayName("음수 retryCount로 생성 시 예외 발생")
    fun create_negativeRetryCount_throwsException() {
        assertThatThrownBy {
            Notification(
                recipientId = RecipientId("user-1"),
                notificationType = NotificationType.EMAIL,
                title = "제목",
                content = "내용",
                referenceEventId = ReferenceEventId("event-1"),
                retryCount = -1
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("negative")
    }

    @Test
    @DisplayName("SENT가 아닌 상태에서 sentAt이 있으면 예외 발생")
    fun create_sentAtWithNonSentStatus_throwsException() {
        assertThatThrownBy {
            Notification(
                recipientId = RecipientId("user-1"),
                notificationType = NotificationType.EMAIL,
                title = "제목",
                content = "내용",
                referenceEventId = ReferenceEventId("event-1"),
                sentAt = LocalDateTime.now()
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("sentAt")
    }

    // === 잘못된 상태 전이 ===

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
