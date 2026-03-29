# 비동기 처리 아키텍처

## 1. 전체 구조

API 요청과 알림 발송을 비동기로 분리하여 장애 격리와 안정성을 확보한다.

```
┌──────────┐     ┌───────────┐     ┌─────────────┐     ┌──────────────┐
│  Client  │────>│  REST API │────>│  DB (H2/RDB)│<────│  Scheduler   │
│          │     │           │     │  PENDING 저장 │     │  (폴링)       │
└──────────┘     └───────────┘     └─────────────┘     └──────+───────┘
                                                              │
                                                              v
                                                     ┌───────────────┐
                                                     │ Notification  │
                                                     │ Sender        │
                                                     │ (EMAIL/IN_APP)│
                                                     └───────────────┘
```

### 처리 흐름

1. 클라이언트가 알림 발송 API를 호출한다.
2. API는 `notifications` 테이블에 `PENDING` 상태로 레코드를 저장하고 즉시 응답한다.
3. 스케줄러가 주기적으로(예: 5초) `PENDING` 상태인 알림을 폴링한다.
4. 폴링된 알림의 상태를 `PROCESSING`으로 변경한 뒤 발송을 시도한다.
5. 발송 결과에 따라 `SENT` 또는 재시도/`FAILED` 처리한다.

## 2. 핵심 추상화: NotificationQueue

발송 대기열을 인터페이스로 추상화하여, 구현체 교체만으로 메시징 인프라를 변경할 수 있다.

```kotlin
interface NotificationQueue {
    /**
     * 알림을 대기열에 추가한다.
     */
    fun enqueue(notification: Notification)

    /**
     * 발송 대기 중인 알림을 최대 batchSize만큼 가져온다.
     * 가져온 알림은 PROCESSING 상태로 전환된다.
     */
    fun dequeue(batchSize: Int): List<Notification>
}
```

### DbNotificationQueue (현재 구현체)

DB를 메시지 큐로 활용하는 Outbox 패턴 구현체이다.

```kotlin
@Component
class DbNotificationQueue(
    private val notificationRepository: NotificationRepository
) : NotificationQueue {

    @Transactional
    override fun enqueue(notification: Notification) {
        notificationRepository.save(notification)
    }

    @Transactional
    override fun dequeue(batchSize: Int): List<Notification> {
        val pending = notificationRepository.findPendingNotifications(
            status = NotificationStatus.PENDING,
            now = LocalDateTime.now(),
            limit = batchSize
        )
        pending.forEach { it.status = NotificationStatus.PROCESSING }
        return pending
    }
}
```

### 폴링 쿼리

```sql
SELECT * FROM notifications
WHERE status = 'PENDING'
  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
ORDER BY created_at ASC
LIMIT :batchSize
```

## 3. 발송 처리: NotificationSender

채널별 발송 로직을 추상화한다. 대기열 구현체와 독립적으로 동작한다.

```kotlin
interface NotificationSender {
    fun support(type: NotificationType): Boolean
    fun send(notification: Notification): SendResult
}

data class SendResult(
    val success: Boolean,
    val errorMessage: String? = null
)
```

채널별 구현체:
- `EmailNotificationSender`: 이메일 발송
- `InAppNotificationSender`: 인앱 알림 발송

스케줄러는 `NotificationType`에 맞는 `NotificationSender`를 선택하여 발송한다.

```kotlin
@Component
class NotificationDispatcher(
    private val senders: List<NotificationSender>
) {
    fun dispatch(notification: Notification): SendResult {
        val sender = senders.first { it.support(notification.notificationType) }
        return sender.send(notification)
    }
}
```

## 4. Kafka 교체 전략

현재 DB 폴링 방식에서 Kafka로 전환할 때, `NotificationQueue` 인터페이스만 교체하면 된다.

```
현재:  API → DbNotificationQueue → DB → Scheduler(폴링) → NotificationSender
교체:  API → KafkaNotificationQueue → Kafka → Consumer → NotificationSender
```

### KafkaNotificationQueue (교체 시 구현체)

```kotlin
@Component
@Profile("kafka")
class KafkaNotificationQueue(
    private val kafkaTemplate: KafkaTemplate<String, Notification>
) : NotificationQueue {

    override fun enqueue(notification: Notification) {
        kafkaTemplate.send("notification-topic", notification.recipientId.toString(), notification)
    }

    override fun dequeue(batchSize: Int): List<Notification> {
        // Kafka Consumer에서 직접 처리하므로 사용하지 않음
        throw UnsupportedOperationException("Kafka consumer handles dequeue")
    }
}
```

### 교체 시 변경 범위

| 컴포넌트 | 변경 여부 | 설명 |
|----------|-----------|------|
| NotificationQueue | 구현체 교체 | `DbNotificationQueue` → `KafkaNotificationQueue` |
| NotificationSender | 변경 없음 | 발송 로직은 대기열과 무관 |
| NotificationDispatcher | 변경 없음 | Sender 선택 로직 동일 |
| REST API | 변경 없음 | `NotificationQueue.enqueue()` 호출만 함 |
| Scheduler | 제거 | Kafka Consumer가 대체 |

## 5. 장애 격리

API와 발송 처리가 분리되어 있어 다음과 같은 장애 격리가 가능하다.

- **발송 채널 장애**: API는 정상 응답, 알림은 `PENDING` 상태로 DB에 안전하게 보관
- **API 서버 장애**: 이미 DB에 저장된 `PENDING` 알림은 스케줄러가 계속 처리
- **스케줄러 장애**: API는 정상 동작, 스케줄러 복구 후 밀린 알림 자동 처리
- **DB 장애**: 전체 시스템 영향 (단일 장애점) → Kafka 전환 시 완화 가능
