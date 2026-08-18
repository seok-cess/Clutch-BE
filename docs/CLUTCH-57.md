## 배경

lolesports 비공식 피드를 수집해 LCK 경기 일정·순위·인게임 데이터를 REST API 로 제공한다.
서버가 단일 폴러로 소스를 호출하고, 사용자 요청은 캐시 또는 DB 로 응답한다.

실시간 경기는 초 단위로 데이터가 바뀌므로 인메모리 캐시로 처리하고,
종료된 경기는 최종값만 DB 에 적재해 영구 보관한다.

## 작업 범위

### 1. 데이터 저장 구조

경기 종료 시 캐시의 최종값을 DB 로 적재하고 캐시를 해제한다.

```
경기 중   피드 → 캐시 ─┬→ 실시간 화면
                       └→ 트리거 판정
경기 종료  캐시 → DB 적재 → 캐시 해제
경기 후    DB → 과거 전적 화면
```

캐시 프레임 수천 개를 통째로 넣지 않고, 마지막 프레임과 10초 간격 샘플만 남긴다.

| 데이터 | 테이블 | 세트당 |
|---|---|---|
| 팀 최종 총계 | `esports_game` | 1행 |
| 선수 최종 기록 | `game_player_stat` | 10행 |
| 골드 추이 | `game_timeline_point` | 약 200행 |

적재 → 캐시 해제 순서를 지켜야 한다. 먼저 지우면 저장할 값이 사라진다.
적재 실패 시 캐시를 남겨 다음 폴링에서 재시도한다.

### 2. 과거 경기 조회 경로

```
캐시에 있나? ─예→ 캐시
  └아니오→ DB 에 적재됐나? ─예→ DB
             └아니오→ 소스 재수집 (세트당 약 1.5분)
```

DB 경로가 없을 때는 과거 경기를 열 때마다 소스를 수백 번 호출해
세트당 1.5분이 걸렸다. 적재 후에는 즉시 응답한다.

### 3. 과거 경기 백필

운영용 엔드포인트로 완료된 과거 매치를 일괄 적재한다.
백그라운드로 돌고 진행 상황을 별도 조회한다.

```
POST /api/admin/backfill?limit=1000
GET  /api/admin/backfill/status
```

### 4. 스키마 정리 (V4)

화면이 실제로 참조하는 컬럼만 남겼다. `game_timeline_point` 는 세트당 수백 행이
쌓이는 유일한 테이블이라 19개 → 7개로 줄였다.

주요 제거 항목

- 프레임마다 반복 저장되던 오브젝트 개수 8개 → 획득 시점은 `objectives_json` 이 담당
- `gold_diff` → `blue_gold - red_gold` 파생값
- 적재 결과 값이 채워지지 않거나 화면이 쓰지 않는 컬럼
  (`data_dragon_version`, `league_name`, `league_slug`, `esports_match.ended_at`)

### 5. 일정 화면 월별 필터

시즌 전체 320건이 한 번에 나열돼 원하는 날짜를 찾기 어려웠다.
월 탭을 추가하고, 진행 중인 경기가 있으면 그 달을 기본 선택한다.

## API

| 기능 | Method | URL |
|---|---|---|
| 경기 일정 | GET | `/api/schedule` |
| 순위 | GET | `/api/standings` |
| 라이브 현황 | GET | `/api/live` |
| 스코어보드 | GET | `/api/live/{gameId}/scoreboard` |
| 골드 추이 | GET | `/api/live/{gameId}/history` |
| 선수 상세 | GET | `/api/live/{gameId}/details` |
| 세트 목록 | GET | `/api/matches/{matchId}/games` |
| 매치 팀 | GET | `/api/matches/{matchId}/teams` |
| 최근 폼 | GET | `/api/records/recent` |
| 상대 전적 | GET | `/api/records/h2h` |

## 검증

과거 매치 287건을 실제로 적재해 확인했다.

```
esports_match          287행
match_team             574행
esports_game           747행
game_player_stat     7,470행
game_timeline_point 141,554행
```

- 값 일치 — 1세트 16:6 킬, 골드 60366:48132, 타워 11:0
- 선수 — `BFX Clear` / Nidalee / top / 1-0-3 / CS 289 / 딜지분 20.8%
- 오브젝트 17건 — 11:36 레드 산악용, 19:12 블루 구름용 등
- 적재 후 캐시 버퍼 0개
- 실패 2건 — 소스가 스탯을 제공하지 않는 경기 (재시도해도 동일)

## 참고

- 마이그레이션 V1~V4 전부 정상 적용, `gradlew build` 통과
- 스키마 상세는 `docs/schema-v4.md`, ERD import 용 DDL 은 `docs/schema.sql`

## 남은 작업

- 쿠폰 트리거 판정 로직 (`PentakillDetector` TODO)
- `/api/admin` 인증 — 현재 누구나 호출 가능
- 과거 열람분 캐시 상한
