package com.jiron.notification.application.port.`in`

import com.jiron.notification.domain.model.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * 알림 조회 유즈케이스
 */
interface GetNotificationUseCase {
    fun findById(id: Long): Notification?
    fun findByRecipientId(recipientId: String, pageable: Pageable): Page<Notification>
}
