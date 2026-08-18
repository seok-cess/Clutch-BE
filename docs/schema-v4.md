# DB 스키마 (V4)

2026-08-12 · `feat/CLUTCH-14` · 마이그레이션 V1~V4

## 워크플로우

**실시간은 캐시, 과거는 DB.** 경기가 끝나면 캐시의 최종값만 DB 로 옮기고 캐시를 비운다.

```
경기 중   피드 → 캐시 ─┬→ 실시간 화면
                       └→ 트리거 판정
경기 종료  캐시 → DB 적재 → 캐시 해제
경기 후    DB → 과거 전적 화면
```

 **적재 → 캐시 해제** 순서로 진행되며
적재 실패 시 캐시를 남겨 다음 폴링에서 재시도한다.

| 데이터 | 테이블 | 세트당 |
|---|---|---|
| 팀 최종 총계 | `esports_game` | 1행 |
| 선수 최종 기록 | `game_player_stat` | 10행 |
| 골드 추이 (10초 간격) | `game_timeline_point` | ~200행 |

캐시 프레임 수천 개를 통째로 넣지 않는다. 마지막 프레임 + 10초 간격 샘플만 남긴다.

## 경기 도메인 테이블 — 어디에 쓰이나

### 계층

```
esports_match          T1 vs HLE (BO3)          1행
├─ match_team          T1 / HLE                 2행
└─ esports_game        1세트 · 2세트 · 3세트     세트당 1행
   ├─ game_player_stat   선수 10명               세트당 10행
   └─ game_timeline_point 골드 추이              세트당 ~200행
```

### 화면별

**일정 화면** — `esports_match` + `match_team`

```
17:00  종료  7주 차   [로고] kt Rolster  1 : 2  Hanwha Life [로고]
─────  ────  ─────    ────────────────────────────────────────────
       esports_match                  match_team 2행
```

`esports_match` 가 시각·라운드·다전제 수를, `match_team` 이 로고·팀명·스코어·승패를 준다.

**경기 상세 상단 (스코어보드)** — `esports_game`

킬 스코어, 골드바, 타워·억제기·바론·용 아이콘. 세트의 팀 최종 총계다.

**경기 상세 하단 (선수 표)** — `game_player_stat`

챔피언·레벨·KDA·CS·골드, 그리고 상세 표의 아이템·룬·와드·딜지분.

**골드 추이 그래프** — `game_timeline_point` + `esports_game.objectives_json`

선(線)은 시계열 테이블에서, 용·바론 마커는 `objectives_json` 에서 온다.

### 표로

| 테이블 | 단위 | 쓰이는 곳 |
|---|---|---|
| `esports_match` | 매치(다전제) | 일정 화면의 시각·라운드 |
| `match_team` | 매치당 2행 | 일정 화면의 대진·로고·스코어 + **세트별 진영(블루/레드) 판별** |
| `esports_game` | 세트 | 상세 상단 스코어보드 + 오브젝트 |
| `game_player_stat` | 세트당 10행 | 상세 하단 선수 표 |
| `game_timeline_point` | 세트당 ~200행 | 골드 추이 그래프 |


## V4 에서 뺀 컬럼

### `game_timeline_point` 19 → 7
남긴 것
`game_id · game_time_seconds · blue_gold · red_gold · blue_kills · red_kills` 

| 뺀 것 | 이유 |
|---|---|
| `gold_diff` | `blue_gold - red_gold` 파생값 |
| 오브젝트 8개 (towers·inhibitors·barons·dragon_count × 2) | 프레임마다 같은 값이 반복된다. 화면은 획득 **시점**만 쓰고 그건 `objectives_json` 에 있다 |
| `source_frame_at` | 원본 프레임 시각 — 그래프는 게임 경과 초를 쓴다 |
| `created_at` / `updated_at` | 시계열 점마다의 적재 시각은 조회에 안 쓰인다 |

### 그 외

| 테이블 | 뺀 것 | 이유 |
|---|---|---|
| `game_player_stat` | `window_snapshot_at` · `details_snapshot_at` | 최종값 1행만 저장하므로 무의미. 필요하면 `esports_game.final_*_frame_at` |
| `esports_game` | `blue_dragons_json` · `red_dragons_json` | `objectives_json` 과 중복 |
| `esports_game` | `data_dragon_version` | 적재 결과 전 세트 NULL. 프론트가 자체 관리 |
| `esports_match` | `league_name` | 값은 채워지지만 화면이 참조하지 않는다 (LCK 단일이라 `league_external_id` 로 충분) |
| `esports_match` | `league_slug` | 적재 결과 전 건 NULL |
| `esports_match` | `ended_at` | 적재 결과 전 건 NULL — 소스가 매치 단위 종료 시각을 주지 않는다 |



## 구현

| 단계 | 구현체 |
|---|---|
| 실시간 화면 | `DataCacheService` · `PollingScheduler` |
| 트리거 판정 | `PentakillDetector` (판정 로직 TODO) |
| 종료 시 적재 | `GamePersistService` |
| 과거 조회 | `GameQueryService` |
| 캐시 해제 | `DataCacheService.evictGame()` |
| 과거 백필 | `BackfillService` · `POST /api/admin/backfill` |

### 조회 경로

```
캐시에 있나? ─예→ 캐시           (진행 중이거나 방금 본 경기)
  └아니오→ DB 에 적재됐나? ─예→ DB
             └아니오→ 소스 재수집 (세트당 ~1.5분)
```

## 검증

과거 매치를 실제로 적재해 확인했다.

- 값 일치 — 1세트 16:6 킬, 골드 60366:48132, 타워 11:0, 패치 `16.15.800.4844`
- 선수 — `BFX Clear` / Nidalee / top / 1-0-3 / CS 289 / 딜지분 20.8% / 아이템 6개
- 시계열 — 0~1745초 171포인트, 골드차 -71 → +12184
- 오브젝트 17건 — 11:36 레드 산악용, 19:12 블루 구름용 등
- API 3종(`/scoreboard` `/history` `/details`) 모두 DB 로 응답
- 적재 후 캐시 버퍼 0개



## 남은 작업

1. 트리거 판정 로직 (`PentakillDetector` TODO) — 적재보다 먼저 실행돼야 한다
2. `/api/admin` 인증 — 현재 누구나 호출 가능
3. 쿠폰 도메인 — `coupon_event` 가 매치당 1건으로 제약돼 있어 트리거 6종을 담지 못한다

### 트리거 구현 가능 여부

| 트리거 | |
|---|---|
| 퍼블 · 22분 4용 · 22분 바론 · 60분 게임 · 퍼펙트 게임 | ✅ |
| 만골 역전 | ✅ `game_timeline_point` 시계열 필요 |
| 펜타킬 | ⚠️ 피드가 누적 킬 수만 준다. 프레임 간격(~10초) 안의 5킬을 연속킬로 볼지 판단 필요 |
| DPM 1000 이상 | ❌ 딜량 절대값이 없다. `championDamageShare`(0~1)만 있으므로 "딜 지분 %" 로 대체 권장 |

경기 중 트리거는 화면 지연과 시점을 맞춰야 한다. 스코어보드는 기본이 재생 모드라
실제보다 약 45초 뒤처지며, 최신 프레임 기준으로 쏘면 화면에 나오기 전에 알림이 먼저 뜬다.