package com.jiron.notification.domain.vo

/**
 * 알림 발송 상태
 *
 * 각 상태에서 허용되는 전이를 boolean 프로퍼티로 선언한다.
 */
enum class NotificationStatus(
    val description: String,
    val canProcess: Boolean = false,
    val canComplete: Boolean = false,
    val canFail: Boolean = false,
    val canRetry: Boolean = false
) {
    PENDING("발송 대기", canProcess = true),
    PROCESSING("발송 중", canComplete = true, canFail = true, canRetry = true),
    SENT("발송 완료"),
    FAILED("발송 실패");
}
