package com.jiron.notification.application.service

import com.jiron.notification.application.port.`in`.RecoverStuckNotificationsUseCase
import com.jiron.notification.application.port.out.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * PROCESSING stuck 알림 복구 서비스
 */
@Service
class StuckNotificationRecoveryService(
    private val notificationRepository: NotificationRepository
) : RecoverStuckNotificationsUseCase {

    private val logger = LoggerFactory.getLogger(StuckNotificationRecoveryService::class.java)

    @Transactional
    override fun execute() {
        val threshold = LocalDateTime.now().minusMinutes(5)
        val stuckNotifications = notificationRepository.findStuckProcessing(threshold)

        stuckNotifications.forEach { notification ->
            notification.resetToPending()
            notificationRepository.save(notification)
        }

        if (stuckNotifications.isNotEmpty()) {
            logger.info("Recovered {} stuck notifications", stuckNotifications.size)
        }
    }
}
