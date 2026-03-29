package com.jiron.notification.adapter.out.persistence

import com.jiron.notification.domain.model.Notification

/**
 * 알림 도메인 ↔ JPA Entity 변환 매퍼
 */
object NotificationMapper {

    fun toDomain(entity: NotificationEntity): Notification {
        return Notification(
            id = entity.id,
            recipientId = entity.recipientId,
            notificationType = entity.notificationType,
            channel = entity.channel,
            title = entity.title,
            content = entity.content,
            status = entity.status,
            retryCount = entity.retryCount,
            maxRetryCount = entity.maxRetryCount,
            nextRetryAt = entity.nextRetryAt,
            referenceEventId = entity.referenceEventId,
            createdAt = entity.createdAt,
            sentAt = entity.sentAt
        )
    }

    fun toEntity(domain: Notification): NotificationEntity {
        return NotificationEntity(
            id = domain.id,
            recipientId = domain.recipientId,
            notificationType = domain.notificationType,
            channel = domain.channel,
            title = domain.title,
            content = domain.content,
            status = domain.status,
            retryCount = domain.retryCount,
            maxRetryCount = domain.maxRetryCount,
            nextRetryAt = domain.nextRetryAt,
            referenceEventId = domain.referenceEventId,
            createdAt = domain.createdAt,
            sentAt = domain.sentAt
        )
    }
}
