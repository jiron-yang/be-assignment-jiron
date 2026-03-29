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

        val id = notificationService.execute(command)

        assertThat(id).isGreaterThan(0)
    }

    @Test
    @DisplayName("동일 멱등성 키로 중복 요청 시 같은 id 반환")
    fun send_idempotent_returnsSameId() {
        val command = SendNotificationCommand(
            recipientId = "user-idempotent-1",
            notificationType = NotificationType.EMAIL,
            title = "제목",
            content = "내용",
            referenceEventId = "event-idempotent-1"
        )

        val firstId = notificationService.execute(command)
        val secondId = notificationService.execute(command)

        assertThat(firstId).isEqualTo(secondId)
    }

    @Test
    @DisplayName("존재하지 않는 id 조회 시 null 반환")
    fun findById_notFound_returnsNull() {
        val result = notificationService.findById(999999L)

        assertThat(result).isNull()
    }

    @Test
    @DisplayName("등록 후 조회 시 NotificationView 반환 (createdAt 포함)")
    fun findById_returnsView() {
        val command = SendNotificationCommand(
            recipientId = "user-view-1",
            notificationType = NotificationType.IN_APP,
            title = "조회 테스트",
            content = "내용",
            referenceEventId = "event-view-1"
        )

        val id = notificationService.execute(command)
        val view = notificationService.findById(id)

        assertThat(view).isNotNull
        assertThat(view!!.id).isEqualTo(id)
        assertThat(view.createdAt).isNotNull()
        assertThat(view.recipientId).isEqualTo("user-view-1")
    }
}
