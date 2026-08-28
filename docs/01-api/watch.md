# 시청 API

시청 시간의 누적·수령 규칙은 [`../02-domain/watch.md`](../02-domain/watch.md)를
따른다. 이 API는 `X-User-Id`가 아니라 경로의 `{userId}`를 사용한다.

## 시청 시작

```http
POST /api/users/{userId}/matches/{matchId}/watch-sessions
```

응답 `200 OK`:

```json
{
  "sessionKey":"uuid",
  "matchId":115548147900750225,
  "enteredAt":"2026-08-28T01:00:00Z",
  "heartbeatIntervalSeconds":30,
  "sessionTimeoutSeconds":90,
  "heartbeatSequence":0
}
```

`userId`는 양수이고 `matchId`는 비어 있지 않아야 한다. 사용자가 없거나 경기가 없으면
`404`, 현재 시청할 수 없는 경기 또는 세션 전환 중이면 `409`다.

## Heartbeat

```http
POST /api/users/{userId}/watch-sessions/{sessionKey}/heartbeat
Content-Type: application/json

{"sequence":1}
```

응답 `200 OK`:

```json
{
  "rewardState":"ACCUMULATING",
  "rewardSequence":1,
  "accumulatedSeconds":30,
  "remainingSeconds":270,
  "rewardPoint":100
}
```

`sequence`은 1 이상이며 이전에 성공한 순번보다 커야 한다. `rewardState`는
`PAUSED`, `ACCUMULATING`, `CLAIMABLE` 중 하나다. 만료된 세션은 `410`, 다른 사용자의
세션은 `403`, 존재하지 않는 세션은 `404`, 순번 오류는 `409`다.

## 포인트 수령

```http
POST /api/users/{userId}/watch-sessions/{sessionKey}/point-claims
Content-Type: application/json

{"rewardSequence":1}
```

응답 `200 OK`:

```json
{
  "rewardSequence":1,
  "awardedPoint":100,
  "totalPoint":12600,
  "nextRewardSequence":2
}
```

`rewardSequence`은 현재 수령 가능한 양수 회차와 같아야 한다. 아직 수령 가능하지 않거나
회차가 다르면 `409`다. 세션 소유·만료·존재 여부 오류의 상태는 heartbeat와 같다.
