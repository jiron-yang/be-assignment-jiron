package com.jiron.notification.application.service

import com.jiron.notification.application.port.out.NotificationChannel
import com.jiron.notification.application.port.out.NotificationRepository
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import com.jiron.notification.domain.vo.RetryPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class NotificationSenderTest {

    // Mockito any() wrapper (Kotlin null-safety 대응)
    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private val notificationRepository: NotificationRepository = Mockito.mock(NotificationRepository::class.java).also {
        Mockito.`when`(it.save(anyObject())).thenAnswer { invocation -> invocation.arguments[0] }
    }

    // 테스트용 PROCESSING 상태 알림 생성
    private fun createProcessingNotification(retryCount: Int = 0): Notification {
        val notification = Notification(
            recipientId = "user-1",
            notificationType = NotificationType.EMAIL,
            channel = "EMAIL",
            title = "테스트",
            content = "내용",
            referenceEventId = "event-1"
        )
        notification.startProcessing()
        notification.retryCount = retryCount
        return notification
    }

    // 발송 성공 채널
    private val successChannel = object : NotificationChannel {
        override fun send(notification: Notification) { /* 성공 */ }
        override fun supports(type: NotificationType) = true
    }

    // 발송 실패 채널
    private val failChannel = object : NotificationChannel {
        override fun send(notification: Notification) {
            throw RuntimeException("Send failed")
        }
        override fun supports(type: NotificationType) = true
    }

    @Test
    @DisplayName("발송 성공 → SENT 상태")
    fun sendAll_success_marksSent() {
        val sender = NotificationSender(listOf(successChannel), notificationRepository)
        val notification = createProcessingNotification()

        sender.sendAll(listOf(notification))

        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.sentAt).isNotNull()
    }

    @Test
    @DisplayName("발송 실패 → 재시도 스케줄링 (retryCount < max)")
    fun sendAll_failure_schedulesRetry() {
        val sender = NotificationSender(listOf(failChannel), notificationRepository)
        val notification = createProcessingNotification(retryCount = 0)

        sender.sendAll(listOf(notification))

        assertThat(notification.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(notification.retryCount).isEqualTo(1)
    }

    @Test
    @DisplayName("발송 실패 → FAILED (retryCount >= max)")
    fun sendAll_failure_marksFailed_whenMaxRetries() {
        val sender = NotificationSender(listOf(failChannel), notificationRepository)
        val notification = createProcessingNotification(retryCount = RetryPolicy.MAX_RETRY_COUNT)

        sender.sendAll(listOf(notification))

        assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
    }
}
