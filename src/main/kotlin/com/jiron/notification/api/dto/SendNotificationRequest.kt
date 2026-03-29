package com.jiron.notification.api.dto

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
)
