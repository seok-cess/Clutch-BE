# 매치·세트 상태와 결과 데이터 계약

## 범위

이 문서는 `esports_match`(매치)와 `esports_game`(세트)의 상태·결과를 어떤 의미로 저장하고
언제 신뢰할 수 있는지 정의한다. 시청 세션 차단, 포인트 수령 비활성화, 배팅 마감과 정산,
세트별 결과 제공이 이 계약에 의존한다.

## 소스 제약

LoL Esports 는 **세트 승자를 직접 주지 않는다.** window 피드, `getEventDetails`,
`getGames` 응답에 승패 필드가 없다. 대신 매치의 팀별 `gameWins` 가 세트 종료마다 오르므로,
폴링 간 증가분으로 승자를 판정한다. 골드·킬·억제기 같은 지표로 추정하지 않는다.

또한 소스의 세트 상태(`state`)는 실제 종료보다 늦다. 2026-08-13 라이브 실측
(KRX vs BFX, matchId `115548147900750225`):

| 시각(KST) | 사건 |
|---|---|
| 17:39:34 | livestats `gameState=finished` — 실제 종료 |
| 17:42:31 | esports-api 아직 `inProgress`, gameWins 0:0 |
| 17:44:43 | `completed` + gameWins 1:0 — **약 5분 뒤** |

따라서 **세트 종료 시점과 승자 확정 시점은 다르다.** 용도에 맞는 값을 써야 한다.

| 필요한 것 | 근거 | 지연 |
|---|---|---|
| 세트가 끝났는가 (화면 표시·배팅 창 시작) | livestats `gameState=finished` | 즉시 |
| 누가 이겼는가 (정산) | `gameWins` 증가분 | 약 5분 |

첫 세트 배팅은 공식 시작 전부터 열되, 공식 일정 또는 esports-api의 `inProgress` 상태만으로는
마감하지 않는다. 실제 livestats의 첫 인게임 프레임이 수신된 시각부터 1분 뒤에 마감한다. 따라서
일정 상태가 먼저 바뀌고 livestats가 늦게 열려도 시작 전 배팅이 사라지지 않는다. livestats가
영구적으로 오지 않는 경우에만 안전 마감 시각을 사용한다.

다음 세트 배팅은 원칙적으로 `gameState=finished` 프레임 시각부터 연다. 다만 해당 프레임의
캐시가 정리된 뒤 처음 관측한 값이 공식 `completed`뿐이면, 그 관측 시각을 대체 오픈 시각으로
쓴다. 이 경우 과거 시각으로 소급해 배팅을 열지 않으며, 다음 세트 게임 ID가 확인되는 즉시
선개설 이벤트에 연결한다. 실제 다음 세트의 시작 시각이 확인되면 그 시각부터 1분 뒤에 배팅을
마감한다. 시작 시각이 아직 없을 때의 20분은 무기한 오픈을 막기 위한 안전 마감일 뿐, 실제
시작 시각이 들어오면 1분 마감 시각으로 대체한다.

### 배팅 시작 전 화면 노출

첫 세트 배팅은 공식 시작 20분 전부터 열리므로, 해당 기간에는 아직 `/api/live`의 실제 라이브
목록이 비어 있을 수 있다. 이 경우 사용자 화면은 `GET /api/betting-candidates`로 실제 `OPEN`인
배팅 이벤트와 연결된 예정 매치를 조회해 배팅 카드를 표시한다. 매치가 라이브 목록에도 들어오면
프론트는 같은 외부 매치 ID를 한 번만 표시한다. 배팅 이벤트가 없는 30분 사전 적재 후보는 이 API에
포함하지 않는다.

`GET /api/matches/{externalMatchId}/betting-events/current`은 화면에 마감 시각이나 남은 시간을
제공하지 않는다. 화면은 `bettingAvailable`이 `true`이면 **배팅 가능**, `false`이면 **배팅 닫힘**만
표시한다. `closesAt`은 서버의 배팅 수락 판단과 안전 마감에만 사용하며 API로 노출하지 않는다.

다전제의 최대 세트(BO3의 3세트, BO5의 5세트)가 끝나면, 공식 최종 `gameWins` 응답이 늦어도
다음 세트 이벤트를 만들지 않는다. 이미 게임 ID 없이 선개설된 후속 이벤트가 있으면 결과 조정
작업이 매치 상세를 계속 재조회하고, 공식 종료가 확인되는 즉시 `CANCELLED` 처리한 뒤 등록된
배팅을 환불한다.

## 매치 상태 (`esports_match.lifecycle_status`)

소스 원본값을 그대로 쓴다. 세트가 아니라 **매치 전체**의 상태다.

| 값 | 의미 |
|---|---|
| `unstarted` | 매치 시작 전 |
| `inProgress` | 세트가 진행 중이거나 다음 세트를 기다리는 중 |
| `completed` | 최종 승리 팀이 결정되어 매치가 끝남 |

`completed` 판정은 **어느 팀이든 과반(`bestOf / 2 + 1`) 세트를 가져간 시점**이다.
세트 하나가 끝났다고 `completed` 로 바꾸지 않는다.

`gameWins` 가 약 5분 늦게 오르므로 매치 종료 판정도 그만큼 늦다. 소스 제약이라 우회할 수 없다.

## 세트 결과 (`esports_game`)

| 컬럼 | 의미 |
|---|---|
| `match_id` | 전체 매치 식별자 |
| `game_number` | 매치 내 세트 번호 |
| `lifecycle_status` | 세트 상태 (`unstarted` / `inProgress` / `completed`) |
| `ended_at` | 세트 종료 시각(UTC) |
| `external_game_id` | 외부 API 세트 식별자 |
| `winner_match_team_id` | 세트 승리 팀 |
| `winner_decided_at` | 승자 확정 시각(UTC) |

동일 매치·세트 번호의 중복 저장은 `uk_esports_game_match_number (match_id, game_number)` 가 막는다.

### 승자를 신뢰할 수 있는 조건

`winner_decided_at IS NOT NULL` 인 세트만 확정된 결과다. `NULL` 은 다음 중 하나다.

- 아직 `gameWins` 가 오르지 않음 (세트 종료 후 약 5분 이내)
- 서버 재시작으로 증가분을 관측하지 못함
- 진영 매핑이 없어 승자를 세트에 귀속하지 못함

**포인트 지급과 배팅 정산은 이 컬럼이 `NOT NULL` 인 세트만 사용한다.**
확정 전에는 지급을 보류한다 — 잘못 지급하면 회수가 어렵다.

### 승자 추적과 복구

- `gameWins` 증가와 세트 `completed` 반영 순서가 어긋나도 증가분을 보류했다가 귀속한다.
- 서버 재시작 첫 관측에서도 미확정 세트가 하나이거나 한 팀의 연속 승리만 남은 경우에는
  누적 승수와 이미 확정된 결과를 대조해 복구한다.
- 늦게 확정된 승자는 기존 `esports_game` 행에 반영해 다음 재시작의 복구 근거로 사용한다.
- livestats 종료(`ended_at` 존재) 뒤에도 `winner_decided_at`이 비어 있거나, 게임 적재가 늦어도
  연결된 배팅 이벤트가 `CLOSED`인데 승자가 비어 있으면, 결과 조정 작업이 라이브 목록과 무관하게
  매치 상세를 재조회한다.
  `OPEN` 이벤트는 종료가 DB에 적재된 세트에 연결된 경우에만 먼저 닫는다. 공식 `gameWins`
  증가가 확인될 때만 승자를 기록하고 기존 정산 흐름으로 넘긴다.
- 여러 미확정 세트에 양 팀 승리가 섞여 순서를 특정할 수 없으면 임의 판정하지 않는다.
  운영자가 공식 결과를 확인한 뒤 아래 관리자 API로 승자 기록과 정산을 원자적으로 수행한다.

```http
PUT /api/admin/betting-events/{bettingEventId}/winner
Content-Type: application/json

{"winnerTeamId":"외부 팀 ID"}
```

이미 다른 승자가 확정된 이벤트에는 `409 Conflict`를 반환하며, `OPEN` 또는 `CANCELLED`인
이벤트에는 복구 결과를 입력할 수 없다.

## 최종 승리 팀

매치 전체의 승패는 `match_team.outcome`(`win` / `loss`)과 `match_team.game_wins` 에 있다.
`outcome` 은 매치가 완전히 끝난 뒤 `getSchedule` 응답에서 채워진다.
`getEventDetails` 는 완료된 매치에서도 `outcome` 을 주지 않으므로 근거로 쓰지 않는다.

## 미해결 항목

- **진영 매핑**: `match_team.external_team_id` 가 대부분 비어 있어
  `esports_game` 의 blue/red 귀속이 되지 않는다. 이 값이 없으면 승자도 저장할 수 없다.
  `getSchedule` 응답에 팀 id 가 없는 것이 원인이며, `getEventDetails` 의 id 로 채워야 한다.
- **세트 사이 포인트 적립**: 세트와 세트 사이에는 포인트를 적립하지 않는다.
  이 구간에도 매치는 정의상 `inProgress` 이므로, 매치 상태가 아니라
  **진행 중인 세트의 존재 여부**(`esports_game.lifecycle_status = 'inProgress'`)로 판정해야 한다.
