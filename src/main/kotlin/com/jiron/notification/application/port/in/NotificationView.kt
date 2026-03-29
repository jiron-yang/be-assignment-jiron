package com.jiron.notification.application.port.`in`

import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import java.time.LocalDateTime

/**
 * 알림 조회 전용 Read Model
 */
data class NotificationView(
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
)
