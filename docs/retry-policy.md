# 재시도 정책

## 1. 지수 백오프 (Exponential Backoff)

발송 실패 시 재시도 간격을 점진적으로 늘려, 일시적 장애 상황에서 시스템 부하를 줄인다.

| 재시도 횟수 | 대기 시간 | nextRetryAt 예시 (실패 시각 10:00 기준) |
|------------|-----------|----------------------------------------|
| 1회차 | 1분 | 10:01 |
| 2회차 | 5분 | 10:06 |
| 3회차 | 30분 | 10:36 |
| 최대 초과 | - | FAILED 확정 |

### 백오프 계산 로직

```kotlin
object RetryPolicy {
    private val RETRY_INTERVALS = listOf(
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(30)
    )

    fun calculateNextRetryAt(retryCount: Int): LocalDateTime? {
        if (retryCount >= RETRY_INTERVALS.size) return null
        return LocalDateTime.now().plus(RETRY_INTERVALS[retryCount])
    }

    fun isRetryable(notification: Notification): Boolean {
        return notification.retryCount < notification.maxRetryCount
    }
}
```

## 2. nextRetryAt 기반 폴링 조건

스케줄러는 다음 조건을 만족하는 알림만 폴링한다.

```sql
SELECT * FROM notifications
WHERE status = 'PENDING'
  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
ORDER BY created_at ASC
LIMIT :batchSize
```

- `next_retry_at IS NULL`: 최초 생성된 알림 (즉시 발송 대상)
- `next_retry_at <= NOW()`: 재시도 대기 시간이 경과한 알림

이 조건 덕분에 재시도 대기 중인 알림이 조기에 폴링되지 않는다.

## 3. 발송 실패 처리 흐름

```kotlin
@Transactional
fun handleSendResult(notification: Notification, result: SendResult) {
    if (result.success) {
        notification.status = NotificationStatus.SENT
        notification.sentAt = LocalDateTime.now()
    } else if (RetryPolicy.isRetryable(notification)) {
        notification.retryCount++
        notification.nextRetryAt = RetryPolicy.calculateNextRetryAt(notification.retryCount)
        notification.status = NotificationStatus.PENDING
    } else {
        notification.status = NotificationStatus.FAILED
    }
    notification.updatedAt = LocalDateTime.now()
}
```

### 상태 전이 예시

```
[최초 생성] status=PENDING, retryCount=0, nextRetryAt=null
     │
     ▼ 스케줄러 폴링
[1차 시도] status=PROCESSING, retryCount=0
     │
     ▼ 발송 실패
[재시도 대기] status=PENDING, retryCount=1, nextRetryAt=+1분
     │
     ▼ 1분 후 스케줄러 폴링
[2차 시도] status=PROCESSING, retryCount=1
     │
     ▼ 발송 실패
[재시도 대기] status=PENDING, retryCount=2, nextRetryAt=+5분
     │
     ▼ 5분 후 스케줄러 폴링
[3차 시도] status=PROCESSING, retryCount=2
     │
     ▼ 발송 실패
[최종 실패] status=PENDING, retryCount=3, nextRetryAt=+30분
     │
     ▼ 30분 후 스케줄러 폴링
[4차 시도] status=PROCESSING, retryCount=3
     │
     ▼ 발송 실패 (retryCount >= maxRetryCount)
[확정 실패] status=FAILED, retryCount=3
```

## 4. 장애 시나리오 대응

### 4.1 서버 재시작

- **상황**: 발송 처리 중 서버가 예기치 않게 종료
- **대응**: `PENDING` 상태의 알림은 DB에 그대로 유지된다. 서버 재시작 후 스케줄러가 자동으로 폴링을 재개하여 밀린 알림을 처리한다.
- **데이터 유실**: 없음 (DB에 영속화되어 있으므로)

### 4.2 PROCESSING 상태 고착 (Stuck)

- **상황**: 알림이 `PROCESSING` 상태로 전환된 후 서버가 죽거나 처리가 중단되어 상태가 갱신되지 않음
- **대응**: 별도의 복구 스케줄러가 주기적으로 실행되어, `PROCESSING` 상태가 5분 이상 지속된 알림을 감지하고 `PENDING` 상태로 복귀시킨다.

```kotlin
@Scheduled(fixedDelay = 60_000) // 1분마다 실행
@Transactional
fun recoverStuckNotifications() {
    val threshold = LocalDateTime.now().minusMinutes(5)
    val stuck = notificationRepository.findStuckNotifications(
        status = NotificationStatus.PROCESSING,
        updatedBefore = threshold
    )
    stuck.forEach {
        it.status = NotificationStatus.PENDING
        it.updatedAt = LocalDateTime.now()
    }
}
```

```sql
SELECT * FROM notifications
WHERE status = 'PROCESSING'
  AND updated_at < :threshold
```

### 4.3 채널 장애 (이메일 서버 다운 등)

- **상황**: 외부 발송 채널이 일시적으로 장애
- **대응**: 발송 실패 시 재시도 정책에 따라 지수 백오프로 자동 재시도한다. 간격이 점진적으로 늘어나므로 장애 중인 외부 시스템에 과도한 요청을 보내지 않는다.
- **복구**: 채널이 복구되면 다음 재시도에서 정상 발송된다.

### 4.4 최종 실패 (최대 재시도 초과)

- **상황**: 3회 재시도 후에도 발송 실패
- **대응**: `FAILED` 상태로 확정한다. 이후 재시도하지 않는다.
- **후속 조치**: `FAILED` 상태의 알림은 모니터링/알림 시스템을 통해 운영자에게 통보하고, 필요 시 수동으로 재발송하거나 원인을 분석한다.

## 5. 설정값 요약

| 항목 | 값 | 설명 |
|------|-----|------|
| maxRetryCount | 3 | 최대 재시도 횟수 |
| 백오프 간격 | 1분, 5분, 30분 | 재시도별 대기 시간 |
| 폴링 주기 | 5초 | 스케줄러가 PENDING 알림을 조회하는 간격 |
| Stuck 감지 기준 | 5분 | PROCESSING 상태 지속 시간 초과 기준 |
| Stuck 복구 주기 | 1분 | 복구 스케줄러 실행 간격 |
