package com.jiron.notification.adapter.out.persistence

import com.jiron.notification.application.port.`in`.NotificationView
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.RecipientId
import com.jiron.notification.domain.vo.ReferenceEventId

/**
 * 알림 도메인 ↔ JPA Entity 변환 매퍼
 */
object NotificationMapper {

    fun toDomain(entity: NotificationEntity): Notification {
        return Notification(
            id = entity.id,
            recipientId = RecipientId(entity.recipientId),
            notificationType = entity.notificationType,
            title = entity.title,
            content = entity.content,
            status = entity.status,
            retryCount = entity.retryCount,
            maxRetryCount = entity.maxRetryCount,
            nextRetryAt = entity.nextRetryAt,
            referenceEventId = ReferenceEventId(entity.referenceEventId),
            sentAt = entity.sentAt
        )
    }

    fun toEntity(domain: Notification): NotificationEntity {
        return NotificationEntity(
            id = domain.id,
            recipientId = domain.recipientId.value,
            notificationType = domain.notificationType,
            channel = domain.notificationType.name,
            title = domain.title,
            content = domain.content,
            status = domain.status,
            retryCount = domain.retryCount,
            maxRetryCount = domain.maxRetryCount,
            nextRetryAt = domain.nextRetryAt,
            referenceEventId = domain.referenceEventId.value,
            sentAt = domain.sentAt
        )
    }

    fun toView(entity: NotificationEntity): NotificationView {
        return NotificationView(
            id = entity.id,
            recipientId = entity.recipientId,
            notificationType = entity.notificationType,
            status = entity.status,
            title = entity.title,
            content = entity.content,
            retryCount = entity.retryCount,
            referenceEventId = entity.referenceEventId,
            createdAt = entity.createdAt,
            sentAt = entity.sentAt
        )
    }
}
