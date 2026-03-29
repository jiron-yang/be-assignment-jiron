package com.jiron.notification.application

import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationStatus
import com.jiron.notification.domain.NotificationType
import com.jiron.notification.infrastructure.persistence.NotificationJpaRepository
import com.jiron.notification.infrastructure.persistence.NotificationMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알림 Provider 레이어
 * Service가 Repository에 직접 의존하지 않도록 중간 레이어 역할
 */
@Component
class NotificationProvider(
    private val notificationJpaRepository: NotificationJpaRepository
) {

    fun save(notification: Notification): Notification {
        val entity = NotificationMapper.toEntity(notification)
        val saved = notificationJpaRepository.save(entity)
        return NotificationMapper.toDomain(saved)
    }

    fun findById(id: Long): Notification? {
        return notificationJpaRepository.findById(id).orElse(null)
            ?.let { NotificationMapper.toDomain(it) }
    }

    fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification> {
        return notificationJpaRepository.findAllByRecipientId(recipientId, pageable)
            .map { NotificationMapper.toDomain(it) }
    }

    fun findStuckProcessing(before: LocalDateTime): List<Notification> {
        return notificationJpaRepository.findAllByStatusAndUpdatedAtBefore(
            NotificationStatus.PROCESSING, before
        ).map { NotificationMapper.toDomain(it) }
    }

    fun findByIdempotencyKey(
        recipientId: String,
        notificationType: NotificationType,
        referenceEventId: String
    ): Notification? {
        return notificationJpaRepository.findByRecipientIdAndNotificationTypeAndReferenceEventId(
            recipientId, notificationType, referenceEventId
        )?.let { NotificationMapper.toDomain(it) }
    }
}
