# API 명세서

Base URL `/api` · 응답 JSON · 시간 ISO 8601 UTC · 인증 없음

목록형 API 는 데이터가 없어도 404 가 아니라 빈 배열을 반환한다.

---

## 전체 목록

| # | 기능 | Method | URL | 설명 |
|---|---|---|---|---|
| 1 | 경기 일정 | GET | `/api/schedule` | 예정·진행중·종료 경기 목록 |
| 2 | 순위 | GET | `/api/standings` | 스테이지별 팀 순위 |
| 3 | 라이브 현황 | GET | `/api/live` | 지금 진행 중인 경기 |
| 4 | 스코어보드 | GET | `/api/live/{gameId}/scoreboard` | 세트의 팀·선수 지표 |
| 5 | 골드 추이 | GET | `/api/live/{gameId}/history` | 그래프용 시계열 + 오브젝트 |
| 6 | 선수 상세 | GET | `/api/live/{gameId}/details` | 아이템·룬·와드 |
| 7 | 세트 목록 | GET | `/api/matches/{matchId}/games` | 매치에 속한 세트들 |
| 8 | 매치 팀 | GET | `/api/matches/{matchId}/teams` | 팀 정보 (id 포함) |
| 9 | 최근 폼 | GET | `/api/records/recent` | 팀별 최근 경기 결과 |
| 10 | 상대 전적 | GET | `/api/records/h2h` | 두 팀 맞대결 |
| 11 | 내부 상태 | GET | `/api/debug` | 폴링·캐시 상태 (개발용) |
| 12 | 과거 적재 | POST | `/api/admin/backfill` | 과거 경기 DB 적재 (운영용) |
| 13 | 적재 진행률 | GET | `/api/admin/backfill/status` | 백필 진행 상황 |

---

## 1. 경기 일정 — `GET /api/schedule`

| 필드 | 타입 | 설명 |
|---|---|---|
| `startTime` | string | 경기 시작 시각 |
| `state` | string | `unstarted` / `inProgress` / `completed` |
| `blockName` | string | 라운드 표기 (예: "7주 차") |
| `matchId` | string | 매치 식별자 |
| `bestOf` | int | 다전제 수 (3 = BO3) |
| `teams[]` | array | 팀 2개 (아래 구조) |

**teams[] 구조** — 2·8·9·10번 API 에서도 같은 형태로 쓰인다

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | 팀 식별자. **이 API 에서는 항상 null** (아래 주의) |
| `name` / `code` | string | 팀 이름 / 약어 (T1, HLE) |
| `image` | string | 로고 URL |
| `outcome` | string | `win` / `loss` / null(미종료) |
| `gameWins` | int | 이 매치에서 딴 세트 수 |
| `wins` / `losses` | int | 시즌 전적 |

> **주의** 소스가 일정 응답에 팀 id 를 주지 않아 `id` 가 null 이다.
> 진영(블루/레드) 판별이 필요하면 8번 API 를 쓴다.

---

## 2. 순위 — `GET /api/standings`

| 필드 | 타입 | 설명 |
|---|---|---|
| `stageName` | string | 스테이지명 (예: "정규 시즌") |
| `sectionName` | string | 섹션명 |
| `rankings[].ordinal` | int | 순위 |
| `rankings[].teams[]` | array | 해당 순위 팀. 공동 순위면 여러 개 |
| └ `name` / `code` / `image` | string | 팀 정보 |
| └ `wins` / `losses` | int | 전적 |

---

## 3. 라이브 현황 — `GET /api/live`

| 필드 | 타입 | 설명 |
|---|---|---|
| `live` | boolean | 진행 중 경기 존재 여부 |
| `matches[].matchId` | string | 매치 식별자 |
| `matches[].leagueName` | string | 리그명 |
| `matches[].blockName` | string | 라운드 |
| `matches[].startTime` | string | 시작 시각 |
| `matches[].teams[]` | array | 팀 2개 |
| `matches[].games[]` | array | 세트 목록 (`gameId` / `number` / `state`) |
| `matches[].activeGameId` | string | 진행 중인 세트 id (없으면 null) |

진행 중 경기가 없으면 `{"live": false, "matches": []}`.

---

## 4. 스코어보드 — `GET /api/live/{gameId}/scoreboard`

**Query**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `lag` | int | N | 재생 지연(초). 생략 시 서버가 자동 결정 |

`lag` 규칙

| 값 | 동작 |
|---|---|
| 생략 | 재생 모드 — 소스 요구 지연 + 여유로 자동 (권장) |
| `> 0` | 재생 모드 — `now - lag` 시점. 10~300초로 제한 |
| `<= 0` | 최신 우선 — 지연은 적지만 10초 단위로 점프 |

> 기본이 재생 모드인 이유: 소스가 10초 블록에 초 단위 프레임을 한꺼번에 주므로,
> 최신만 보면 그 안의 변화가 전부 버려진다.

**Response**

| 필드 | 타입 | 설명 |
|---|---|---|
| `gameId` | string | 세트 식별자 |
| `rfc460Timestamp` | string | 이 프레임의 시각 |
| `gameState` | string | 게임 상태 (`in_game` 등) |
| `patchVersion` | string | 패치 버전 |
| `gameTimeSeconds` | long | 게임 경과 초 (미확정 시 null) |
| `goldDiff` | long | 골드차 (`blue - red`, 양수면 블루 우세) |
| `blue` / `red` | object | 팀별 지표 (아래) |

**팀 지표**

| 필드 | 타입 | 설명 |
|---|---|---|
| `esportsTeamId` | string | 팀 id. 8번 API 의 `id` 와 매칭해 진영 판별 |
| `totalGold` | long | 총 골드 |
| `totalKills` | int | 총 킬 |
| `towers` / `inhibitors` / `barons` | int | 오브젝트 수 |
| `dragons[]` | array | 획득한 용 종류 (순서대로) |
| `participants[]` | array | 선수 5명 (아래) |

**선수 (participants[])**

| 필드 | 타입 | 설명 |
|---|---|---|
| `participantId` | int | 참가 번호 (1~5 블루, 6~10 레드) |
| `summonerName` | string | 소환사명 |
| `championId` | string | 챔피언 |
| `role` | string | 포지션 |
| `level` | int | 레벨 |
| `kills` / `deaths` / `assists` | int | KDA |
| `creepScore` | int | CS |
| `totalGold` | long | 골드 |
| `currentHealth` / `maxHealth` | int | 체력 (과거 경기는 null) |

**상태 코드** 200 정상 / 404 해당 시점 프레임 없음

---

## 5. 골드 추이 — `GET /api/live/{gameId}/history`

**Query**

| 이름 | 타입 | 기본 | 설명 |
|---|---|---|---|
| `lag` | int | 자동 | 4번과 동일 |
| `step` | int | `10` | 샘플링 간격(초). 최소 1 |

**Response**

| 필드 | 타입 | 설명 |
|---|---|---|
| `gameId` | string | 세트 식별자 |
| `points[].gameTimeSeconds` | long | 경과 초 |
| `points[].goldDiff` | long | 골드차 |
| `points[].blueGold` / `redGold` | long | 팀별 골드 |
| `points[].blueKills` / `redKills` | int | 팀별 킬 |
| `objectives[].gameTimeSeconds` | long | 획득 시각 |
| `objectives[].side` | string | `blue` / `red` |
| `objectives[].type` | string | `dragon` / `baron` / `tower` / `inhibitor` |
| `objectives[].subtype` | string | 용 종류 (그 외는 null) |

> **정확도** 피드에 이벤트 시각이 없어 프레임 간 개수 증가로 역산한다.
> 프레임 간격만큼 오차가 있다. 오브젝트는 `step` 과 무관하게 1초 해상도로 탐지한다.

---

## 6. 선수 상세 — `GET /api/live/{gameId}/details`

`lag` 파라미터는 4번과 동일.

| 필드 | 타입 | 설명 |
|---|---|---|
| `gameId` | string | 세트 식별자 |
| `rfc460Timestamp` | string | 프레임 시각 |
| `participants[].participantId` | int | 참가 번호 |
| `participants[].summonerName` | string | 소환사명 |
| `participants[].championId` | string | 챔피언 |
| `participants[].killParticipation` | double | 킬 관여율 (0~1) |
| `participants[].championDamageShare` | double | 딜 지분 (0~1) |
| `participants[].wardsPlaced` / `wardsDestroyed` | int | 와드 설치 / 파괴 |
| `participants[].totalGoldEarned` | long | 획득 골드 |
| `participants[].items[]` | array | 아이템 ID |
| `participants[].perks[]` | array | 룬 ID |

> `items` / `perks` 는 ID 원본이다. 이름·아이콘 변환은 프론트의 Data Dragon 이 담당한다.

**상태 코드** 200 정상 / 404 프레임 없음

---

## 7. 세트 목록 — `GET /api/matches/{matchId}/games`

| 필드 | 타입 | 설명 |
|---|---|---|
| `gameId` | string | 세트 식별자 |
| `number` | int | 세트 번호 (1, 2, 3…) |
| `state` | string | `unstarted` / `inProgress` / `completed` |

과거 경기 열람의 진입점. 여기서 얻은 `gameId` 로 4·5·6번을 호출한다.

---

## 8. 매치 팀 — `GET /api/matches/{matchId}/teams`

응답 구조는 1번의 `teams[]` 와 같지만 **`id` 가 채워져 있다.**
스코어보드의 `esportsTeamId` 와 매칭해 블루/레드 진영을 판별할 때 쓴다.

---

## 9. 최근 폼 — `GET /api/records/recent`

팀 코드를 키로 하는 객체 (배열 아님).

| 필드 | 타입 | 설명 |
|---|---|---|
| `{팀코드}[].startTime` | string | 경기 시각 |
| `{팀코드}[].opponentCode` / `opponentName` | string | 상대 팀 |
| `{팀코드}[].outcome` | string | `win` / `loss` |
| `{팀코드}[].gameWins` | int | 내 세트 득점 |
| `{팀코드}[].opponentGameWins` | int | 상대 세트 득점 |

---

## 10. 상대 전적 — `GET /api/records/h2h`

**Query**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `a` | string | Y | 팀 A 코드 |
| `b` | string | Y | 팀 B 코드 |

예: `/api/records/h2h?a=T1&b=HLE`

| 필드 | 타입 | 설명 |
|---|---|---|
| `teamA` / `teamB` | string | 팀 코드 |
| `winsA` / `winsB` | int | 각 팀 승수 |
| `meetings[]` | array | 맞대결 목록 (9번과 같은 구조, 최신순) |

**상태 코드** 200 정상 / 400 `a` 또는 `b` 누락

---

## 11. 내부 상태 — `GET /api/debug`

| 필드 | 타입 | 설명 |
|---|---|---|
| `serverTime` | string | 서버 현재 시각 |
| `liveStatsLagSeconds` | int | 현재 적용 중인 소스 지연 |
| `backoff` | object | 작업별 연속 실패·차단 상태 |
| `scheduleCached` / `standingsCached` | boolean | 캐시 적재 여부 |
| `liveMatches` | array | 라이브 매치 스냅샷 |
| `activeGameIds` | array | 폴링 중인 세트 |
| `windowBuffers` / `detailsBuffers` | object | 게임별 프레임 버퍼 현황 |

> **개발 전용** 배포 시 제거하거나 프로파일로 잠글 것.

---

## 12. 과거 적재 — `POST /api/admin/backfill`

**Query**

| 이름 | 타입 | 기본 | 설명 |
|---|---|---|---|
| `limit` | int | `1000` | 처리할 매치 수 (최신순) |
| `all` | boolean | `false` | 이미 적재된 세트도 다시 수집 |

백그라운드로 돌고 즉시 반환한다 (`202 Accepted`).

| 필드 | 타입 | 설명 |
|---|---|---|
| `started` | boolean | 시작 여부 |
| `limit` | int | 적용된 매치 수 |
| `statusUrl` | string | 진행률 조회 경로 |

**상태 코드** 202 시작 / 409 이미 실행 중

> 세트당 소스 요청이 수백 건(약 11초)이라 전체 287매치는 약 2시간 걸린다.
> **인증이 없으므로 배포 전 반드시 잠가야 한다.**

---

## 13. 적재 진행률 — `GET /api/admin/backfill/status`

| 필드 | 타입 | 설명 |
|---|---|---|
| `running` | boolean | 실행 중 여부 |
| `matchesTotal` / `matchesScanned` | int | 전체 / 처리한 매치 수 |
| `gamesPersisted` | int | 적재한 세트 수 |
| `gamesSkipped` | int | 이미 적재돼 건너뛴 수 |
| `gamesFailed` | int | 실패 수 (소스가 스탯 미제공인 경기) |
| `currentMatch` | string | 처리 중인 매치 |
| `elapsedSeconds` | long | 경과 시간 |
| `progressPercent` | double | 진행률 |
| `etaSeconds` | long | 예상 잔여 시간 |
| `bufferedGames` | int | 캐시에 남은 게임 수 |

---

## 상태 코드 정리

| 코드 | 의미 |
|---|---|
| 200 | 정상 |
| 202 | 백필 시작됨 |
| 400 | 필수 쿼리 누락 (`/records/h2h`) |
| 404 | 해당 시점 프레임 없음 (scoreboard / details) |
| 409 | 백필 이미 실행 중 |
| 500 | 서버 오류 |
