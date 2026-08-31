# 관리자 쿠폰 운영 홈 API

## 조회

```http
GET /api/v1/admin/coupon-dashboard?date=2026-08-29&trendDays=7
X-User-Id: 1
```

- `date`는 Asia/Seoul 운영 기준일이며 생략하면 오늘이다.
- `trendDays`는 1 이상 30 이하이며 기본값은 7이다.
- 관리자 사용자만 조회할 수 있다.

## 발급 지표 의미

| 필드 | 의미 |
|---|---|
| `todayRequestCount` | Claim이 생성된 요청과 Kafka에 기록된 사전 거절 요청의 합계 |
| `todayIssuedCount` | `coupon_claim_request`가 `SUCCEEDED`인 요청 수 |
| `todayFailedCount` | Claim의 `FAILED` 수와 품절·중복·신청 불가·Redis 장애 거절 수의 합계 |
| `todayPendingCount` | `coupon_claim_request`가 `PENDING`인 요청 수 |
| `todaySuccessRate` | 성공을 성공과 실패 합계로 나눈 비율 |
| `issuanceTrend[].failedCount` | 날짜별 발급 처리 실패와 사전 거절의 합계 |

발급 성공·실패와 사전 거절은 Kafka Consumer가 KST 일별 통계에 누적한 값을
조회한다. 따라서 결과나 `coupon.claim.rejected` 이벤트가 Consumer에 도달하기 전에는
잠시 지연될 수 있다. 배포 전에 발생한 Claim과 저장된 거절은 Flyway에서 한 번
선집계한다. Kafka 발행 자체가 실패한 거절은 사용자 요청 보호를 위해 재시도
Outbox를 두지 않으므로 누락될 수 있다.

K6 등 부하 테스트 데이터는 이름으로 자동 제외하지 않는다. 운영 통계와 분리하려면 테스트
전용 데이터베이스를 사용하거나 별도 데이터 분류 계약을 먼저 정해야 한다.
