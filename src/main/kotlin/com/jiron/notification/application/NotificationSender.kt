package com.jiron.notification.application

import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationChannel
import com.jiron.notification.domain.RetryPolicy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알림 발송 처리 컴포넌트
 */
@Component
class NotificationSender(
    private val channels: List<NotificationChannel>,
    private val notificationProvider: NotificationProvider
) {

    private val logger = LoggerFactory.getLogger(NotificationSender::class.java)

    /**
     * 알림 목록 일괄 발송
     */
    fun sendAll(notifications: List<Notification>) {
        for (notification in notifications) {
            try {
                val channel = channels.find { it.supports(notification.notificationType) }
                if (channel == null) {
                    logger.error("No channel found for notification type: ${notification.notificationType}")
                    notification.markFailed()
                    notificationProvider.save(notification)
                    continue
                }

                channel.send(notification)
                notification.markSent()
                notificationProvider.save(notification)
                logger.info("Notification sent successfully: id=${notification.id}")
            } catch (e: Exception) {
                logger.error("Failed to send notification: id=${notification.id}", e)
                try {
                    val nextRetryAt = RetryPolicy.calculateNextRetryAt(
                        notification.retryCount,
                        LocalDateTime.now()
                    )
                    if (nextRetryAt != null) {
                        notification.scheduleRetry(nextRetryAt)
                        notificationProvider.save(notification)
                        logger.info("Notification scheduled for retry: id=${notification.id}, retryCount=${notification.retryCount}")
                    } else {
                        notification.markFailed()
                        notificationProvider.save(notification)
                        logger.warn("Notification max retries exceeded: id=${notification.id}")
                    }
                } catch (retryError: Exception) {
                    logger.error("Failed to handle retry for notification: id=${notification.id}", retryError)
                }
            }
        }
    }
}
