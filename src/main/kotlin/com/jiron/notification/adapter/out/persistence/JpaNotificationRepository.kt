package com.jiron.notification.adapter.out.persistence

import com.jiron.notification.application.port.`in`.NotificationView
import com.jiron.notification.application.port.out.NotificationQueryPort
import com.jiron.notification.application.port.out.NotificationRepository
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationIdempotencyKey
import com.jiron.notification.domain.vo.NotificationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 알림 저장소 JPA 구현체 (Command + Query)
 */
@Component
class JpaNotificationRepository(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationRepository, NotificationQueryPort {

    // === Command ===

    override fun save(notification: Notification): Notification {
        val entity = NotificationMapper.toEntity(notification)
        val saved = notificationJpaRepository.save(entity)
        return NotificationMapper.toDomain(saved)
    }

    override fun findByIdempotencyKey(key: NotificationIdempotencyKey): Notification? {
        return notificationJpaRepository.findByRecipientIdAndNotificationTypeAndReferenceEventId(
            key.recipientId, key.notificationType, key.referenceEventId
        )?.let { NotificationMapper.toDomain(it) }
    }

    override fun findStuckProcessing(before: LocalDateTime): List<Notification> {
        return notificationJpaRepository.findAllByStatusAndUpdatedAtBefore(
            NotificationStatus.PROCESSING, before
        ).map { NotificationMapper.toDomain(it) }
    }

    // === Query (Read Model) ===

    override fun findById(id: Long): NotificationView? {
        return notificationJpaRepository.findById(id).orElse(null)
            ?.let { NotificationMapper.toView(it) }
    }

    override fun findByRecipientId(recipientId: String, pageable: Pageable): Page<NotificationView> {
        return notificationJpaRepository.findAllByRecipientId(recipientId, pageable)
            .map { NotificationMapper.toView(it) }
    }
}
