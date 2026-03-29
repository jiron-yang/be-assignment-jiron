package com.jiron.notification.application

import com.jiron.notification.api.dto.SendNotificationRequest
import com.jiron.notification.domain.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * 알림 유즈케이스 서비스
 */
@Service
class NotificationService(
    private val notificationQueue: NotificationQueue,
    private val notificationProvider: NotificationProvider
) {

    /**
     * 알림 발송 요청
     * 멱등성 키(recipientId + notificationType + referenceEventId)로 중복 방지
     */
    fun send(request: SendNotificationRequest): Notification {
        val existing = notificationProvider.findByIdempotencyKey(
            request.recipientId,
            request.notificationType,
            request.referenceEventId
        )
        if (existing != null) {
            return existing
        }

        val notification = Notification(
            recipientId = request.recipientId,
            notificationType = request.notificationType,
            channel = request.notificationType.name,
            title = request.title,
            content = request.content,
            referenceEventId = request.referenceEventId
        )

        return notificationQueue.enqueue(notification)
    }

    /** 알림 단건 조회 */
    fun findById(id: Long): Notification? {
        return notificationProvider.findById(id)
    }

    /** 수신자별 알림 목록 조회 */
    fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification> {
        return notificationProvider.findByRecipientId(recipientId, pageable)
    }
}
