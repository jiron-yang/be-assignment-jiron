package com.jiron.notification.infrastructure.persistence

import com.jiron.notification.domain.NotificationStatus
import com.jiron.notification.domain.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/**
 * 알림 JPA 레포지토리
 */
interface NotificationJpaRepository : JpaRepository<NotificationEntity, Long> {

    fun findAllByStatusAndNextRetryAtBefore(
        status: NotificationStatus,
        now: LocalDateTime
    ): List<NotificationEntity>

    fun findAllByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
        status: NotificationStatus,
        now: LocalDateTime,
        pageable: Pageable
    ): List<NotificationEntity>

    fun findAllByStatusAndUpdatedAtBefore(
        status: NotificationStatus,
        before: LocalDateTime
    ): List<NotificationEntity>

    fun findAllByRecipientId(
        recipientId: String,
        pageable: Pageable
    ): Page<NotificationEntity>

    fun findByRecipientIdAndNotificationTypeAndReferenceEventId(
        recipientId: String,
        notificationType: NotificationType,
        referenceEventId: String
    ): NotificationEntity?
}
