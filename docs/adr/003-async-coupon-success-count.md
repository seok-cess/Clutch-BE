# ADR-003: 쿠폰 성공 수량 집계를 발급 경로에서 분리

- Status: Accepted
- Date: 2026-08-24
- Decision Makers: 쿠폰 발급 담당자

## Context

10,000 VU 쿠폰 발급 부하에서 Redis는 재고와 중복 발급을 정상 제어했지만, 실제 쿠폰을
동기로 생성한 뒤 모든 성공 요청이 같은 `coupon_event_item.success_count` 행을 갱신했다.
이 공통 행의 X-lock 대기가 Hikari 커넥션 대기와 HTTP timeout으로 확대됐다.

쿠폰은 응답 시점에 실제로 생성되어야 하므로 동기 발급 자체는 유지한다. 다만 Redis Lua가
재고와 동일 회차 중복을 이미 원자적으로 확정하므로, MySQL 성공 수량 행은 발급 허용을
결정하는 데 사용하지 않아도 된다.

## Decision

- Redis Lua가 재고 차감과 사용자 중복 방지를 계속 최종적으로 결정한다.
- 요청 MySQL transaction에는 발급 요청, `user_coupon`, 결과 Outbox만 저장한다.
- 요청 transaction에서 `coupon_event_item.success_count`를 증가시키지 않는다.
- `success_count`는 5초 주기의 단일 스케줄러가 실제 `user_coupon` 수로 보정하는
  비동기 집계값으로 바꾼다.
- Redis 재고 초기화와 장애 복구는 `success_count`가 아니라 실제 `user_coupon` 수를
  사용한다.
- 복구 정합성 검증은 `SUCCEEDED` 발급 요청 수와 실제 `user_coupon` 수가 일치하는지로
  수행한다.

## Alternatives Considered

### 공통 행에 낙관적 락 적용

높은 동시성에서 대부분의 요청이 version 충돌 후 재시도해 DB 작업량이 줄지 않는다.

### 공통 행에 비관적 락 적용

동일 행을 기다리는 요청 수를 늘려 현재의 행 잠금 대기를 더 악화시킨다.

### Redis Stream 기반 전체 비동기 쿠폰 발급

대규모 폭주 처리에는 유리하지만, 성공 응답의 의미가 예약 완료로 바뀌고 Redis Stream의
내구성·DLQ·사용자 처리 상태를 별도 설계해야 한다. 즉시 쿠폰 발급 요구와는 맞지 않아
이번 범위에서 선택하지 않는다.

## Consequences

- 사용자는 기존처럼 응답에서 실제 쿠폰 ID를 즉시 받는다.
- 발급 가능 여부에 대한 MySQL 단일 행 잠금이 사라진다.
- `success_count`와 실제 쿠폰 수는 최대 집계 주기만큼 일시적으로 다를 수 있다.
- 관리자 집계는 지연된 값임을 전제로 하며, 재고·복구 판단에는 사용하지 않는다.
- 집계 작업은 항목별 실제 쿠폰 수를 조회하므로 항목 수가 크게 늘면 집계 쿼리를 묶는
  후속 최적화가 필요하다.

## Follow-up

- 변경 전후 1,000/3,000/5,000/10,000 VU에서 행 잠금, Hikari pending, Claim P95를 비교한다.
- 집계 지연과 실패 횟수, 실제 쿠폰 수와 `success_count` 차이를 메트릭으로 추가한다.
- 동기 `user_coupon` 저장 자체가 다음 병목으로 확인될 때 Redis Stream 비동기 발급을
  별도 ADR로 검토한다.
