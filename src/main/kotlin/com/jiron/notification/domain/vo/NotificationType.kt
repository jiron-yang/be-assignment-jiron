package com.jiron.notification.domain.vo

/**
 * 알림 유형
 */
enum class NotificationType(val description: String) {
    EMAIL("이메일"),
    IN_APP("인앱 알림")
}
