# 도메인 모델 설계

## 1. Notification 도메인 모델 (Aggregate Root)

알림 발송의 핵심 도메인 모델로, JPA 어노테이션 없이 순수 도메인 로직만 포함한다. 모든 상태 변경은 도메인 메서드를 통해서만 가능하다.

```kotlin
class Notification(
    val id: Long = 0L,
    val recipientId: RecipientId,
    val notificationType: NotificationType,
    val title: String,
    val content: String,
    status: NotificationStatus = NotificationStatus.PENDING,
    retryCount: Int = 0,
    val maxRetryCount: Int = RetryPolicy.MAX_RETRY_COUNT,
    nextRetryAt: LocalDateTime = LocalDateTime.now(),
    val referenceEventId: ReferenceEventId,
    sentAt: LocalDateTime? = null
) {

    init {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(content.isNotBlank()) { "Content must not be blank" }
        require(retryCount >= 0) { "Retry count must not be negative: $retryCount" }
        require(maxRetryCount > 0) { "Max retry count must be positive: $maxRetryCount" }
        require(retryCount <= maxRetryCount) { "Retry count($retryCount) must not exceed max($maxRetryCount)" }
        require(sentAt == null || status == NotificationStatus.SENT) {
            "sentAt must be null unless status is SENT"
        }
    }

    var status: NotificationStatus = status
        private set

    var retryCount: Int = retryCount
        private set

    var nextRetryAt: LocalDateTime = nextRetryAt
        private set

    var sentAt: LocalDateTime? = sentAt
        private set

    fun startProcessing() { /* PENDING → PROCESSING */ }
    fun markSent() { /* PROCESSING → SENT */ }
    fun markFailed() { /* PROCESSING → FAILED */ }
    fun resetToPending() { /* PROCESSING → PENDING (stuck 복구용) */ }
    fun handleSendFailure(now: LocalDateTime) { /* 재시도 판단 + 상태 전이 */ }
    fun canRetry(): Boolean = retryCount < maxRetryCount
}
```

### 불변식 보호

- `var` 프로퍼티에 `private set`을 적용하여 외부에서 직접 상태 변경을 차단한다.
- `init` 블록에서 생성 시점에 비즈니스 정책을 검증한다.
- 각 상태 전이 메서드에서 `require`로 허용된 전이만 가능하게 한다.

### Value Object

| VO | 설명 |
|----|------|
| `RecipientId` | 수신자 식별자. inline value class로 원시 타입 오용 방지. blank 검증 포함. |
| `ReferenceEventId` | 참조 이벤트 식별자. inline value class로 원시 타입 오용 방지. blank 검증 포함. |
| `NotificationIdempotencyKey` | `(recipientId, notificationType, referenceEventId)` 조합의 멱등성 키. |

### 도메인 ↔ Entity 분리

`Notification`(도메인)과 `NotificationEntity`(JPA)는 별도 클래스이다. `NotificationMapper`가 변환을 담당한다.

- `channel` 필드는 도메인에 존재하지 않으며, Entity에서 `notificationType.name`으로 자동 파생한다.
- `createdAt`, `updatedAt`은 도메인에 존재하지 않으며, Entity의 `@PrePersist`/`@PreUpdate`로 관리한다.
- 조회 시에는 도메인 모델을 거치지 않고 Entity에서 `NotificationView`(Read Model)로 직접 변환한다.

### Enum 정의

```kotlin
enum class NotificationType(val description: String) {
    EMAIL("이메일"),
    IN_APP("인앱 알림")
}

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
```

`NotificationStatus`의 boolean 프로퍼티로 각 상태에서 허용되는 전이를 선언한다. 도메인 메서드에서 `require(status.canProcess)` 형태로 사용하여 잘못된 전이를 차단한다.

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
         markSent()    handleSendFailure()  handleSendFailure()
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

| 현재 상태 | 이벤트 | 다음 상태 | 도메인 메서드 |
|-----------|--------|-----------|--------------|
| PENDING | 스케줄러 폴링 | PROCESSING | `startProcessing()` |
| PROCESSING | 발송 성공 | SENT | `markSent()` |
| PROCESSING | 발송 실패 (재시도 가능) | PENDING | `handleSendFailure(now)` |
| PROCESSING | 발송 실패 (재시도 초과) | FAILED | `handleSendFailure(now)` |
| PROCESSING | stuck 복구 | PENDING | `resetToPending()` |

## 3. ERD

`schema.sql` 기준 DB 스키마이다.

```
+--------------------------------------------------+
|                  notifications                    |
+--------------------------------------------------+
| id                  BIGINT       PK, AUTO_INCREMENT |
| recipient_id        VARCHAR(255) NOT NULL          |
| notification_type   VARCHAR(50)  NOT NULL          |
| channel             VARCHAR(50)  NOT NULL          |
| title               VARCHAR(500) NOT NULL          |
| content             VARCHAR(2000) NOT NULL         |
| status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING' |
| retry_count         INT          NOT NULL DEFAULT 0|
| max_retry_count     INT          NOT NULL DEFAULT 3|
| next_retry_at       TIMESTAMP    NOT NULL          |
| reference_event_id  VARCHAR(255) NOT NULL          |
| created_at          TIMESTAMP    NOT NULL          |
| updated_at          TIMESTAMP    NOT NULL          |
| sent_at             TIMESTAMP    NULL              |
+--------------------------------------------------+
| UNIQUE (recipient_id, notification_type, reference_event_id) |
| INDEX  (status, next_retry_at)                               |
| INDEX  (recipient_id)                                        |
+--------------------------------------------------+
```

### 인덱스 설계

- **복합 유니크 인덱스** `uk_notification_idempotency`: `(recipient_id, notification_type, reference_event_id)`
  - 동일 수신자에게 동일 이벤트에 대한 같은 유형의 알림이 중복 생성되는 것을 방지
  - 멱등성 보장의 핵심 메커니즘
- **폴링용 인덱스** `idx_notifications_polling`: `(status, next_retry_at)`
  - 스케줄러가 발송 대상을 조회할 때 사용
  - `WHERE status = 'PENDING' AND next_retry_at <= NOW()` 쿼리 최적화
- **수신자 조회 인덱스** `idx_notifications_recipient`: `(recipient_id)`
  - 수신자별 알림 목록 조회 최적화

## 4. 멱등성 보장

`(recipientId, notificationType, referenceEventId)` 복합 유니크 제약으로 멱등성을 보장한다.

- `referenceEventId`: 알림을 유발한 원본 이벤트의 고유 식별자 (예: 주문번호, 결제ID)
- 동일한 이벤트에 대해 API를 여러 번 호출해도 알림은 하나만 생성됨
- 중복 요청 시 기존 알림의 id를 반환한다 (에러가 아닌 정상 응답)

```kotlin
@Transactional
override fun execute(command: SendNotificationCommand): Long {
    val existing = notificationRepository.findByIdempotencyKey(command.idempotencyKey)
    if (existing != null) {
        return existing.id
    }

    val notification = Notification(/* ... */)

    return try {
        notificationRepository.save(notification).id
    } catch (e: DataIntegrityViolationException) {
        // 동시 요청으로 인한 유니크 제약 위반 시 기존 알림 반환
        notificationRepository.findByIdempotencyKey(command.idempotencyKey)?.id ?: throw e
    }
}
```

`NotificationIdempotencyKey` VO로 멱등성 키 조합을 명시적으로 표현하여, `SendNotificationCommand`에서 자동 생성된다.
