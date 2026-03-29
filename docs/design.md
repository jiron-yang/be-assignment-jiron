# 도메인 모델 설계

## 1. Notification 엔티티

알림 발송의 핵심 엔티티로, Outbox 패턴의 메시지 역할을 수행한다.

```kotlin
@Entity
@Table(
    name = "notifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_idempotency",
            columnNames = ["recipient_id", "notification_type", "reference_event_id"]
        )
    ]
)
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val recipientId: Long,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val notificationType: NotificationType,

    @Column(nullable = false)
    val channel: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: NotificationStatus = NotificationStatus.PENDING,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Column(nullable = false)
    val maxRetryCount: Int = 3,

    var nextRetryAt: LocalDateTime? = null,

    @Column(nullable = false)
    val referenceEventId: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now(),

    var sentAt: LocalDateTime? = null
)
```

### Enum 정의

```kotlin
enum class NotificationType(val description: String) {
    EMAIL("이메일"),
    IN_APP("인앱 알림")
}

enum class NotificationStatus(val description: String) {
    PENDING("발송 대기"),
    PROCESSING("발송 중"),
    SENT("발송 완료"),
    FAILED("발송 실패")
}
```

## 2. 상태 전이 다이어그램

```
                         +-----------+
                         |  PENDING  |
                         +-----+-----+
                               |
                   스케줄러 폴링 (nextRetryAt <= now)
                               |
                               v
                       +-------+-------+
                       |  PROCESSING   |
                       +---+---+---+---+
                           |   |   |
              +------------+   |   +-------------+
              |                |                  |
         발송 성공        발송 실패            발송 실패
              |        retryCount < max    retryCount >= max
              |                |                  |
              v                v                  v
         +----+----+    +-----+-----+      +-----+-----+
         |  SENT   |    |  PENDING  |      |  FAILED   |
         +---------+    +-----------+      +-----------+
                      (retryCount++,
                       nextRetryAt 갱신)
```

### 전이 규칙 요약

| 현재 상태 | 이벤트 | 다음 상태 | 조건 |
|-----------|--------|-----------|------|
| PENDING | 스케줄러 폴링 | PROCESSING | `nextRetryAt <= now` 또는 `nextRetryAt IS NULL` |
| PROCESSING | 발송 성공 | SENT | - |
| PROCESSING | 발송 실패 | PENDING | `retryCount < maxRetryCount` |
| PROCESSING | 발송 실패 | FAILED | `retryCount >= maxRetryCount` |

## 3. ERD

```
+--------------------------------------------------+
|                  notifications                    |
+--------------------------------------------------+
| id                  BIGINT       PK, AUTO_INCREMENT |
| recipient_id        BIGINT       NOT NULL          |
| notification_type   VARCHAR(20)  NOT NULL          |
| channel             VARCHAR(50)  NOT NULL          |
| title               VARCHAR(255) NOT NULL          |
| content             TEXT         NOT NULL          |
| status              VARCHAR(20)  NOT NULL          |
| retry_count         INT          NOT NULL DEFAULT 0|
| max_retry_count     INT          NOT NULL DEFAULT 3|
| next_retry_at       TIMESTAMP    NULL              |
| reference_event_id  VARCHAR(255) NOT NULL          |
| created_at          TIMESTAMP    NOT NULL          |
| updated_at          TIMESTAMP    NOT NULL          |
| sent_at             TIMESTAMP    NULL              |
+--------------------------------------------------+
| UNIQUE (recipient_id, notification_type, reference_event_id) |
| INDEX  (status, next_retry_at)                               |
+--------------------------------------------------+
```

### 인덱스 설계

- **복합 유니크 인덱스** `uk_notification_idempotency`: `(recipient_id, notification_type, reference_event_id)`
  - 동일 수신자에게 동일 이벤트에 대한 같은 유형의 알림이 중복 생성되는 것을 방지
  - 멱등성 보장의 핵심 메커니즘
- **폴링용 인덱스** `idx_notification_polling`: `(status, next_retry_at)`
  - 스케줄러가 발송 대상을 조회할 때 사용
  - `WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= now)` 쿼리 최적화

## 4. 멱등성 보장

`(recipientId, notificationType, referenceEventId)` 복합 유니크 제약으로 멱등성을 보장한다.

- `referenceEventId`: 알림을 유발한 원본 이벤트의 고유 식별자 (예: 주문번호, 결제ID)
- 동일한 이벤트에 대해 API를 여러 번 호출해도 알림은 하나만 생성됨
- 중복 요청 시 DB 유니크 제약 위반 → `DataIntegrityViolationException` → 409 Conflict 응답
