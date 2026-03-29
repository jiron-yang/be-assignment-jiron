package com.jiron.notification.adapter.`in`.web.dto

import com.jiron.notification.application.port.`in`.NotificationView
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
        fun from(view: NotificationView): NotificationResponse {
            return NotificationResponse(
                id = view.id,
                recipientId = view.recipientId,
                notificationType = view.notificationType,
                status = view.status,
                title = view.title,
                content = view.content,
                retryCount = view.retryCount,
                referenceEventId = view.referenceEventId,
                createdAt = view.createdAt,
                sentAt = view.sentAt
            )
        }
    }
}
