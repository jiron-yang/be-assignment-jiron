# 알림 발송 시스템

DB 폴링(Outbox 패턴) 기반의 비동기 알림 발송 시스템이다. Hexagonal Architecture와 DDD를 적용하여 도메인 순수성을 유지하면서, API 요청 시 알림을 DB에 저장하고 스케줄러가 주기적으로 폴링하여 발송을 처리한다.

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
  "id": 1
}
```

중복 요청 시에도 기존 알림의 id를 반환한다 (멱등성 보장).

### 알림 단건 조회

```bash
curl http://localhost:8080/api/notifications/1
```

**응답** (200 OK):
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

### 수신자별 알림 목록 조회

```bash
curl "http://localhost:8080/api/notifications?recipientId=user-123&page=0&size=10"
```

## 설계

### Hexagonal Architecture + CQRS-lite

Port & Adapter 패턴으로 도메인 로직을 인프라에서 격리하고, Command/Query를 분리한다.

```
Command 경로: Controller → SendNotificationUseCase → Notification(도메인) → NotificationRepository
Query 경로:   Controller → GetNotificationUseCase → NotificationQueryPort → NotificationView
```

### 비동기 처리 구조

```
Client → REST API → DB 저장 (PENDING) → 스케줄러 폴링 (5초) → 발송 처리
```

API는 알림을 `PENDING` 상태로 DB에 저장한 뒤 id만 반환한다. 스케줄러가 주기적으로 대기 중인 알림을 조회하여 채널별(`EMAIL`, `IN_APP`) 발송을 수행한다.

### 상태 머신

```
PENDING → PROCESSING → SENT      (발송 성공)
                     → PENDING   (발송 실패, 재시도 가능 → retryCount++ & nextRetryAt 갱신)
                     → FAILED    (발송 실패, 최대 재시도 초과)
```

### 재시도 정책

지수 백오프(Exponential Backoff)를 적용한다. `RetryPolicy.MAX_RETRY_COUNT`가 단일 소스이다.

| 재시도 | 대기 시간 |
|--------|-----------|
| 1회차  | 1분       |
| 2회차  | 5분       |
| 3회차  | 30분      |
| 초과   | FAILED 확정 |

`nextRetryAt` 필드를 기준으로 폴링하여, 대기 시간이 경과하기 전에는 조회되지 않는다.

### 멱등성 보장

`(recipientId, notificationType, referenceEventId)` 복합 유니크 제약으로 동일 이벤트에 대한 알림 중복 생성을 방지한다. 중복 요청 시 기존 알림의 id를 반환한다.

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

### 2. DDD 적용 - 도메인 순수성

`Notification`은 JPA 어노테이션이 없는 순수 도메인 모델이다. JPA Entity(`NotificationEntity`)는 adapter 계층에 별도로 존재하며, `NotificationMapper`가 도메인 ↔ Entity 변환을 담당한다.

### 3. 불변식 보호

- `var` 프로퍼티에 `private set`을 적용하여 외부에서 직접 상태 변경을 차단한다.
- `init` 블록에서 비즈니스 정책을 검증한다 (title/content blank, retryCount 음수 등).
- `NotificationStatus`에 `canProcess`, `canComplete`, `canFail`, `canRetry` boolean 프로퍼티를 선언하여 상태 전이 규칙을 enum 자체에 캡슐화한다.
- `handleSendFailure(now)` 메서드로 재시도 판단과 상태 전이를 Aggregate 내부에 캡슐화한다.

### 4. Value Object 도입

`RecipientId`, `ReferenceEventId`를 inline value class로 정의하여 원시 타입 오용을 방지한다. `NotificationIdempotencyKey`는 멱등성 키 조합을 명시적으로 표현한다.

### 5. CQRS-lite

Command(발송 요청)와 Query(조회)를 포트 수준에서 분리한다. `NotificationRepository`는 Command 측만, `NotificationQueryPort`는 Query 측만 담당하며, 조회 시 도메인 모델 대신 `NotificationView`(Read Model)를 반환한다.

## 프로젝트 구조

```
src/main/kotlin/com/jiron/notification/
├── domain/
│   ├── model/
│   │   └── Notification.kt                    # 알림 도메인 모델 (Aggregate Root)
│   └── vo/
│       ├── NotificationStatus.kt              # 알림 상태 (상태 전이 규칙 포함)
│       ├── NotificationType.kt                # 알림 유형 (EMAIL, IN_APP)
│       ├── RetryPolicy.kt                     # 재시도 정책 (지수 백오프)
│       ├── RecipientId.kt                     # 수신자 식별자 VO
│       ├── ReferenceEventId.kt                # 참조 이벤트 식별자 VO
│       └── NotificationIdempotencyKey.kt      # 멱등성 키 VO
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── SendNotificationUseCase.kt     # 발송 요청 유즈케이스
│   │   │   ├── GetNotificationUseCase.kt      # 조회 유즈케이스
│   │   │   ├── NotificationView.kt            # 조회 전용 Read Model
│   │   │   ├── ProcessPendingNotificationsUseCase.kt  # 발송 처리 유즈케이스
│   │   │   └── RecoverStuckNotificationsUseCase.kt    # stuck 복구 유즈케이스
│   │   └── out/
│   │       ├── NotificationRepository.kt      # 저장소 포트 (Command)
│   │       ├── NotificationQueryPort.kt       # 조회 포트 (Query)
│   │       ├── NotificationQueue.kt           # 큐 추상화 포트
│   │       └── NotificationChannel.kt         # 발송 채널 추상화 포트
│   └── service/
│       ├── NotificationService.kt             # 발송 요청 + 조회 서비스
│       ├── NotificationProcessor.kt           # 발송 처리 서비스
│       └── StuckNotificationRecoveryService.kt # stuck 복구 서비스
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   ├── NotificationController.kt      # REST API 컨트롤러
│   │   │   └── dto/
│   │   │       ├── SendNotificationRequest.kt # 발송 요청 DTO
│   │   │       └── NotificationResponse.kt    # 응답 DTO
│   │   └── scheduler/
│   │       ├── NotificationPollingScheduler.kt       # 발송 폴링 스케줄러 (5초)
│   │       └── StuckNotificationRecoveryScheduler.kt # stuck 복구 스케줄러 (1분)
│   └── out/
│       ├── persistence/
│       │   ├── NotificationEntity.kt          # JPA Entity
│       │   ├── NotificationMapper.kt          # 도메인 ↔ Entity 매퍼
│       │   ├── JpaNotificationRepository.kt   # Repository + QueryPort 구현체
│       │   └── DbNotificationQueue.kt         # DB 기반 큐 구현체
│       └── channel/
│           └── EmailNotificationChannel.kt    # 이메일 발송 구현체
└── NotificationApplication.kt                 # Spring Boot 메인
```
