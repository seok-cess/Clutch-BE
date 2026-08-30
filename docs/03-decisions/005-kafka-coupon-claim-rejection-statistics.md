# ADR-005: Kafka 기반 쿠폰 신청 거절 통계

- Status: Accepted
- Date: 2026-08-29
- Decision Makers: 쿠폰 관리자 대시보드 담당자

## Context

기존 관리자 운영 홈은 `coupon_claim_request`만 날짜별로 집계했다. 그러나 품절, 동일
회차 중복 신청, 신청 가능 시간 종료와 Redis 장애는 Redis 또는 발급 조건 검증에서 먼저
종료되므로 Claim 행이 생성되지 않는다. 이 때문에 대규모 신청에서 HTTP 409 또는 503이
발생해도 운영 홈의 실패 수는 0으로 보일 수 있었다.

거절마다 MySQL 행을 사용자 요청 thread에서 직접 저장하면 Redis로 DB 부하를 줄인 기존
발급 구조를 훼손한다. 통계 장애 때문에 원래 409·503 응답이 늦어지거나 500으로 바뀌어도
안 된다.

## Decision

- `CouponClaimApplicationService`가 기존 발급 서비스를 호출하고 `CouponClaimException`만
  거절 통계 대상으로 전달한다.
- 거절은 전용 bounded executor를 거쳐 `coupon.claim.rejected` Kafka 토픽에 비동기로
  발행한다. Kafka 메타데이터 조회가 지연돼도 사용자 요청 thread는 기다리지 않는다.
- 이벤트에는 `messageId`, 요청한 이벤트·회차 ID, 오류 코드와 UTC 발생 시각을 넣는다.
- Consumer는 `messageId`를 기본 키로 `coupon_claim_rejection_message`에 `INSERT IGNORE`해
  Kafka 재전달을 한 번만 반영한다.
- 관리자 운영 홈의 전체 요청과 실패 수는 기존 Claim 결과와 거절 메시지를 합산한다.
- 일별 추이는 두 원본을 UTC 범위로 조회한 뒤 KST 날짜로 묶는다.
- 통계 직렬화 또는 Kafka 발행 실패는 기록만 남기고 원래 사용자 응답을 변경하지 않는다.

## 처리 흐름

```text
사용자 쿠폰 신청
  -> 기존 Redis·발급 처리
  -> 성공: 기존 coupon_claim_request 기준 집계
  -> CouponClaimException
       -> 사용자에게 기존 409·503 응답
       -> coupon.claim.rejected 비동기 발행
       -> Consumer의 messageId 멱등 저장
       -> 운영 홈의 전체 요청·실패·일별 추이에 합산
```

## Consequences

- 품절, 중복, 신청 불가 시간과 Redis 장애가 배포 이후 운영 홈 실패 통계에 포함된다.
- 기존 쿠폰 발급 transaction과 Redis 재고 보상 규칙은 바뀌지 않는다.
- Kafka 발행을 기다리지 않아 사용자 응답 지연과 DB 쓰기 경합을 추가하지 않는다.
- Kafka가 중단된 동안 비동기 발행에 실패한 거절은 Outbox가 없으므로 통계에서 누락될 수
  있다. 사용자 발급 안정성과 거절 통계의 완전성 중 사용자 경로 격리를 우선한 선택이다.
- 순간 요청량이 전용 비동기 큐 용량을 넘긴 경우에도 사용자 요청은 유지하고 초과 통계
  이벤트는 경고 로그를 남긴 뒤 건너뛴다.
- 배포 전 거절은 원본 데이터가 없어 소급 집계할 수 없다.
- K6와 운영 데이터는 이벤트 이름으로 추정해 필터링하지 않는다. 정확한 분리가 필요하면
  테스트 전용 DB 또는 명시적인 데이터 분류 계약을 별도 도입한다.

## Alternatives Considered

### 요청 thread에서 MySQL에 거절 원본 저장

통계 유실을 줄일 수 있지만 품절 시 대량 요청이 다시 MySQL로 집중되어 Redis 선처리의
부하 절감 효과를 약화한다.

### 기존 `coupon.issue.result`에 거절 상태 추가

이 토픽은 Claim과 실제 쿠폰 생성 뒤의 확정 결과 계약이다. Claim ID가 없는 사전 거절을
섞으면 기존 Consumer와 DLT 계약이 모호해지므로 별도 토픽을 선택했다.

### 이벤트 이름으로 K6 데이터 제외

이름은 업무 식별자가 아니고 운영 이벤트도 같은 문자열을 사용할 수 있어 정확한 규칙이
될 수 없다. 코드에서 이름 기반 필터를 추가하지 않는다.

## 관련 코드

- `com.clutch.coupon.claim.service.CouponClaimApplicationService`
- `com.clutch.coupon.statistics.kafka.CouponClaimRejectionConsumer`
- `com.clutch.coupon.statistics.repository.CouponClaimRejectionStatisticsRepository`
- `src/main/resources/db/migration/V18__coupon_claim_rejection_statistics.sql`
