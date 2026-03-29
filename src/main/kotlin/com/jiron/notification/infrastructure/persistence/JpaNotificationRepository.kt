package com.jiron.notification.infrastructure.persistence

import com.jiron.notification.application.port.out.NotificationRepository
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알림 저장소 JPA 구현체
 */
@Component
class JpaNotificationRepository(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationRepository {

    override fun save(notification: Notification): Notification {
        val entity = NotificationMapper.toEntity(notification)
        val saved = notificationJpaRepository.save(entity)
        return NotificationMapper.toDomain(saved)
    }

    override fun findById(id: Long): Notification? {
        return notificationJpaRepository.findById(id).orElse(null)
            ?.let { NotificationMapper.toDomain(it) }
    }

    override fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification> {
        return notificationJpaRepository.findAllByRecipientId(recipientId, pageable)
            .map { NotificationMapper.toDomain(it) }
    }

    override fun findStuckProcessing(before: LocalDateTime): List<Notification> {
        return notificationJpaRepository.findAllByStatusAndUpdatedAtBefore(
            NotificationStatus.PROCESSING, before
        ).map { NotificationMapper.toDomain(it) }
    }

    override fun findByIdempotencyKey(
        recipientId: String,
        notificationType: NotificationType,
        referenceEventId: String
    ): Notification? {
        return notificationJpaRepository.findByRecipientIdAndNotificationTypeAndReferenceEventId(
            recipientId, notificationType, referenceEventId
        )?.let { NotificationMapper.toDomain(it) }
    }
}
