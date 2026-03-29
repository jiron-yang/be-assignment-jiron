# 알림 발송 시스템

DB 폴링(Outbox 패턴) 기반의 비동기 알림 발송 시스템이다. API 요청 시 알림을 DB에 저장하고, 스케줄러가 주기적으로 폴링하여 발송을 처리한다.

**기술 스택**: Kotlin, Spring Boot 3.4, JPA, H2 (in-memory)

## 실행 방법

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

- 서버: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:notification`
  - Username: `sa` / Password: (없음)

## API 명세

### 알림 발송 요청

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "recipientId": "user-123",
    "notificationType": "EMAIL",
    "title": "주문 완료 안내",
    "content": "주문이 성공적으로 완료되었습니다.",
    "referenceEventId": "order-456"
  }'
```

**응답** (201 Created):
```json
{
  "id": 1,
  "recipientId": "user-123",
  "notificationType": "EMAIL",
  "status": "PENDING",
  "title": "주문 완료 안내",
  "content": "주문이 성공적으로 완료되었습니다.",
  "retryCount": 0,
  "referenceEventId": "order-456",
  "createdAt": "2026-03-29T10:00:00",
  "sentAt": null
}
```

### 알림 단건 조회

```bash
curl http://localhost:8080/api/notifications/1
```

### 수신자별 알림 목록 조회

```bash
curl "http://localhost:8080/api/notifications?recipientId=user-123&page=0&size=10"
```

## 설계

### 비동기 처리 구조

```
Client → REST API → DB 저장 (PENDING) → 스케줄러 폴링 (5초) → 발송 처리
```

API는 알림을 `PENDING` 상태로 DB에 저장한 뒤 즉시 응답한다. 스케줄러가 주기적으로 대기 중인 알림을 조회하여 채널별(`EMAIL`, `IN_APP`) 발송을 수행한다.

### 상태 머신

```
PENDING → PROCESSING → SENT      (발송 성공)
                     → PENDING   (발송 실패, 재시도 가능 → retryCount++ & nextRetryAt 갱신)
                     → FAILED    (발송 실패, 최대 재시도 초과)
```

### 재시도 정책

지수 백오프(Exponential Backoff)를 적용한다.

| 재시도 | 대기 시간 |
|--------|-----------|
| 1회차  | 1분       |
| 2회차  | 5분       |
| 3회차  | 30분      |
| 초과   | FAILED 확정 |

`nextRetryAt` 필드를 기준으로 폴링하여, 대기 시간이 경과하기 전에는 조회되지 않는다.

### 멱등성 보장

`(recipientId, notificationType, referenceEventId)` 복합 유니크 제약으로 동일 이벤트에 대한 알림 중복 생성을 방지한다. 중복 요청 시 409 Conflict를 반환한다.

### PROCESSING stuck 복구

서버 장애 등으로 `PROCESSING` 상태가 5분 이상 지속된 알림을 1분 주기로 감지하여 `PENDING`으로 복귀시킨다.

### 큐 추상화

`NotificationQueue` 인터페이스로 대기열을 추상화했다. 현재는 DB 폴링(`DbNotificationQueue`)으로 구현되어 있으며, Kafka 등 메시지 브로커로 교체 시 구현체만 변경하면 된다.

## 고민 포인트

### 1. DB 폴링 vs 메시지 큐

Kafka 같은 메시지 큐 대신 DB 폴링을 선택한 이유:
- **단순성**: 별도 인프라 없이 RDB 하나로 동작한다.
- **유실 없음**: 트랜잭션으로 저장되므로 알림이 유실되지 않는다.
- **교체 용이**: `NotificationQueue` 인터페이스 뒤에 숨겨 놓았으므로, 트래픽 증가 시 Kafka로 교체할 수 있다.

### 2. 상태 전이를 엔티티에 캡슐화

`startProcessing()`, `markSent()`, `scheduleRetry()` 등 상태 전이 메서드를 `Notification` 엔티티 내부에 두었다. 잘못된 상태 전이(예: PENDING에서 바로 SENT)를 `require`로 차단하여 불변식을 보장한다.

### 3. Service -> Provider -> Repository 간접 의존

Service가 Repository를 직접 의존하지 않고 Provider를 거치도록 했다. Provider가 영속성 로직을 캡슐화하므로, 테스트 시 Provider만 모킹하면 되고, 저장소 교체 시 영향 범위가 줄어든다.

## 프로젝트 구조

```
src/main/kotlin/com/jiron/notification/
├── api/
│   ├── NotificationController.kt        # REST API 컨트롤러
│   ├── GlobalExceptionHandler.kt        # 전역 예외 처리
│   └── dto/
│       ├── SendNotificationRequest.kt   # 발송 요청 DTO
│       └── NotificationResponse.kt      # 응답 DTO
├── application/
│   ├── NotificationService.kt           # 비즈니스 로직
│   ├── NotificationProvider.kt          # 영속성 추상화
│   ├── NotificationQueue.kt             # 큐 인터페이스
│   └── NotificationSender.kt            # 발송 처리
├── domain/
│   ├── Notification.kt                  # 알림 엔티티 (상태 전이 캡슐화)
│   ├── NotificationType.kt              # 알림 유형 (EMAIL, IN_APP)
│   ├── NotificationStatus.kt            # 알림 상태 (PENDING, PROCESSING, SENT, FAILED)
│   ├── NotificationChannel.kt           # 발송 채널 인터페이스
│   └── RetryPolicy.kt                   # 재시도 정책 (지수 백오프)
├── infrastructure/
│   ├── channel/
│   │   ├── EmailNotificationChannel.kt  # 이메일 발송 구현
│   │   └── InAppNotificationChannel.kt  # 인앱 알림 발송 구현
│   └── persistence/
│       ├── DbNotificationQueue.kt       # DB 폴링 기반 큐 구현
│       └── NotificationJpaRepository.kt # JPA Repository
├── scheduler/
│   ├── NotificationPollingScheduler.kt  # 발송 폴링 스케줄러 (5초)
│   └── StuckNotificationRecoveryScheduler.kt  # stuck 복구 스케줄러 (1분)
└── NotificationApplication.kt           # Spring Boot 메인
```
