package com.jiron.notification.adapter.`in`.web.dto

import com.jiron.notification.application.port.`in`.SendNotificationCommand
import com.jiron.notification.domain.vo.NotificationType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * 알림 발송 요청 DTO
 */
data class SendNotificationRequest(
    @field:NotBlank val recipientId: String,
    @field:NotNull val notificationType: NotificationType,
    @field:NotBlank val title: String,
    @field:NotBlank val content: String,
    @field:NotBlank val referenceEventId: String
) {
    fun toCommand(): SendNotificationCommand {
        return SendNotificationCommand(
            recipientId = recipientId,
            notificationType = notificationType,
            title = title,
            content = content,
            referenceEventId = referenceEventId
        )
    }
}
