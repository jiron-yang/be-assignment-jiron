package com.jiron.notification.application.port.out

import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationType

/**
 * 알림 채널 추상화 인터페이스
 */
interface NotificationChannel {
    fun send(notification: Notification)
    fun supports(type: NotificationType): Boolean
}
