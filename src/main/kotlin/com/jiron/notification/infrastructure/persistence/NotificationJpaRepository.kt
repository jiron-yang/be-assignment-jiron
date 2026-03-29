package com.jiron.notification.infrastructure.persistence

import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/**
 * 알림 JPA 레포지토리
 */
interface NotificationJpaRepository : JpaRepository<Notification, Long> {

    fun findAllByStatusAndNextRetryAtBefore(
        status: NotificationStatus,
        now: LocalDateTime
    ): List<Notification>

    fun findAllByStatusAndUpdatedAtBefore(
        status: NotificationStatus,
        before: LocalDateTime
    ): List<Notification>

    fun findAllByRecipientId(
        recipientId: String,
        pageable: Pageable
    ): Page<Notification>
}
