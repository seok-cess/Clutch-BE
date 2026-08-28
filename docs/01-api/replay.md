# 리플레이 제어 API

이 API는 `replay.enabled=true`일 때만 생성된다. fixture 재생의 도메인 경계는
[`../02-domain/replay.md`](../02-domain/replay.md)를 따른다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/replay/start` | 새 fixture 재생 시작 |
| `GET` | `/api/replay/status` | 현재 재생 위치 조회 |
| `POST` | `/api/replay/speed?value={1..20}` | 중단 없이 배속 변경 |

## 시작

`POST /api/replay/start`는 외부 소스가 `STUB`일 때만 가능하다. 성공 시 `200 OK`로
`runId`, `matchId`, `gameIds`를 반환한다. REAL 소스이면 `409`, replay 서버 연결·응답
오류면 `503`과 `{"error":"..."}`다.

## 상태와 배속

상태 조회와 배속 변경은 다음 형식의 `200 OK`를 반환한다.

```json
{
  "runId":"run-1",
  "matchId":"external-match-id",
  "esportsMatchId":12,
  "gameIds":["game-1"],
  "elapsedSeconds":42,
  "totalSeconds":1800,
  "progressPercent":2.3,
  "fixtureTime":"2026-08-28T00:00:42Z",
  "speed":2.0
}
```

`esportsMatchId`는 아직 DB에 적재되지 않았다면 `null`이다. `value`는 1 이상 20 이하여야
하며, 범위를 벗어나면 `400`이다. replay 서버 통신 오류는 `503`이다.
