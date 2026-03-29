package com.jiron.notification.domain.vo

/**
 * 알림 수신자 식별자
 */
@JvmInline
value class RecipientId(val value: String) {
    init {
        require(value.isNotBlank()) { "RecipientId must not be blank" }
    }
}
