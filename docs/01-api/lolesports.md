# LoL Esports API

일정·라이브 화면은 외부 API를 요청마다 직접 호출하지 않고 인메모리 캐시에서 응답한다.
캐시에 없는 과거 세트의 통계 조회는 저장 데이터 반환 또는 온디맨드 적재를 시도한다.
외부 데이터의 종료·승자 확정 의미는 [`../02-domain/match-set-result.md`](../02-domain/match-set-result.md)를
따른다.

## 일정·순위·라이브

| 메서드 | 경로 | 쿼리 | 응답 |
|---|---|---|---|
| `GET` | `/api/schedule` | 없음 | 매치 일정 배열 |
| `GET` | `/api/standings` | 없음 | 외부 순위 섹션 배열 |
| `GET` | `/api/standings/teams` | `season`, `leagueId`, `tournamentIds` | 저장된 세트 기준 팀 순위표 |
| `GET` | `/api/live` | 없음 | `live`, `matches` |
| `GET` | `/api/records/recent` | 없음 | 팀 코드별 최근 5경기 |
| `GET` | `/api/records/h2h` | 필수 `a`, `b` | 두 팀 상대 전적 |
| `GET` | `/api/stats/players/kda` | `season`, `limit`(기본 5) | 시즌 KDA 순위 |
| `GET` | `/api/stats/champions` | `season`, `limit`(기본 5) | 챔피언 픽·승률 |

일정·라이브 매치에는 `matchId`, 리그·블록, `startTime`, `bestOf`, 팀 목록, 세트 목록,
`activeGameId`가 포함된다. 라이브 매치에는 외부 상태와 구분된 `matchFinished`,
`matchWinnerTeamId`가 있다.

세트 항목은 `gameId`, `number`, 외부 `state`, `feedFinished`, `winnerTeamId`,
`statsUnavailable`을 반환한다. `feedFinished`는 livestats가 먼저 알린 종료 신호이고,
`winnerTeamId`는 이후 승자가 확정되기 전까지 `null`일 수 있다.

## 매치·세트 조회

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/matches/{matchId}/games` | 매치의 세트 목록 |
| `GET` | `/api/matches/{matchId}/teams` | 외부 팀 ID를 포함한 참가 팀 목록 |
| `GET` | `/api/live/{gameId}/scoreboard` | 게임 시점의 스코어보드 |
| `GET` | `/api/live/{gameId}/history` | 골드 차이·오브젝트 타임라인 |
| `GET` | `/api/live/{gameId}/details` | 선수 상세 통계 |

### `lag` 규칙

스코어보드·히스토리·상세의 `lag`은 선택 쿼리다.

- 생략: 외부 소스가 요구하는 최소 지연과 화면 여유를 합친 재생 시점이다.
- `lag <= 0`: 캐시의 최신 프레임을 우선한다. 값이 블록 단위로 변할 수 있다.
- `lag > 0`: 현재 시각에서 해당 초만큼 이전 프레임을 사용한다. REAL 소스에서는
  10~300초 범위로 제한한다.
- STUB 소스에서는 replay 서버의 현재 fixture 시간에 맞춘 프레임을 사용한다.

`/history`는 추가로 `step`을 받아 포인트 표본 간격(초)을 정한다. 기본은 REAL 10초,
STUB 1초이며 최소 1초다.

### 응답 핵심 필드

- `scoreboard`: 프레임 시각, 게임 상태·패치, `gameTimeSeconds`, 블루-레드
  `goldDiff`, 진영별 골드·킬·오브젝트·참가자 스탯이다.
- `history`: 게임 시간별 골드·킬 포인트와 드래곤·바론·타워·억제기 이벤트다. 이벤트
  시각은 프레임 간 수치 증가로 계산하므로 프레임 간격 수준의 오차가 있을 수 있다.
- `details`: 선수별 킬 관여, 피해 점유, 와드, 획득 골드, 아이템 ID와 룬 ID다. 아이템과
  룬은 표시명으로 변환하지 않은 원본 ID 배열이다.

프레임을 찾지 못하면 이 세 API는 `404 No Content`를 반환한다.

## 운영·진단 엔드포인트

아래 엔드포인트도 `com.clutch.lolesports.api`에 있다. 일반 사용자 화면 계약이 아니며,
현재 Controller 수준의 관리자 권한 검증은 없다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/admin/backfill?limit=1000&all=false` | 완료된 과거 매치를 비동기 적재 시작 |
| `GET` | `/api/admin/backfill/status` | 백필 진행 상태 |
| `POST` | `/api/admin/repair/match-origin?dryRun=true` | 기존 매치의 원본 정보 대조·보정 |
| `GET` | `/api/operator/external-source` | 현재 외부 소스 조회 |
| `PUT` | `/api/operator/external-source` | 외부 소스를 `REAL` 또는 `STUB`으로 전환 |
| `GET` | `/api/debug` | 폴링·캐시·backoff 진단 스냅샷 |

백필 시작은 `202 Accepted`로 `started`, `limit`, `statusUrl`을 반환한다. 이미 실행 중이면
`409`다. `all=true`는 이미 적재된 세트도 다시 수집한다. 원본 보정은 `dryRun=true`가
기본이며 변경 대상을 집계만 하고 저장하지 않는다.

외부 소스 전환 API는 `external-source.enabled=true`일 때만 생성된다. 요청 본문은
`{"mode":"REAL"}` 또는 `{"mode":"STUB"}`이고, 응답은 현재 `mode`다. STUB 전환 전
replay 서버를 확인할 수 없으면 `503`, mode가 없거나 올바르지 않으면 `400`이다.

`/api/debug`은 서버 시각, 현재 livestats 지연, 폴링 backoff와 캐시 상태를 가변 JSON
객체로 반환한다. Controller 주석상 개발·라이브 테스트용이다.
