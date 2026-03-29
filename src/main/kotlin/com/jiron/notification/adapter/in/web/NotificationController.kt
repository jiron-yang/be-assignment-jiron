package com.jiron.notification.adapter.`in`.web

import com.jiron.notification.adapter.`in`.web.dto.NotificationResponse
import com.jiron.notification.adapter.`in`.web.dto.SendNotificationRequest
import com.jiron.notification.application.port.`in`.GetNotificationUseCase
import com.jiron.notification.application.port.`in`.SendNotificationUseCase
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 알림 API 컨트롤러
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val sendNotificationUseCase: SendNotificationUseCase,
    private val getNotificationUseCase: GetNotificationUseCase
) {

    /** 알림 발송 요청 */
    @PostMapping
    fun send(@Valid @RequestBody request: SendNotificationRequest): ResponseEntity<NotificationResponse> {
        val notification = sendNotificationUseCase.send(request.toCommand())
        return ResponseEntity.status(201).body(NotificationResponse.from(notification))
    }

    /** 알림 단건 조회 */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<NotificationResponse> {
        val notification = getNotificationUseCase.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(NotificationResponse.from(notification))
    }

    /** 수신자별 알림 목록 조회 */
    @GetMapping
    fun findByRecipientId(
        @RequestParam recipientId: String,
        pageable: Pageable
    ): ResponseEntity<Page<NotificationResponse>> {
        val page = getNotificationUseCase.findByRecipientId(recipientId, pageable)
        return ResponseEntity.ok(page.map { NotificationResponse.from(it) })
    }
}
