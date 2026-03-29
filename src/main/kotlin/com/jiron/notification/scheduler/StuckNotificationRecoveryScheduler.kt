package com.jiron.notification.scheduler

import com.jiron.notification.application.port.out.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * PROCESSING 상태에서 stuck된 알림을 복구하는 스케줄러
 * 5분 이상 PROCESSING 상태인 알림을 PENDING으로 되돌린다.
 */
@Component
class StuckNotificationRecoveryScheduler(
    private val notificationRepository: NotificationRepository
) {

    private val logger = LoggerFactory.getLogger(StuckNotificationRecoveryScheduler::class.java)

    @Transactional
    @Scheduled(fixedDelay = 60000)
    fun recover() {
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
