package com.jiron.notification.adapter.out.persistence

import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 알림 JPA Entity
 */
@Entity
@Table(name = "notifications")
class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    val notificationType: NotificationType = NotificationType.EMAIL,

    @Column(name = "channel", nullable = false)
    val channel: String = "",

    @Column(name = "title", nullable = false)
    val title: String = "",

    @Column(name = "content", nullable = false)
    val content: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: NotificationStatus = NotificationStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "max_retry_count", nullable = false)
    val maxRetryCount: Int = 3,

    @Column(name = "next_retry_at", nullable = false)
    var nextRetryAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "reference_event_id", nullable = false)
    val referenceEventId: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
