# 세트 승패 배팅 API

배팅 대상·마감·정산 규칙은 [`../02-domain/betting.md`](../02-domain/betting.md)를 따른다.
`X-User-Id`가 필요한 API는 양의 정수 사용자 ID를 요구한다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/betting-candidates` | 실제 `OPEN` 배팅 이벤트가 있는 예정·라이브 매치 목록 |
| `GET` | `/api/matches/{externalMatchId}/betting-events/current` | 매치의 현재 이벤트와 내 배팅 |
| `POST` | `/api/betting-events/{bettingEventId}/bets` | 세트 승리 팀과 배팅금 등록 |
| `GET` | `/api/betting-events/{bettingEventId}/bets/me` | 특정 이벤트의 내 배팅 조회 |
| `GET` | `/api/users/me/bets` | 내 배팅 전체 목록 |
| `PUT` | `/api/admin/betting-events/{bettingEventId}/winner` | 운영자 승자 복구 및 즉시 정산 |

## 배팅 후보와 현재 이벤트

`GET /api/betting-candidates`는 매치 ID, 리그·블록·시작 시각, `bestOf`, 팀 정보,
세트 목록과 `activeGameId`를 배열로 반환한다. 세트에는 외부 상태와 별도로
`feedFinished`, `winnerTeamId`, `statsUnavailable`을 포함한다.

```http
GET /api/matches/{externalMatchId}/betting-events/current
X-User-Id: 42
```

현재 이벤트 응답에는 `bettingEventId`, 매치·세트 ID, 세트 번호, 두 선택 팀 ID,
`status`, `bettingAvailable`, 그리고 내 배팅(`myBet`, 없으면 `null`)이 담긴다.
`bettingAvailable`은 서버가 현 시점에 등록을 수락하는지 나타내며 마감 시각은 노출하지
않는다.

## 배팅 등록

```http
POST /api/betting-events/{bettingEventId}/bets
X-User-Id: 42
Content-Type: application/json

{"selectedTeamId":"team-id","amount":1000}
```

- `selectedTeamId`는 비어 있을 수 없다.
- `amount`는 1,000 이상 100,000 이하다.
- 성공 시 `201 Created`와 `userBetId`, `userId`, `bettingEventId`, 선택 팀,
  금액, `PLACED` 상태, 차감 후 `remainingPoint`를 반환한다.

## 내 배팅 조회

두 조회 API는 배팅 ID, 이벤트 ID, 선택 팀, 금액, 사용자 배팅 상태와 생성/정산 정보를
반환한다. 전체 목록에는 매치·세트 정보와 두 참가 팀의 ID·표시 코드가 함께 포함된다.

## 운영자 승자 복구

```http
PUT /api/admin/betting-events/{bettingEventId}/winner
Content-Type: application/json

{"winnerTeamId":"team-id"}
```

성공 시 `204 No Content`다. 경로상 운영자 기능이지만 현재 Controller에는 관리자
권한 인자 해석기가 연결돼 있지 않다.

## 오류

오류 형식은 `{"code":"...","message":"..."}`다.

| 상태 | 대표 코드 |
|---|---|
| `400` | `INVALID_REQUEST`, `EVENT_NOT_OPEN`, `LIVE_DATA_UNAVAILABLE`, 금액·팀·포인트 관련 오류 |
| `404` | `EVENT_NOT_FOUND`, `BET_NOT_FOUND`, `USER_NOT_FOUND` |
| `409` | `DUPLICATE_BET`, `WINNER_ALREADY_DECIDED` |
