package com.jiron.notification.adapter.`in`.scheduler

import com.jiron.notification.application.port.`in`.ProcessPendingNotificationsUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 알림 폴링 스케줄러
 * 주기적으로 발송 대기 중인 알림을 조회하여 발송 처리
 */
@Component
class NotificationPollingScheduler(
    private val processPendingNotificationsUseCase: ProcessPendingNotificationsUseCase
) {

    @Scheduled(fixedDelay = 5000)
    fun poll() {
        processPendingNotificationsUseCase.execute()
    }
}
