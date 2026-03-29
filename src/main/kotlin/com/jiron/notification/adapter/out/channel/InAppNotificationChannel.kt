package com.jiron.notification.adapter.out.channel

import com.jiron.notification.application.port.out.NotificationChannel
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 인앱 알림 채널 구현체
 */
@Component
class InAppNotificationChannel : NotificationChannel {

    private val logger = LoggerFactory.getLogger(InAppNotificationChannel::class.java)

    override fun send(notification: Notification) {
        logger.info("In-app notification sent to ${notification.recipientId}: ${notification.title}")
    }

    override fun supports(type: NotificationType): Boolean {
        return type == NotificationType.IN_APP
    }
}
