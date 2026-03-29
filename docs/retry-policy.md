# 재시도 정책

## 1. 지수 백오프 (Exponential Backoff)

발송 실패 시 재시도 간격을 점진적으로 늘려, 일시적 장애 상황에서 시스템 부하를 줄인다.

| 재시도 횟수 | 대기 시간 | nextRetryAt 예시 (실패 시각 10:00 기준) |
|------------|-----------|----------------------------------------|
| 1회차 | 1분 | 10:01 |
| 2회차 | 5분 | 10:06 |
| 3회차 | 30분 | 10:36 |
| 최대 초과 | - | FAILED 확정 |

### RetryPolicy (단일 소스)

`RetryPolicy.MAX_RETRY_COUNT`가 최대 재시도 횟수의 단일 소스이다. 도메인 모델의 `maxRetryCount` 기본값도 이 상수를 참조한다.

```kotlin
object RetryPolicy {
    private val RETRY_INTERVALS = listOf(
        Duration.ofMinutes(1),   // 1차 재시도
        Duration.ofMinutes(5),   // 2차 재시도
        Duration.ofMinutes(30)   // 3차 재시도
    )
    const val MAX_RETRY_COUNT = 3

    /** 다음 재시도 시각 계산. 재시도 불가시 null 반환 */
    fun calculateNextRetryAt(currentRetryCount: Int, now: LocalDateTime): LocalDateTime? {
        if (currentRetryCount >= MAX_RETRY_COUNT) return null
        val interval = RETRY_INTERVALS.getOrElse(currentRetryCount) { RETRY_INTERVALS.last() }
        return now.plus(interval)
    }
}
```

## 2. nextRetryAt 기반 폴링 조건

스케줄러는 다음 조건을 만족하는 알림만 폴링한다.

```sql
SELECT * FROM notifications
WHERE status = 'PENDING'
  AND next_retry_at <= NOW()
ORDER BY next_retry_at ASC
LIMIT :batchSize
```

- `next_retry_at <= NOW()`: 재시도 대기 시간이 경과한 알림
- 최초 생성 시 `nextRetryAt`은 `LocalDateTime.now()`로 설정되므로 즉시 발송 대상이 된다.

이 조건 덕분에 재시도 대기 중인 알림이 조기에 폴링되지 않는다.

## 3. 발송 실패 처리: handleSendFailure()

재시도 판단과 상태 전이가 `Notification` Aggregate 내부에 캡슐화되어 있다.

```kotlin
fun handleSendFailure(now: LocalDateTime) {
    require(status.canFail) { "Cannot handle failure from $status" }
    val nextRetry = RetryPolicy.calculateNextRetryAt(retryCount, now)
    if (nextRetry != null) {
        retryCount++
        nextRetryAt = nextRetry
        status = NotificationStatus.PENDING
    } else {
        status = NotificationStatus.FAILED
    }
}
```

- `RetryPolicy.calculateNextRetryAt()`가 null을 반환하면 최대 재시도 초과로 `FAILED` 확정
- null이 아니면 `retryCount`를 증가시키고 `nextRetryAt`을 갱신하여 `PENDING`으로 복귀
- `status.canFail`로 `PROCESSING` 상태에서만 호출 가능하도록 보장

### NotificationProcessor에서의 호출

```kotlin
private fun handleFailure(notification: Notification) {
    try {
        notification.handleSendFailure(LocalDateTime.now())
        notificationRepository.save(notification)
        if (notification.status == NotificationStatus.PENDING) {
            logger.info("Notification scheduled for retry: id=${notification.id}, retryCount=${notification.retryCount}")
        } else {
            logger.warn("Notification max retries exceeded: id=${notification.id}")
        }
    } catch (retryError: Exception) {
        logger.error("Failed to handle retry for notification: id=${notification.id}", retryError)
    }
}
```

## 4. 상태 전이 예시

```
[최초 생성] status=PENDING, retryCount=0, nextRetryAt=now
     │
     ▼ 스케줄러 폴링 (startProcessing)
[1차 시도] status=PROCESSING, retryCount=0
     │
     ▼ 발송 실패 (handleSendFailure)
[재시도 대기] status=PENDING, retryCount=1, nextRetryAt=+1분
     │
     ▼ 1분 후 스케줄러 폴링
[2차 시도] status=PROCESSING, retryCount=1
     │
     ▼ 발송 실패 (handleSendFailure)
[재시도 대기] status=PENDING, retryCount=2, nextRetryAt=+5분
     │
     ▼ 5분 후 스케줄러 폴링
[3차 시도] status=PROCESSING, retryCount=2
     │
     ▼ 발송 실패 (handleSendFailure)
[재시도 대기] status=PENDING, retryCount=3, nextRetryAt=+30분
     │
     ▼ 30분 후 스케줄러 폴링
[4차 시도] status=PROCESSING, retryCount=3
     │
     ▼ 발송 실패 (handleSendFailure → retryCount >= MAX_RETRY_COUNT)
[확정 실패] status=FAILED, retryCount=3
```

## 5. 장애 시나리오 대응

### 5.1 서버 재시작

- **상황**: 발송 처리 중 서버가 예기치 않게 종료
- **대응**: `PENDING` 상태의 알림은 DB에 그대로 유지된다. 서버 재시작 후 스케줄러가 자동으로 폴링을 재개하여 밀린 알림을 처리한다.
- **데이터 유실**: 없음 (DB에 영속화되어 있으므로)

### 5.2 PROCESSING 상태 고착 (Stuck)

- **상황**: 알림이 `PROCESSING` 상태로 전환된 후 서버가 죽거나 처리가 중단되어 상태가 갱신되지 않음
- **대응**: `StuckNotificationRecoveryService`가 1분 주기로 실행되어, `PROCESSING` 상태가 5분 이상 지속된 알림을 감지하고 `resetToPending()`으로 `PENDING` 상태로 복귀시킨다.

```kotlin
@Service
class StuckNotificationRecoveryService(
    private val notificationRepository: NotificationRepository
) : RecoverStuckNotificationsUseCase {

    @Transactional
    override fun execute() {
        val threshold = LocalDateTime.now().minusMinutes(5)
        val stuckNotifications = notificationRepository.findStuckProcessing(threshold)
        stuckNotifications.forEach { notification ->
            notification.resetToPending()
            notificationRepository.save(notification)
        }
    }
}
```

### 5.3 채널 장애 (이메일 서버 다운 등)

- **상황**: 외부 발송 채널이 일시적으로 장애
- **대응**: 발송 실패 시 `handleSendFailure(now)`가 지수 백오프로 자동 재시도를 스케줄링한다. 간격이 점진적으로 늘어나므로 장애 중인 외부 시스템에 과도한 요청을 보내지 않는다.
- **복구**: 채널이 복구되면 다음 재시도에서 정상 발송된다.

### 5.4 최종 실패 (최대 재시도 초과)

- **상황**: 3회 재시도 후에도 발송 실패
- **대응**: `handleSendFailure(now)`가 `FAILED` 상태로 확정한다. 이후 재시도하지 않는다.
- **후속 조치**: `FAILED` 상태의 알림은 모니터링/알림 시스템을 통해 운영자에게 통보하고, 필요 시 수동으로 재발송하거나 원인을 분석한다.

## 6. 설정값 요약

| 항목 | 값 | 소스 |
|------|-----|------|
| maxRetryCount | 3 | `RetryPolicy.MAX_RETRY_COUNT` |
| 백오프 간격 | 1분, 5분, 30분 | `RetryPolicy.RETRY_INTERVALS` |
| 폴링 주기 | 5초 | `NotificationPollingScheduler` `@Scheduled(fixedDelay = 5000)` |
| Stuck 감지 기준 | 5분 | `StuckNotificationRecoveryService` `minusMinutes(5)` |
| Stuck 복구 주기 | 1분 | `StuckNotificationRecoveryScheduler` `@Scheduled(fixedDelay = 60000)` |
