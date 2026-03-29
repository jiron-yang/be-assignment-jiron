package com.jiron.notification.application.port.`in`

import com.jiron.notification.domain.vo.NotificationIdempotencyKey
import com.jiron.notification.domain.vo.NotificationType
import com.jiron.notification.domain.vo.RecipientId
import com.jiron.notification.domain.vo.ReferenceEventId

/**
 * 알림 발송 요청 유즈케이스
 */
interface SendNotificationUseCase {
    fun execute(command: SendNotificationCommand): Long
}

/**
 * 알림 발송 요청 커맨드
 */
data class SendNotificationCommand(
    val recipientId: RecipientId,
    val notificationType: NotificationType,
    val title: String,
    val content: String,
    val referenceEventId: ReferenceEventId
) {
    val idempotencyKey: NotificationIdempotencyKey
        get() = NotificationIdempotencyKey(recipientId, notificationType, referenceEventId)
}
