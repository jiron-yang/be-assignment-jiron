package com.jiron.notification.adapter.out.persistence

import com.jiron.notification.application.port.out.NotificationQueue
import com.jiron.notification.domain.model.Notification
import com.jiron.notification.domain.vo.NotificationStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * DB 기반 알림 큐 구현체
 */
@Component
class DbNotificationQueue(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationQueue {

    @Transactional
    override fun dequeueForProcessing(batchSize: Int): List<Notification> {
        val entities = notificationJpaRepository.findAllByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            NotificationStatus.PENDING,
            LocalDateTime.now(),
            PageRequest.of(0, batchSize)
        )

        return entities.map { entity ->
            val domain = NotificationMapper.toDomain(entity)
            domain.startProcessing()
            val updatedEntity = NotificationMapper.toEntity(domain)
            val saved = notificationJpaRepository.save(updatedEntity)
            NotificationMapper.toDomain(saved)
        }
    }
}
