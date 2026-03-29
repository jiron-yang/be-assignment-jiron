package com.jiron.notification.application.service

import com.jiron.notification.application.port.`in`.SendNotificationCommand
import com.jiron.notification.domain.vo.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class NotificationServiceTest @Autowired constructor(
    private val notificationService: NotificationService
) {

    @Test
    @DisplayName("알림 등록 성공")
    fun send_success() {
        val command = SendNotificationCommand(
            recipientId = "user-service-1",
            notificationType = NotificationType.EMAIL,
            title = "환영합니다",
            content = "가입을 축하합니다",
            referenceEventId = "event-service-1"
        )

        val result = notificationService.send(command)

        assertThat(result.id).isGreaterThan(0)
        assertThat(result.recipientId).isEqualTo("user-service-1")
        assertThat(result.notificationType).isEqualTo(NotificationType.EMAIL)
    }

    @Test
    @DisplayName("동일 멱등성 키로 중복 요청 시 기존 알림 반환 (id 동일)")
    fun send_idempotent_returnsSameNotification() {
        val command = SendNotificationCommand(
            recipientId = "user-idempotent-1",
            notificationType = NotificationType.EMAIL,
            title = "제목",
            content = "내용",
            referenceEventId = "event-idempotent-1"
        )

        val first = notificationService.send(command)
        val second = notificationService.send(command)

        assertThat(first.id).isEqualTo(second.id)
    }

    @Test
    @DisplayName("존재하지 않는 id 조회 시 null 반환")
    fun findById_notFound_returnsNull() {
        val result = notificationService.findById(999999L)

        assertThat(result).isNull()
    }
}
