# 관리자 쿠폰 발급 통계 API

## 조회

```http
GET /api/v1/admin/coupon-statistics?size=20
X-User-Id: 1
```

- 관리자 사용자만 조회할 수 있다.
- `size` 기본값은 20이고 1 이상 100 이하여야 한다.
- 이벤트 목록은 최근 발급 결과 또는 처리 오류가 발생한 순서로 반환한다. 결과가 없는
  이벤트는 생성 시각을 기준으로 정렬하며 성공·실패·오류 수는 모두 0이다.
- 통계는 `coupon.issue.result`의 비동기 처리 결과이므로 실제 발급 직후 잠시 지연될 수 있다.

## 응답 예시

```json
{
  "summary": {
    "totalResultCount": 1250,
    "successCount": 1230,
    "failureCount": 20,
    "processingErrorCount": 3,
    "unassignedErrorCount": 1,
    "lastProcessedAt": "2026-08-28T05:30:00"
  },
  "events": [
    {
      "couponEventId": 10,
      "eventName": "펜타킬 기념 쿠폰",
      "triggerType": "PENTA_KILL",
      "eventStatus": "OPEN",
      "totalResultCount": 500,
      "successCount": 492,
      "failureCount": 8,
      "processingErrorCount": 1,
      "lastResultAt": "2026-08-28T05:30:00",
      "lastErrorAt": "2026-08-28T05:29:10"
    }
  ]
}
```

## 필드 의미

| 필드 | 의미 |
|---|---|
| `totalResultCount` | 성공과 실패 결과의 합계 |
| `successCount` | `SUCCEEDED` 발급 결과 이벤트 수 |
| `failureCount` | `FAILED` 발급 결과 이벤트 수 |
| `processingErrorCount` | 재시도 소진 후 `-dlt`에 도달한 Kafka 처리 오류 수 |
| `unassignedErrorCount` | 이벤트를 식별할 수 없는 처리 오류 수 |
| `lastProcessedAt` | 마지막 발급 결과 또는 처리 오류 시각 |

Kafka 브로커 중단은 Consumer 처리 오류가 아니므로 `processingErrorCount`에 포함되지 않는다.
이 경우 Outbox가 PENDING으로 남고 Kafka 복구 후 통계가 갱신된다.
