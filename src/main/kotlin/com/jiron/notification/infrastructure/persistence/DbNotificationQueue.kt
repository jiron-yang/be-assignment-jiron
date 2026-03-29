package com.jiron.notification.infrastructure.persistence

import com.jiron.notification.application.NotificationQueue
import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationStatus
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

    override fun enqueue(notification: Notification): Notification {
        return notificationJpaRepository.save(notification)
    }

    @Transactional
    override fun dequeueForProcessing(batchSize: Int): List<Notification> {
        val notifications = notificationJpaRepository.findAllByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            NotificationStatus.PENDING,
            LocalDateTime.now(),
            PageRequest.of(0, batchSize)
        )

        return notifications.map { notification ->
            notification.startProcessing()
            notificationJpaRepository.save(notification)
        }
    }
}
