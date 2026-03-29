package com.jiron.notification.domain

/**
 * 알림 채널 추상화 인터페이스
 */
interface NotificationChannel {
    fun send(notification: Notification)
    fun supports(type: NotificationType): Boolean
}
