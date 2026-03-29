package com.jiron.notification.infrastructure.channel

import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationChannel
import com.jiron.notification.domain.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 이메일 알림 채널 구현체
 */
@Component
class EmailNotificationChannel : NotificationChannel {

    private val logger = LoggerFactory.getLogger(EmailNotificationChannel::class.java)

    override fun send(notification: Notification) {
        logger.info("Email sent to ${notification.recipientId}: ${notification.title}")
    }

    override fun supports(type: NotificationType): Boolean {
        return type == NotificationType.EMAIL
    }
}
