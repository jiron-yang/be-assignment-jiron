package com.jiron.notification.scheduler

import com.jiron.notification.domain.NotificationStatus
import com.jiron.notification.domain.NotificationType
import com.jiron.notification.infrastructure.persistence.NotificationEntity
import com.jiron.notification.infrastructure.persistence.NotificationJpaRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@Transactional
class StuckNotificationRecoverySchedulerTest @Autowired constructor(
    private val stuckNotificationRecoveryScheduler: StuckNotificationRecoveryScheduler,
    private val notificationJpaRepository: NotificationJpaRepository,
    private val entityManager: EntityManager
) {

    @Test
    @DisplayName("PROCESSING 상태로 5분 이상 경과한 알림을 PENDING으로 복구한다")
    fun recoverStuckNotifications() {
        // given
        val stuckEntity = createEntity("stuck-event-1").apply {
            status = NotificationStatus.PROCESSING
        }
        notificationJpaRepository.saveAndFlush(stuckEntity)

        // updatedAt을 6분 전으로 강제 설정 (네이티브 쿼리로 @PreUpdate 우회)
        val sixMinutesAgo = LocalDateTime.now().minusMinutes(6)
        entityManager.createNativeQuery("UPDATE notifications SET updated_at = ? WHERE id = ?")
            .setParameter(1, sixMinutesAgo)
            .setParameter(2, stuckEntity.id)
            .executeUpdate()
        entityManager.flush()
        entityManager.clear()

        // when
        stuckNotificationRecoveryScheduler.recover()

        // then
        val recovered = notificationJpaRepository.findById(stuckEntity.id).get()
        assertThat(recovered.status).isEqualTo(NotificationStatus.PENDING)
    }

    @Test
    @DisplayName("PROCESSING 상태로 5분 미만인 알림은 변경하지 않는다")
    fun doNotRecoverRecentProcessingNotifications() {
        // given
        val recentEntity = createEntity("recent-event-1").apply {
            status = NotificationStatus.PROCESSING
        }
        notificationJpaRepository.saveAndFlush(recentEntity)

        // when
        stuckNotificationRecoveryScheduler.recover()

        // then
        val unchanged = notificationJpaRepository.findById(recentEntity.id).get()
        assertThat(unchanged.status).isEqualTo(NotificationStatus.PROCESSING)
    }

    private fun createEntity(referenceEventId: String): NotificationEntity {
        return NotificationEntity(
            recipientId = "test-recipient",
            notificationType = NotificationType.EMAIL,
            channel = "EMAIL",
            title = "Test Title",
            content = "Test Content",
            referenceEventId = referenceEventId
        )
    }
}
