package com.jiron.notification.application

import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationType
import com.jiron.notification.infrastructure.persistence.NotificationJpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * 알림 Provider 레이어
 * Service가 Repository에 직접 의존하지 않도록 중간 레이어 역할
 */
@Component
class NotificationProvider(
    private val notificationJpaRepository: NotificationJpaRepository
) {

    fun save(notification: Notification): Notification {
        return notificationJpaRepository.save(notification)
    }

    fun findById(id: Long): Notification? {
        return notificationJpaRepository.findById(id).orElse(null)
    }

    fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification> {
        return notificationJpaRepository.findAllByRecipientId(recipientId, pageable)
    }

    fun findByIdempotencyKey(
        recipientId: String,
        notificationType: NotificationType,
        referenceEventId: String
    ): Notification? {
        return notificationJpaRepository.findByRecipientIdAndNotificationTypeAndReferenceEventId(
            recipientId, notificationType, referenceEventId
        )
    }
}
