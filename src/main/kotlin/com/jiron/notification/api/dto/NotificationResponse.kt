package com.jiron.notification.api.dto

import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import java.time.LocalDateTime

/**
 * 알림 응답 DTO
 */
data class NotificationResponse(
    val id: Long,
    val recipientId: String,
    val notificationType: NotificationType,
    val status: NotificationStatus,
    val title: String,
    val content: String,
    val retryCount: Int,
    val referenceEventId: String,
    val createdAt: LocalDateTime,
    val sentAt: LocalDateTime?
) {
    companion object {
        fun from(notification: Notification): NotificationResponse {
            return NotificationResponse(
                id = notification.id,
                recipientId = notification.recipientId,
                notificationType = notification.notificationType,
                status = notification.status,
                title = notification.title,
                content = notification.content,
                retryCount = notification.retryCount,
                referenceEventId = notification.referenceEventId,
                createdAt = notification.createdAt,
                sentAt = notification.sentAt
            )
        }
    }
}
