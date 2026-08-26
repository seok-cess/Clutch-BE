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
- 회차를 열 때 활성 단계, 쿠폰 항목, 혜택 스냅샷과 유효 시간을 Redis 발급
  컨텍스트로 함께 준비한다. 요청은 이 컨텍스트와 Redis Lua를 먼저 사용한다.
- Redis가 `STOCK_EXHAUSTED` 또는 `ALREADY_CLAIMED`를 반환한 요청은 MySQL
  transaction과 조회를 시작하지 않는다. Redis 컨텍스트가 없으면 DB로 우회하지
  않고 fail-closed로 발급을 막는다.
- 요청 MySQL transaction에는 발급 요청, `user_coupon`, 결과 Outbox만 저장한다.
- 요청 transaction에서 `coupon_event_item.success_count`를 증가시키지 않는다.
- `success_count`는 5초 주기의 단일 스케줄러가 실제 `user_coupon` 수를 항목별
  `GROUP BY` 한 번으로 집계하여 보정하는 비동기 집계값으로 바꾼다.
- `user_coupon.coupon_event_item_id` 인덱스로 전체 항목 집계 시 테이블 본문 반복 조회와
  항목별 N개 COUNT 쿼리를 피한다.
- 다중 애플리케이션 인스턴스에서는 MySQL named lock을 즉시 획득한 한 인스턴스만
  집계를 실행한다. 다른 인스턴스는 대기하지 않고 해당 주기를 건너뛴다. 같은 MySQL
  서버를 여러 환경이 공유하면 환경별로 lock name 설정을 구분한다.
- 집계 실행 시간, 성공·실패·잠금 스킵, 비교한 항목 수와 실제 갱신한 항목 수를
  Micrometer 메트릭으로 기록한다.
- Redis 재고 초기화와 장애 복구는 `success_count`가 아니라 실제 `user_coupon` 수를
  사용한다.
- Redis 복구는 재고와 당첨 사용자 집합뿐 아니라 발급 컨텍스트도 함께 재구축한다.
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
- 품절·중복 요청이 공통 이벤트 메타데이터 조회를 위해 MySQL에 몰리지 않는다.
- 발급 가능 여부에 대한 MySQL 단일 행 잠금이 사라진다.
- `success_count`와 실제 쿠폰 수는 최대 집계 주기만큼 일시적으로 다를 수 있다.
- 관리자 집계는 지연된 값임을 전제로 하며, 재고·복구 판단에는 사용하지 않는다.
- 동일 DB를 사용하는 인스턴스가 늘어나도 같은 전체 집계를 중복 실행하지 않는다.
- named lock 연결이 종료되면 MySQL이 잠금을 해제하며, 작업 정상 종료와 예외 발생
  시에도 애플리케이션이 명시적으로 잠금을 해제한다.
- 집계 작업은 인덱스를 한 번 순회하지만 전체 실제 쿠폰 수에 비례하므로 데이터가 더
  커지면 증분 집계를 별도 ADR로 검토한다.

## Follow-up

- 변경 전후 1,000/3,000/5,000/10,000 VU에서 행 잠금, Hikari pending, Claim P95를 비교한다.
- 실제 쿠폰 수와 `success_count` 차이를 별도 정합성 메트릭으로 추가할지 운영 결과를
  바탕으로 검토한다.
- 동기 `user_coupon` 저장 자체가 다음 병목으로 확인될 때 Redis Stream 비동기 발급을
  별도 ADR로 검토한다.
