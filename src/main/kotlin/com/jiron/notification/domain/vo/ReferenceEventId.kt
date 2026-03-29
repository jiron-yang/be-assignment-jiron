package com.jiron.notification.domain.vo

/**
 * 참조 이벤트 식별자
 */
@JvmInline
value class ReferenceEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReferenceEventId must not be blank" }
    }
}
