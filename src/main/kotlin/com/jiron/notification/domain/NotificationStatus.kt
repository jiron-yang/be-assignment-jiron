package com.jiron.notification.domain

/**
 * 알림 발송 상태
 */
enum class NotificationStatus(val description: String) {
    PENDING("발송 대기"),
    PROCESSING("발송 중"),
    SENT("발송 완료"),
    FAILED("발송 실패")
}
