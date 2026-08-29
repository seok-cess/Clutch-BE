# 시청 도메인 규칙

## 범위

이 문서는 사용자의 경기 시청 세션, 유효 시청 시간 누적, 포인트 수령과 세션 종료 규칙을 정의한다.
매치·세트 상태와 세트 승자를 외부 데이터에서 해석하는 방식은
[`match-set-result.md`](match-set-result.md)를 따른다.

## 주요 개념

- 시청 세션: 한 사용자가 한 경기를 시청하기 위해 입장한 기록이다. DB의 `watch_session`은
  세션의 최종 상태를 보존한다.
- 활성 시청 세션: 사용자가 현재 heartbeat를 보내고 포인트를 적립할 수 있는 하나의 Redis 세션이다.
- 유효 시청 시간: 실제 진행 중인 세트가 있는 동안, 유효한 heartbeat 사이의 시간만 누적한 값이다.
- 포인트 수령 회차: 한 시청 세션에서 5분을 누적해 포인트를 받을 수 있는 순번이다. 첫 회차는 1이다.
- 수령 가능 상태: 유효 시청 시간이 5분에 도달해 수령 버튼을 보여 주고 추가 적립을 멈춘 상태다.

## 시청 입장과 활성 세션

- 사용자가 존재하고 매치 전체 상태가 `inProgress`인 경우에만 시청 세션을 시작할 수 있다.
- 라이브 폴링은 첫 세트의 시청 세션이 시작되기 전에 상세 응답의 리그·대회 식별자로 경기와 참가 팀을
  DB에 선저장한다. replay가 실행마다 새 경기 ID를 발급해도 같은 순서를 지킨다.
- 사용자 한 명은 동시에 하나의 활성 시청 세션만 가질 수 있다. 여러 경기의 시청 시간을 함께
  누적하거나 포인트를 함께 받는 기능은 제공하지 않는다.
- 동일 경기에 다시 입장하면 기존 누적 시간과 heartbeat 순번은 유지하고, 최신 화면에 새 `sessionKey`를
  발급한다. 이전 `sessionKey`의 heartbeat와 수령 요청은 거절한다.
- 다른 경기에 입장하면 기존 활성 세션을 종료한 뒤 새 세션을 만든다.
- 세션 전환과 포인트 수령은 사용자별 Redis lock으로 동시에 수행되지 않게 한다.

## 유효 시청 시간 누적

- 클라이언트는 서버가 응답한 주기(기본 30초)에 맞춰 증가하는 heartbeat 순번을 전송한다.
- heartbeat의 처리 시각은 클라이언트 시간이 아니라 서버 수신 시각을 사용한다.
- 현재 활성 세션·사용자·세션 생존 상태가 일치하고, heartbeat 순번이 이전 처리 순번보다 커야 한다.
- 한 heartbeat 간격에서 최대 60초만 유효 시청 시간으로 인정한다. 60초를 초과한 공백은 누적하지 않는다.
- 해당 매치의 live stats `gameState`가 `in_game`일 때만 시간을 누적한다. `paused`는 게임이
  일시 정지된 상태이므로 누적하지 않는다. 세트와 세트 사이, 라이브 게임이 없거나 끝난 상태에서도
  세션은 유지하되 적립 상태를 `PAUSED`로 반환한다.
- 유효 시청 시간이 수령 기준에 도달하면 `CLAIMABLE` 상태가 되고, 사용자가 수령하기 전까지
  추가 시간을 누적하지 않는다.

## 포인트 수령

- 유효 시청 시간 5분마다 100포인트를 수령할 수 있다.
- 수령 요청은 현재 포인트 수령 회차를 포함해야 하며, 아직 5분에 도달하지 않았거나 회차가 다르면
  수령할 수 없다.
- 수령 자격을 Redis에서 원자적으로 확인한 뒤, 사용자 포인트 증가와 `watch_point_transaction` 저장을
  하나의 DB transaction에서 처리한다.
- 동일 시청 세션·수령 회차의 거래는 하나만 저장한다. DB 지급 뒤 Redis 갱신 전에 재시도한 요청은
  기존 거래를 반환해 포인트를 중복 지급하지 않는다.
- 수령이 완료되면 누적 유효 시청 시간을 0으로 초기화하고, 수령 회차를 1 증가시켜 다음 5분 누적을
  시작한다.
- 포인트 수령 거래에는 사용자, 시청 세션, 수령 회차, 경기, 지급 포인트를 기록한다.

```text
시청 입장
→ 진행 중인 세트에서 유효 heartbeat 시간 누적
→ 5분 도달
→ 수령 가능 상태 및 시간 누적 정지
→ 사용자 수령 요청
→ 100포인트 지급과 거래 기록
→ 누적 시간 0, 다음 수령 회차 시작
```

## 세션 종료와 미수령 보상

- heartbeat 생존 키의 기본 TTL은 90초다. 이 시간 안에 다음 heartbeat가 없으면 세션을 종료한다.
- 같은 경기에 재입장해 세션 키가 교체되거나 다른 경기로 전환해도 기존 세션은 종료한다.
- 종료 시 Redis에 남은 마지막 시청 시간만 DB 세션에 반영한다.
- 부분 누적 시간과 수령 가능하지만 사용자가 아직 누르지 않은 보상은 자동 지급하거나 다음 세션으로
  이월하지 않는다.
- 종료된 DB 세션의 상태는 `COMPLETED`이며, 활성 세션의 상태는 `WATCHING`이다.

## Redis 상태와 기본 시간 설정

Redis는 활성 세션 판정과 고빈도 heartbeat 처리의 기준 저장소다. 기본 설정은 다음과 같다.

| 설정 | 기본값 | 용도 |
|---|---:|---|
| heartbeat 전송 주기 | 30초 | 클라이언트 heartbeat 권장 주기 |
| heartbeat 생존 TTL | 90초 | 다음 heartbeat가 없을 때 세션 종료 |
| 활성 세션 TTL | 120초 | 사용자별 활성 세션 유지 |
| 세션 상태 TTL | 1시간 | Redis 시청 상태의 최대 보관 시간 |
| 세션 전환 lock TTL | 10초 | 입장 전환과 수령의 동시 처리 방지 |
| 한 번에 인정할 최대 시간 | 60초 | 긴 heartbeat 공백의 부정 누적 방지 |
| 수령 기준 | 5분 | 한 회차의 유효 시청 시간 |
| 회차당 지급 포인트 | 100p | 수령 성공 시 지급 포인트 |

- heartbeat, 세션 키 교체, 수령 준비와 수령 완료는 Redis Lua script로 원자적으로 처리한다.
- Redis 키 만료로 세션을 종료할 때는 만료 이벤트를 처리해 DB 세션을 완료 상태로 전환하고 Redis 상태를
  정리한다.
- TTL은 `heartbeat 생존 TTL < 활성 세션 TTL < 세션 상태 TTL` 관계를 유지해야 한다.

## API 흐름

```text
POST /api/users/{userId}/matches/{externalMatchId}/watch-sessions
POST /api/users/{userId}/watch-sessions/{sessionKey}/heartbeat
POST /api/users/{userId}/watch-sessions/{sessionKey}/point-claims
```

- 시청 세션 시작 응답은 `sessionKey`, heartbeat 전송 주기, 세션 만료 기준과 마지막 heartbeat 순번을 준다.
- heartbeat 응답은 현재 수령 상태(`PAUSED`, `ACCUMULATING`, `CLAIMABLE`), 수령 회차, 누적·남은 시간과
  다음 수령 포인트를 준다.
- 포인트 수령 응답은 지급 포인트, 지급 뒤 사용자 총포인트와 다음 수령 회차를 준다.

## 데이터 정합성

- 사용자별 활성 세션은 Redis의 active 키와 세션 전환 lock으로 제어한다.
- `watch_session.session_key`는 유일하다.
- `watch_point_transaction`은 같은 시청 세션과 수령 회차의 중복 기록을 허용하지 않는다.
- 포인트 증가와 포인트 거래 저장은 같은 DB transaction에서 성공하거나 함께 rollback한다.
- 시청 시간 누적과 수령 상태 전환은 Redis Lua script의 단일 원자 연산으로 처리한다.

## 관련 코드

- `com.clutch.watch`
- `com.clutch.lolesports.service.LolesportsWatchAccrualEligibilityProvider`
- `src/main/resources/redis/watch/`
- `src/main/resources/db/migration/V6__watch_reward_schema.sql`
- `src/main/resources/db/migration/V7__watch_reward_claim_sequence.sql`
