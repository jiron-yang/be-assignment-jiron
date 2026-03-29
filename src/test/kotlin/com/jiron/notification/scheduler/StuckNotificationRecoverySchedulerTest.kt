package com.jiron.notification.scheduler

import com.jiron.notification.domain.Notification
import com.jiron.notification.domain.NotificationStatus
import com.jiron.notification.domain.NotificationType
import com.jiron.notification.infrastructure.persistence.NotificationJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class StuckNotificationRecoverySchedulerTest @Autowired constructor(
    private val stuckNotificationRecoveryScheduler: StuckNotificationRecoveryScheduler,
    private val notificationJpaRepository: NotificationJpaRepository
) {

    @Test
    @DisplayName("PROCESSING 상태로 5분 이상 경과한 알림을 PENDING으로 복구한다")
    fun recoverStuckNotifications() {
        // given
        val stuckNotification = createNotification("stuck-event-1").apply {
            startProcessing()
        }
        notificationJpaRepository.save(stuckNotification)

        // updatedAt을 6분 전으로 강제 설정
        notificationJpaRepository.flush()
        val sixMinutesAgo = LocalDateTime.now().minusMinutes(6)
        stuckNotification.updatedAt = sixMinutesAgo
        notificationJpaRepository.saveAndFlush(stuckNotification)

        // when
        stuckNotificationRecoveryScheduler.recover()

        // then
        val recovered = notificationJpaRepository.findById(stuckNotification.id).get()
        assertThat(recovered.status).isEqualTo(NotificationStatus.PENDING)
    }

    @Test
    @DisplayName("PROCESSING 상태로 5분 미만인 알림은 변경하지 않는다")
    fun doNotRecoverRecentProcessingNotifications() {
        // given
        val recentNotification = createNotification("recent-event-1").apply {
            startProcessing()
        }
        notificationJpaRepository.saveAndFlush(recentNotification)

        // when
        stuckNotificationRecoveryScheduler.recover()

        // then
        val unchanged = notificationJpaRepository.findById(recentNotification.id).get()
        assertThat(unchanged.status).isEqualTo(NotificationStatus.PROCESSING)
    }

    private fun createNotification(referenceEventId: String): Notification {
        return Notification(
            recipientId = "test-recipient",
            notificationType = NotificationType.EMAIL,
            channel = "EMAIL",
            title = "Test Title",
            content = "Test Content",
            referenceEventId = referenceEventId
        )
    }
}
