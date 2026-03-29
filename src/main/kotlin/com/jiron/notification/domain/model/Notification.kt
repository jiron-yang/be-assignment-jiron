package com.jiron.notification.domain.model

import com.jiron.notification.domain.vo.NotificationStatus
import com.jiron.notification.domain.vo.NotificationType
import com.jiron.notification.domain.vo.RecipientId
import com.jiron.notification.domain.vo.ReferenceEventId
import com.jiron.notification.domain.vo.RetryPolicy
import java.time.LocalDateTime

/**
 * 알림 도메인 모델 (Aggregate Root)
 *
 * 모든 상태 변경은 도메인 메서드를 통해서만 가능하다.
 */
class Notification(
    val id: Long = 0L,
    val recipientId: RecipientId,
    val notificationType: NotificationType,
    val title: String,
    val content: String,
    status: NotificationStatus = NotificationStatus.PENDING,
    retryCount: Int = 0,
    val maxRetryCount: Int = RetryPolicy.MAX_RETRY_COUNT,
    nextRetryAt: LocalDateTime = LocalDateTime.now(),
    val referenceEventId: ReferenceEventId,
    sentAt: LocalDateTime? = null
) {

    var status: NotificationStatus = status
        private set

    var retryCount: Int = retryCount
        private set

    var nextRetryAt: LocalDateTime = nextRetryAt
        private set

    var sentAt: LocalDateTime? = sentAt
        private set

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
     * 발송 실패 시 재시도 가능 여부를 판단하여 재시도 스케줄링 또는 최종 실패 처리
     */
    fun handleSendFailure(now: LocalDateTime) {
        require(status == NotificationStatus.PROCESSING) {
            "Cannot handle failure: current status is $status, expected PROCESSING"
        }
        val nextRetry = RetryPolicy.calculateNextRetryAt(retryCount, now)
        if (nextRetry != null) {
            retryCount++
            nextRetryAt = nextRetry
            status = NotificationStatus.PENDING
        } else {
            status = NotificationStatus.FAILED
        }
    }

    /**
     * 재시도 가능 여부 확인
     */
    fun canRetry(): Boolean = retryCount < maxRetryCount
}
