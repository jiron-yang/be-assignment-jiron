# 비동기 처리 아키텍처

## 1. 전체 구조

Hexagonal Architecture 기반으로 API 요청과 알림 발송을 비동기로 분리하여 장애 격리와 안정성을 확보한다.

```
┌──────────┐     ┌────────────────────┐     ┌─────────────┐     ┌──────────────────────┐
│  Client  │────>│  NotificationController │────>│  DB (H2/RDB)│<────│  NotificationPolling │
│          │     │  (SendNotificationUseCase)│    │  PENDING 저장 │     │  Scheduler (5초)     │
└──────────┘     └────────────────────┘     └─────────────┘     └──────────+───────────┘
                                                                           │
                                                              ProcessPendingNotificationsUseCase
                                                                           │
                                                                           v
                                                                  ┌────────────────┐
                                                                  │ Notification   │
                                                                  │ Processor      │
                                                                  │ (EMAIL/IN_APP) │
                                                                  └────────────────┘
```

### 처리 흐름

1. 클라이언트가 알림 발송 API를 호출한다.
2. `NotificationController` → `SendNotificationUseCase` → `NotificationRepository.save()`로 `PENDING` 상태 알림을 저장하고 id를 반환한다.
3. `NotificationPollingScheduler`가 5초마다 `ProcessPendingNotificationsUseCase.execute()`를 호출한다.
4. `NotificationProcessor`가 `NotificationQueue.dequeueForProcessing()`으로 대기 중인 알림을 조회한다.
5. 개별 알림마다 `TransactionTemplate`으로 별도 트랜잭션을 열어 발송을 처리한다.
6. 발송 결과에 따라 `markSent()` 또는 `handleSendFailure(now)` 도메인 메서드를 호출한다.

## 2. 핵심 추상화: NotificationQueue

발송 대기열을 인터페이스로 추상화하여, 구현체 교체만으로 메시징 인프라를 변경할 수 있다.

```kotlin
interface NotificationQueue {
    /** 발송 대기 중인 알림 조회 (PENDING + nextRetryAt <= now) */
    fun dequeueForProcessing(batchSize: Int): List<Notification>
}
```

큐는 dequeue 전용이다. 알림 생성(enqueue)은 `NotificationRepository.save()`가 직접 담당한다. 이렇게 분리한 이유는 Command 경로에서 큐 의존을 제거하여 서비스 로직을 단순화하기 위함이다.

### DbNotificationQueue (현재 구현체)

DB를 메시지 큐로 활용하는 Outbox 패턴 구현체이다.

```kotlin
@Component
class DbNotificationQueue(
    private val notificationJpaRepository: NotificationJpaRepository
) : NotificationQueue {

    @Transactional
    override fun dequeueForProcessing(batchSize: Int): List<Notification> {
        val entities = notificationJpaRepository
            .findAllByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                NotificationStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, batchSize)
            )

        return entities.map { entity ->
            val domain = NotificationMapper.toDomain(entity)
            domain.startProcessing()
            val updatedEntity = NotificationMapper.toEntity(domain)
            val saved = notificationJpaRepository.save(updatedEntity)
            NotificationMapper.toDomain(saved)
        }
    }
}
```

### 폴링 쿼리

```sql
SELECT * FROM notifications
WHERE status = 'PENDING'
  AND next_retry_at <= NOW()
ORDER BY next_retry_at ASC
LIMIT :batchSize
```

## 3. 발송 처리: NotificationProcessor

`ProcessPendingNotificationsUseCase`를 구현하며, 채널별 발송 로직을 처리한다.

```kotlin
@Service
class NotificationProcessor(
    private val notificationQueue: NotificationQueue,
    private val channels: List<NotificationChannel>,
    private val notificationRepository: NotificationRepository,
    private val transactionTemplate: TransactionTemplate
) : ProcessPendingNotificationsUseCase {

    override fun execute() {
        val notifications = notificationQueue.dequeueForProcessing(batchSize = 10)
        notifications.forEach { notification ->
            transactionTemplate.executeWithoutResult {
                sendOne(notification)
            }
        }
    }
}
```

### 배치 트랜잭션 분리

`TransactionTemplate`을 사용하여 개별 알림마다 독립된 트랜잭션을 연다. 하나의 알림 발송이 실패해도 나머지 알림 처리에 영향을 주지 않는다.

### 채널 추상화

```kotlin
interface NotificationChannel {
    fun send(notification: Notification)
    fun supports(type: NotificationType): Boolean
}
```

`NotificationType`에 맞는 `NotificationChannel`을 선택하여 발송한다. 현재 `EmailNotificationChannel`이 구현되어 있다.

## 4. UseCase 포트를 통한 호출 구조

스케줄러는 UseCase 포트를 통해 서비스를 호출한다. 스케줄러가 서비스 구현체에 직접 의존하지 않는다.

```kotlin
@Component
class NotificationPollingScheduler(
    private val processPendingNotificationsUseCase: ProcessPendingNotificationsUseCase
) {
    @Scheduled(fixedDelay = 5000)
    fun poll() {
        processPendingNotificationsUseCase.execute()
    }
}

@Component
class StuckNotificationRecoveryScheduler(
    private val recoverStuckNotificationsUseCase: RecoverStuckNotificationsUseCase
) {
    @Scheduled(fixedDelay = 60000)
    fun recover() {
        recoverStuckNotificationsUseCase.execute()
    }
}
```

## 5. Kafka 교체 전략

현재 DB 폴링 방식에서 Kafka로 전환할 때의 교체 방법이다.

```
현재:  API → Repository.save() → DB → Scheduler → NotificationQueue.dequeue() → NotificationProcessor
교체:  API → Repository.save() + KafkaTemplate.send() → Kafka Consumer → NotificationProcessor
```

`NotificationQueue`가 dequeue 전용이므로, Kafka 전환 시 다음과 같이 변경한다:

1. 알림 저장 시 Kafka에도 메시지를 발행하는 이벤트 발행 구조 추가
2. Kafka Consumer가 `ProcessPendingNotificationsUseCase`를 대체
3. `NotificationPollingScheduler` 제거
4. `DbNotificationQueue`는 fallback 또는 제거

### 교체 시 변경 범위

| 컴포넌트 | 변경 여부 | 설명 |
|----------|-----------|------|
| NotificationQueue | 구현체 교체 또는 제거 | Kafka Consumer가 대체 |
| NotificationChannel | 변경 없음 | 발송 로직은 대기열과 무관 |
| NotificationRepository | 변경 없음 | Command 저장 로직 동일 |
| REST API / Controller | 변경 없음 | UseCase 호출만 함 |
| NotificationPollingScheduler | 제거 | Kafka Consumer가 대체 |

## 6. 장애 격리

API와 발송 처리가 분리되어 있어 다음과 같은 장애 격리가 가능하다.

- **발송 채널 장애**: API는 정상 응답, 알림은 `PENDING` 상태로 DB에 안전하게 보관
- **API 서버 장애**: 이미 DB에 저장된 `PENDING` 알림은 스케줄러가 계속 처리
- **스케줄러 장애**: API는 정상 동작, 스케줄러 복구 후 밀린 알림 자동 처리
- **DB 장애**: 전체 시스템 영향 (단일 장애점) → Kafka 전환 시 완화 가능
