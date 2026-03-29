package com.jiron.notification.application.port.out

import com.jiron.notification.domain.model.Notification

/**
 * 알림 큐 추상화 인터페이스
 * Kafka 등 다른 메시지 브로커로 교체 가능하도록 분리
 */
interface NotificationQueue {
    /** 발송 대기 중인 알림 조회 (PENDING + nextRetryAt <= now) */
    fun dequeueForProcessing(batchSize: Int): List<Notification>
}
