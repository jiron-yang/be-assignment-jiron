package com.jiron.notification.application.service

import com.jiron.notification.application.port.`in`.ProcessPendingNotificationsUseCase
import com.jiron.notification.application.port.out.NotificationChannel
import com.jiron.notification.application.port.out.NotificationQueue
import com.jiron.notification.application.port.out.NotificationRepository
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.RetryPolicy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 대기 중인 알림 발송 처리 서비스
 */
@Service
class NotificationProcessor(
    private val notificationQueue: NotificationQueue,
    private val channels: List<NotificationChannel>,
    private val notificationRepository: NotificationRepository
) : ProcessPendingNotificationsUseCase {

    private val logger = LoggerFactory.getLogger(NotificationProcessor::class.java)

    @Transactional
    override fun execute() {
        val notifications = notificationQueue.dequeueForProcessing(batchSize = 10)
        if (notifications.isEmpty()) return

        logger.info("Processing {} pending notifications", notifications.size)
        notifications.forEach { sendOne(it) }
        logger.info("Completed processing {} notifications", notifications.size)
    }

    private fun sendOne(notification: Notification) {
        try {
            val channel = channels.find { it.supports(notification.notificationType) }
            if (channel == null) {
                logger.error("No channel found for notification type: ${notification.notificationType}")
                notification.markFailed()
                notificationRepository.save(notification)
                return
            }

            channel.send(notification)
            notification.markSent()
            notificationRepository.save(notification)
            logger.info("Notification sent successfully: id=${notification.id}")
        } catch (e: Exception) {
            logger.error("Failed to send notification: id=${notification.id}", e)
            handleFailure(notification)
        }
    }

    private fun handleFailure(notification: Notification) {
        try {
            val nextRetryAt = RetryPolicy.calculateNextRetryAt(
                notification.retryCount,
                LocalDateTime.now()
            )
            if (nextRetryAt != null) {
                notification.scheduleRetry(nextRetryAt)
                notificationRepository.save(notification)
                logger.info("Notification scheduled for retry: id=${notification.id}, retryCount=${notification.retryCount}")
            } else {
                notification.markFailed()
                notificationRepository.save(notification)
                logger.warn("Notification max retries exceeded: id=${notification.id}")
            }
        } catch (retryError: Exception) {
            logger.error("Failed to handle retry for notification: id=${notification.id}", retryError)
        }
    }
}
