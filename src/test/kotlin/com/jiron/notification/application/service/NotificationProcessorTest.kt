package com.jiron.notification.application.service

import com.jiron.notification.application.port.out.NotificationChannel
import com.jiron.notification.application.port.out.NotificationQueue
import com.jiron.notification.application.port.out.NotificationRepository
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import com.jiron.notification.domain.vo.RecipientId
import com.jiron.notification.domain.vo.ReferenceEventId
import com.jiron.notification.domain.vo.RetryPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.Consumer

class NotificationProcessorTest {

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private val notificationRepository: NotificationRepository = Mockito.mock(NotificationRepository::class.java).also {
        Mockito.`when`(it.save(anyObject())).thenAnswer { invocation -> invocation.arguments[0] }
    }

    private val transactionTemplate: TransactionTemplate = Mockito.mock(TransactionTemplate::class.java).also {
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as Consumer<TransactionStatus>
            val mockStatus = Mockito.mock(TransactionStatus::class.java)
            callback.accept(mockStatus)
            null
        }.`when`(it).executeWithoutResult(anyObject())
    }

    private fun createProcessingNotification(retryCount: Int = 0): Notification {
        val notification = Notification(
            recipientId = RecipientId("user-1"),
            notificationType = NotificationType.EMAIL,
            title = "테스트",
            content = "내용",
            referenceEventId = ReferenceEventId("event-1"),
            retryCount = retryCount
        )
        notification.startProcessing()
        return notification
    }

    private val successChannel = object : NotificationChannel {
        override fun send(notification: Notification) { /* 성공 */ }
        override fun supports(type: NotificationType) = true
    }

    private val failChannel = object : NotificationChannel {
        override fun send(notification: Notification) {
            throw RuntimeException("Send failed")
        }
        override fun supports(type: NotificationType) = true
    }

    private fun createProcessor(channel: NotificationChannel, notifications: List<Notification>): NotificationProcessor {
        val queue = Mockito.mock(NotificationQueue::class.java)
        Mockito.`when`(queue.dequeueForProcessing(Mockito.anyInt())).thenReturn(notifications)
        return NotificationProcessor(queue, listOf(channel), notificationRepository, transactionTemplate)
    }

    @Test
    @DisplayName("발송 성공 → SENT 상태")
    fun execute_success_marksSent() {
        val notification = createProcessingNotification()
        val processor = createProcessor(successChannel, listOf(notification))

        processor.execute()

        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.sentAt).isNotNull()
    }

    @Test
    @DisplayName("발송 실패 → 재시도 스케줄링 (retryCount < max)")
    fun execute_failure_schedulesRetry() {
        val notification = createProcessingNotification(retryCount = 0)
        val processor = createProcessor(failChannel, listOf(notification))

        processor.execute()

        assertThat(notification.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(notification.retryCount).isEqualTo(1)
    }

    @Test
    @DisplayName("발송 실패 → FAILED (retryCount >= max)")
    fun execute_failure_marksFailed_whenMaxRetries() {
        val notification = createProcessingNotification(retryCount = RetryPolicy.MAX_RETRY_COUNT)
        val processor = createProcessor(failChannel, listOf(notification))

        processor.execute()

        assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
    }
}
