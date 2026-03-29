package com.jiron.notification.adapter.`in`.scheduler

import com.jiron.notification.application.port.`in`.RecoverStuckNotificationsUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * PROCESSING 상태에서 stuck된 알림을 복구하는 스케줄러
 */
@Component
class StuckNotificationRecoveryScheduler(
    private val recoverStuckNotificationsUseCase: RecoverStuckNotificationsUseCase
) {

    @Scheduled(fixedDelay = 60000)
    fun recover() {
        recoverStuckNotificationsUseCase.execute()
    }
}
