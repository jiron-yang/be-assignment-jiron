package com.jiron.notification.application.port.out

import com.jiron.notification.application.port.`in`.NotificationView
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * 알림 조회 전용 포트 (Read Model)
 */
interface NotificationQueryPort {
    fun findById(id: Long): NotificationView?
    fun findByRecipientId(recipientId: String, pageable: Pageable): Page<NotificationView>
}
