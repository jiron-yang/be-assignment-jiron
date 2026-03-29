package com.jiron.notification.application.port.out

import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * 알림 저장소 포트
 */
interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findById(id: Long): Notification?
    fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification>
    fun findStuckProcessing(before: LocalDateTime): List<Notification>
    fun findByIdempotencyKey(recipientId: String, notificationType: NotificationType, referenceEventId: String): Notification?
}
