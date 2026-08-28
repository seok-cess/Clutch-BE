# ADR-004: Kafka 발급 결과 기반 관리자 쿠폰 통계

- Status: Accepted
- Date: 2026-08-28
- Decision Makers: 쿠폰 발급 담당자

## Context

현재 실제 쿠폰은 MySQL transaction에서 동기로 생성되며, Kafka의
`coupon.issue.result`는 발급 완료 후 후속 결과를 전달한다. 같은 애플리케이션이 결과
이벤트를 다시 소비하지만 동기 발급 요청은 이미 `SUCCEEDED`이므로 기존 상태 처리만으로는
후속 이벤트의 실질적인 사용처가 없었다.

관리자는 전체 성공·실패 수와 쿠폰 이벤트별 결과를 확인하고, 결과 Consumer가 재시도를
소진한 처리 오류도 구분해서 확인해야 한다. Kafka는 at-least-once 전달이므로 같은 결과가
재전달되어도 통계가 중복 증가하지 않아야 한다.

## 현재 쿠폰 발급과 Kafka 흐름

```text
사용자 발급 요청
  -> Redis Lua: 재고 차감과 동일 회차 중복 당첨 방지
  -> MySQL transaction
       - coupon_claim_request 저장 및 SUCCEEDED 전이
       - user_coupon 실제 생성
       - wallet_outbox에 coupon.issue.result 저장
  -> 사용자에게 couponId와 성공 결과 응답

wallet_outbox Publisher
  -> coupon.issue.result 발행
  -> CouponIssueResultConsumer
       - 기존 PENDING Claim 상태 호환 처리
       - messageId 기준 성공·실패 통계 멱등 집계
       - 정상 처리 실패 시 1초 간격으로 3회 재시도
       - 재시도 소진 시 coupon.issue.result-dlt 전송
  -> CouponIssueResultDltConsumer
       - 원본 Kafka 레코드 좌표 기준 처리 오류 멱등 기록
```

현재 사용자 발급 API는 Kafka 응답을 기다리지 않는다. 실제 `user_coupon` 생성과 API 성공
응답은 MySQL transaction 안에서 먼저 확정되고, Kafka는 그 결과를 관리자 통계와 다른 후속
처리에 전달한다.

### 토픽별 역할

| 토픽 | 생산자 | 소비자 | 현재 역할 |
|---|---|---|---|
| `coupon.issue.result` | `wallet_outbox` Publisher | `CouponIssueResultConsumer` | 확정된 발급 결과 전달과 관리자 성공·실패 통계 집계 |
| `coupon.issue.result-dlt` | 결과 Consumer의 `DefaultErrorHandler` | `CouponIssueResultDltConsumer` | 재시도를 소진한 결과 메시지의 Kafka 처리 오류 기록 |
| `coupon.claim.accepted` | `coupon_claim_outbox` Publisher | `CouponClaimAcceptedConsumer` | 과거 비동기 발급 경로와의 호환용이며 현재 사용자 발급 핵심 경로에서는 사용하지 않음 |

## CLUTCH-272 변경 내역

- `CouponIssueResultService`가 발급 결과를 처리하는 transaction 안에서
  `CouponIssueStatisticsService`를 호출하도록 변경했다.
- `com.clutch.coupon.statistics` 패키지를 추가해 관리자 조회, 결과 집계, DLT 오류 기록을
  한 기능 영역으로 분리했다.
- `V17__coupon_issue_statistics.sql`로 다음 테이블을 추가했다.
  - `coupon_issue_statistics`: 쿠폰 이벤트별 성공·실패·Kafka 처리 오류 누적값
  - `coupon_issue_statistics_message`: `messageId` 기준 결과 메시지 중복 집계 방지
  - `coupon_kafka_processing_error`: 원본 Consumer group, topic, partition, offset 기준
    DLT 오류 중복 기록 방지와 원인 정보 저장
- 배포 전에 `wallet_outbox`에 쌓인 기존 `coupon.issue.result` 메시지를 migration에서
  선집계하도록 했다. 기존 PENDING 메시지가 배포 후 발행돼도 중복 증가하지 않는다.
- `CouponIssueResultDltConsumer`를 추가해 `coupon.issue.result-dlt` 메시지를 이벤트별
  처리 오류 또는 미분류 오류로 기록한다.
- DLT 기록 중 MySQL이 일시적으로 실패하면 5초 간격으로 계속 재시도하는 전용 Kafka
  Listener factory를 추가했다.
- 관리자 조회 API `GET /api/v1/admin/coupon-statistics`를 추가했다.
- DLT 전용 Consumer group 설정
  `coupon.claim.kafka.statistics-dlt-group`을 `application.example.yaml`에 추가했다.
- 결과 멱등 집계, DLT 멱등 기록, 관리자 조회, 실제 Kafka 재시도와 DLT 이동을 검증하는
  단위·Repository·Kafka 통합 테스트를 추가했다.

## 장애별 동작

| 상황 | 실제 쿠폰 발급 | Outbox·Kafka | 관리자 통계 |
|---|---|---|---|
| Kafka 브로커 중단 | MySQL 발급은 정상 완료 | `wallet_outbox`가 `PENDING`으로 남고 복구 후 재발행 | 복구 전까지 지연되며 DLT 오류는 증가하지 않음 |
| 결과 Consumer 처리 실패 | 이미 발급된 쿠폰에는 영향 없음 | 최초 처리 후 3회 재시도하고 `coupon.issue.result-dlt`로 이동 | 성공·실패 대신 처리 오류가 증가 |
| 같은 결과 메시지 재전달 | 영향 없음 | at-least-once 전달 허용 | `messageId`가 같으면 한 번만 집계 |
| DLT 기록 중 MySQL 장애 | 영향 없음 | DLT Consumer가 5초 간격으로 계속 재시도 | DB 복구 후 오류 통계에 반영 |
| Redis 장애 또는 재고 키 유실 | 발급 전 fail-closed로 차단 | 결과 Outbox와 Kafka 메시지가 생성되지 않음 | 성공·실패·DLT 통계에 포함되지 않음 |
| 발급 MySQL transaction 실패 | `user_coupon`과 Claim 변경을 rollback하고 Redis를 보상 | 결과 Outbox가 함께 rollback되어 발행되지 않음 | 결과 이벤트가 없으므로 실패 통계에 포함되지 않음 |

## Decision

- 기존 `coupon.issue.result` Consumer transaction에서 Claim 상태 호환 처리와 관리자 통계
  집계를 함께 수행한다.
- `messageId`를 `coupon_issue_statistics_message`의 기본 키로 저장하고 처음 처리한 메시지만
  이벤트별 성공 또는 실패 수를 증가시킨다.
- 이벤트별 누적값은 `coupon_issue_statistics`에 저장해 관리자 조회 시 전체 발급 이력을
  매번 GROUP BY 하지 않는다.
- 기존 `wallet_outbox`의 발급 결과는 migration에서 처리 메시지와 누적 통계로 선등록한다.
  배포 전에 생성된 PENDING Outbox가 나중에 재전송되어도 중복 집계하지 않는다.
- 결과 Consumer 처리 실패는 기존 `DefaultErrorHandler`의 1초 간격 3회 재시도 후 Spring
  Kafka 4.1 기본 목적지인 `<원본토픽>-dlt`로 전송한다.
- DLT Consumer는 원본 Consumer group, topic, partition, offset 조합으로 오류를 멱등
  저장한다. 원본 key 또는 payload의 `claimId`로 Claim을 찾을 수 있으면 쿠폰 이벤트별
  처리 오류에 포함하고, 찾을 수 없으면 미분류 오류로 집계한다.
- DLT 기록 중 MySQL 일시 장애가 발생하면 별도 Listener factory가 5초 간격으로 계속
  재시도한다. DLT 기록 실패를 다시 `-dlt` 토픽으로 보내 연쇄 DLT가 생기지 않게 한다.
- 관리자 API는 전체 요약과 최근 활동 순 이벤트별 성공·실패·Kafka 처리 오류를 반환한다.

## 통계 의미

- 성공: `CouponIssueResultStatus.SUCCEEDED` 결과 이벤트 수
- 실패: `CouponIssueResultStatus.FAILED` 결과 이벤트 수
- 처리 오류: 결과 Consumer가 재시도를 소진해 DLT에 도달한 원본 Kafka 레코드 수
- 미분류 오류: payload와 key로 쿠폰 이벤트를 식별하지 못한 처리 오류 수

현재 동기 발급 transaction이 rollback된 요청은 Claim과 결과 Outbox도 함께 rollback되므로
실패 통계에 남지 않는다. 실패 수는 결과 이벤트로 확정된 실패만 의미하며, Redis 품절·중복
거절이나 HTTP 요청 오류 수가 아니다.

Kafka 브로커 자체가 중단되면 Consumer 처리 오류가 아니므로 DLT 오류가 증가하지 않는다.
결과 Outbox가 PENDING으로 남고 Kafka 복구 후 통계가 따라잡는다.

## Consequences

- 쿠폰 실제 발급은 계속 Kafka 장애와 무관하게 동기로 완료된다.
- 관리자 통계는 Kafka가 결과를 전달한 시점까지의 비동기 데이터이며 잠시 지연될 수 있다.
- 결과 Consumer와 통계 저장은 같은 transaction이므로 통계 저장 실패 시 Claim 호환 상태
  변경도 rollback되고 동일 메시지를 재시도한다.
- `messageId`와 원본 Kafka 좌표의 unique key가 중복 통계를 막는다.
- 처리 메시지 테이블은 발급 결과 수에 비례해 증가한다. 보관 기간이 필요할 정도로 커지면
  누적 통계와 멱등성 보장 기간을 고려한 정리 정책을 별도 결정한다.

## Alternatives Considered

### 관리자 조회마다 Claim과 user_coupon을 직접 GROUP BY

항상 원본 데이터를 조회한다는 장점이 있지만 데이터가 증가할수록 관리자 요청마다 큰 집계
비용이 발생하고, Kafka 후속 결과 경로와 기존 재시도·DLT를 활용하지 못한다.

### 별도 통계 Consumer group 추가

상태 처리와 통계를 독립적으로 확장할 수 있지만 같은 결과 메시지의 파싱·검증 실패가 기존
Consumer와 통계 Consumer에서 각각 DLT로 발행될 수 있다. 현재 단일 애플리케이션 범위에서는
기존 결과 Consumer transaction에 통계를 결합해 오류 결과를 한 번만 기록한다.

## 관련 코드

- `com.clutch.coupon.claim.result`
- `com.clutch.coupon.statistics`
- `src/main/resources/db/migration/V17__coupon_issue_statistics.sql`
- `docs/api/coupon-issue-statistics.md`
