package com.jiron.notification.application.service

import com.jiron.notification.application.port.`in`.GetNotificationUseCase
import com.jiron.notification.application.port.`in`.SendNotificationCommand
import com.jiron.notification.application.port.`in`.SendNotificationUseCase
import com.jiron.notification.application.port.out.NotificationQueue
import com.jiron.notification.application.port.out.NotificationRepository
import com.jiron.notification.domain.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 유즈케이스 서비스
 */
@Service
class NotificationService(
    private val notificationQueue: NotificationQueue,
    private val notificationRepository: NotificationRepository
) : SendNotificationUseCase, GetNotificationUseCase {

    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * 알림 발송 요청
     * 멱등성 키(recipientId + notificationType + referenceEventId)로 중복 방지
     */
    @Transactional
    override fun send(command: SendNotificationCommand): Notification {
        val existing = notificationRepository.findByIdempotencyKey(
            command.recipientId,
            command.notificationType,
            command.referenceEventId
        )
        if (existing != null) {
            return existing
        }

        val notification = Notification(
            recipientId = command.recipientId,
            notificationType = command.notificationType,
            channel = command.notificationType.name,
            title = command.title,
            content = command.content,
            referenceEventId = command.referenceEventId
        )

        return try {
            notificationQueue.enqueue(notification)
        } catch (e: DataIntegrityViolationException) {
            logger.info("Duplicate notification request detected, returning existing: recipientId={}, type={}, eventId={}",
                command.recipientId, command.notificationType, command.referenceEventId)
            notificationRepository.findByIdempotencyKey(
                command.recipientId,
                command.notificationType,
                command.referenceEventId
            ) ?: throw e
        }
    }

    /** 알림 단건 조회 */
    override fun findById(id: Long): Notification? {
        return notificationRepository.findById(id)
    }

    /** 수신자별 알림 목록 조회 */
    override fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification> {
        return notificationRepository.findByRecipientId(recipientId, pageable)
    }
}
