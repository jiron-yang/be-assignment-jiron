package com.jiron.notification.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.jiron.notification.api.dto.SendNotificationRequest
import com.jiron.notification.domain.vo.NotificationType
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper
) {

    @Test
    @DisplayName("POST /api/notifications → 201 Created")
    fun send_returns201() {
        val request = SendNotificationRequest(
            recipientId = "ctrl-user-1",
            notificationType = NotificationType.EMAIL,
            title = "알림 제목",
            content = "알림 내용",
            referenceEventId = "ctrl-event-1"
        )

        mockMvc.post("/api/notifications") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.recipientId") { value("ctrl-user-1") }
            jsonPath("$.notificationType") { value("EMAIL") }
        }
    }

    @Test
    @DisplayName("POST 필수 필드 누락 → 400 Bad Request")
    fun send_missingFields_returns400() {
        // title 필드 누락
        val body = mapOf(
            "recipientId" to "user-1",
            "notificationType" to "EMAIL",
            "content" to "내용",
            "referenceEventId" to "event-1"
        )

        mockMvc.post("/api/notifications") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("GET /api/notifications/{id} → 200 OK")
    fun findById_returns200() {
        // 먼저 알림 생성
        val request = SendNotificationRequest(
            recipientId = "ctrl-user-2",
            notificationType = NotificationType.IN_APP,
            title = "조회 테스트",
            content = "내용",
            referenceEventId = "ctrl-event-2"
        )
        val createResult = mockMvc.post("/api/notifications") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andReturn()

        val id = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

        mockMvc.get("/api/notifications/$id")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
                jsonPath("$.recipientId") { value("ctrl-user-2") }
            }
    }

    @Test
    @DisplayName("GET /api/notifications/{id} 존재하지 않는 id → 404")
    fun findById_notFound_returns404() {
        mockMvc.get("/api/notifications/999999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    @DisplayName("GET /api/notifications?recipientId=xxx → 200 OK (목록)")
    fun findByRecipientId_returns200() {
        // 알림 생성
        val request = SendNotificationRequest(
            recipientId = "ctrl-user-list",
            notificationType = NotificationType.EMAIL,
            title = "목록 테스트",
            content = "내용",
            referenceEventId = "ctrl-event-list"
        )
        mockMvc.post("/api/notifications") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }

        mockMvc.get("/api/notifications") {
            param("recipientId", "ctrl-user-list")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content", hasSize<Any>(1))
        }
    }
}
