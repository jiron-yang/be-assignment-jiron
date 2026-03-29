package com.jiron.notification.domain.vo

/**
 * 알림 멱등성 키
 * (recipientId, notificationType, referenceEventId) 조합으로 중복 발송을 방지한다.
 */
data class NotificationIdempotencyKey(
    val recipientId: String,
    val notificationType: NotificationType,
    val referenceEventId: String
)
