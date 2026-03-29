package com.jiron.notification.scheduler

import com.jiron.notification.application.port.out.NotificationQueue
import com.jiron.notification.application.service.NotificationSender
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 알림 폴링 스케줄러
 * 주기적으로 발송 대기 중인 알림을 조회하여 발송 처리
 */
@Component
class NotificationPollingScheduler(
    private val notificationQueue: NotificationQueue,
    private val notificationSender: NotificationSender
) {

    private val logger = LoggerFactory.getLogger(NotificationPollingScheduler::class.java)

    @Scheduled(fixedDelay = 5000)
    fun poll() {
        logger.debug("Polling for pending notifications...")
        val notifications = notificationQueue.dequeueForProcessing(batchSize = 10)
        if (notifications.isNotEmpty()) {
            logger.info("Processing {} pending notifications", notifications.size)
            notificationSender.sendAll(notifications)
            logger.info("Completed processing {} notifications", notifications.size)
        }
    }
}
