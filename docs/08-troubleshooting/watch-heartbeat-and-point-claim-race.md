# 시청 heartbeat와 포인트 수령 경합 트러블슈팅

## 문제 상황

네트워크 재시도, 여러 탭과 늦게 도착한 요청 때문에 heartbeat가 중복되거나 역순으로 처리되면
시청 시간이 과다 누적될 수 있다. 같은 5분 보상에 대한 동시 수령 요청은 포인트를 중복 지급할
위험이 있다.

## 원인

고빈도 시청 상태는 Redis에, 실제 사용자 포인트와 거래 이력은 MySQL에 저장하므로 두 저장소를
하나의 트랜잭션으로 묶을 수 없다. 최신 세션, heartbeat 순번과 포인트 수령 회차를 검증하지
않으면 이전 세션이나 재시도 요청도 정상 처리될 수 있다.

## 해결

### heartbeat 원자 검증

Redis Lua Script가 다음 값을 한 번에 확인하고 변경한다.

- 사용자별 현재 활성 세션과 최신 `sessionKey`
- heartbeat 생존 상태와 사용자 일치 여부
- 이전에 처리한 순번보다 큰 heartbeat인지 여부
- 서버 수신 시각 기준의 유효 간격
- 세션 전환 lock 존재 여부

한 heartbeat에서 최대 60초만 인정하고 실제 세트가 `in_game`일 때만 적립한다. 중복·역순
heartbeat와 교체된 이전 세션 요청은 상태와 누적 시간을 변경하지 않는다.

### 포인트 수령 멱등성

1. 사용자별 Redis 전환 lock으로 세션 교체와 수령을 직렬화한다.
2. Redis Lua Script가 현재 회차의 수령 가능 상태를 원자적으로 선점한다.
3. MySQL에서 사용자 행을 잠그고 포인트 증가와 거래 저장을 한 트랜잭션으로 처리한다.
4. `(watch_session_id, reward_sequence)` 유일성 제약으로 같은 회차의 거래를 최종 방어한다.
5. DB 지급 뒤 Redis 완료 전에 재요청되면 기존 거래를 조회해 같은 지급 결과를 반환한다.

## 검증

- 이미 처리한 순번 이하의 heartbeat가 누적 시간과 상태를 바꾸지 않는다.
- 최대 인정 간격을 초과한 요청은 순번을 처리하되 시간을 적립하지 않는다.
- 새 세션으로 교체된 뒤 도착한 이전 `sessionKey` 요청을 거절한다.
- active·alive 키가 만료된 세션을 heartbeat로 되살리지 않는다.
- 동일 세션·회차의 동시 수령에서도 100포인트와 거래 1건만 남는다.
- Redis 완료 처리 전 재시도는 기존 DB 거래를 반환한다.

## 남은 한계

Redis와 MySQL을 하나의 원자적 트랜잭션으로 묶은 구조는 아니다. 현재 구현은 회차별 유일성
제약과 기존 거래 조회로 중복 지급을 막으며, 장애가 발생하면 MySQL 거래를 최종 지급 사실로
사용한다.

## 관련 문서와 코드

- [시청 도메인 규칙](../02-domain/watch.md)
- [시청 API](../01-api/watch.md)
- `com.clutch.watch.service.WatchPointClaimService`
- `com.clutch.watch.service.WatchPointAwardService`
- `src/main/resources/redis/watch/`

