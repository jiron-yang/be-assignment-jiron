package com.jiron.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 알림 엔티티
 */
@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    val notificationType: NotificationType,

    @Column(name = "channel", nullable = false)
    val channel: String,

    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "content", nullable = false)
    val content: String,

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
    val referenceEventId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null
) {

    /**
     * 발송 처리 시작: PENDING → PROCESSING
     */
    fun startProcessing() {
        require(status == NotificationStatus.PENDING) {
            "Cannot start processing: current status is $status, expected PENDING"
        }
        status = NotificationStatus.PROCESSING
        updatedAt = LocalDateTime.now()
    }

    /**
     * 발송 완료 처리: PROCESSING → SENT
     */
    fun markSent() {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot mark as sent: current status is $status, expected PROCESSING"
        }
        status = NotificationStatus.SENT
        sentAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 발송 실패 처리: PROCESSING → FAILED
     */
    fun markFailed() {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot mark as failed: current status is $status, expected PROCESSING"
        }
        status = NotificationStatus.FAILED
        updatedAt = LocalDateTime.now()
    }

    /**
     * PROCESSING 상태에서 PENDING으로 복구 (stuck 상태 복구용)
     */
    fun resetToPending() {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot reset to pending: current status is $status, expected PROCESSING"
        }
        status = NotificationStatus.PENDING
        updatedAt = LocalDateTime.now()
    }

    /**
     * 재시도 스케줄링: PROCESSING → PENDING (retryCount 증가)
     */
    fun scheduleRetry(nextRetryAt: LocalDateTime) {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot schedule retry: current status is $status, expected PROCESSING"
        }
        retryCount++
        this.nextRetryAt = nextRetryAt
        status = NotificationStatus.PENDING
        updatedAt = LocalDateTime.now()
    }
}
