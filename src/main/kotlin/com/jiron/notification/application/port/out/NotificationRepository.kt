package com.jiron.notification.application.port.out

import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationIdempotencyKey
import java.time.LocalDateTime

/**
 * 알림 저장소 포트 (Command 측)
 */
interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findByIdempotencyKey(key: NotificationIdempotencyKey): Notification?
    fun findStuckProcessing(before: LocalDateTime): List<Notification>
}
