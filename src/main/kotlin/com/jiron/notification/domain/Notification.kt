package com.jiron.notification.domain

import java.time.LocalDateTime

/**
 * 알림 도메인 모델
 */
class Notification(
    val id: Long = 0L,
    val recipientId: String,
    val notificationType: NotificationType,
    val channel: String,
    val title: String,
    val content: String,
    var status: NotificationStatus = NotificationStatus.PENDING,
    var retryCount: Int = 0,
    val maxRetryCount: Int = 3,
    var nextRetryAt: LocalDateTime = LocalDateTime.now(),
    val referenceEventId: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
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
    }

    /**
     * 발송 실패 처리: PROCESSING → FAILED
     */
    fun markFailed() {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot mark as failed: current status is $status, expected PROCESSING"
        }
        status = NotificationStatus.FAILED
    }

    /**
     * PROCESSING 상태에서 PENDING으로 복구 (stuck 상태 복구용)
     */
    fun resetToPending() {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot reset to pending: current status is $status, expected PROCESSING"
        }
        status = NotificationStatus.PENDING
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
    }
}
