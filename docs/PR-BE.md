## 개요

경기 종료 시 캐시의 최종값을 DB 에 적재하고, 과거 경기 조회를 DB 경로로 전환한다.

기존에는 과거 경기를 열 때마다 소스를 다시 호출했다. livestats 가 한 번에 10초 창만 주기 때문에
전 구간 골드 추이를 얻으려면 세트당 200회 가까이 요청해야 했고, 실측 약 1.5분이 걸렸다.
적재 후에는 즉시 응답한다.

실시간 화면은 그대로 캐시를 쓴다. DB 에는 종료된 경기만 들어간다.

## 변경 사항

**적재 파이프라인**
- 엔티티·리포지토리 5종 — `EsportsMatch` / `MatchTeam` / `EsportsGame` / `GamePlayerStat` / `GameTimelinePoint`
- `GamePersistService` — 캐시 프레임 수천 개를 통째로 넣지 않고, 마지막 프레임과
  10초 간격 샘플만 남긴다 (화면이 그 이상을 쓰지 않는다)
- `PollingScheduler` — 종료 감지 → 적재 → 캐시 해제 순서로 처리한다.
  먼저 지우면 저장할 값이 사라지므로 순서가 중요하다. 적재 실패 시 캐시를 남겨 재시도한다
- 종료된 게임은 getLive 응답에서 사라지므로, 활성일 때 매치 스냅샷을 담아두고 적재 후 지운다

**조회**
- `GameQueryService` — `finalized_at` 이 채워진 세트만 DB 로 응답한다 (부분 적재를 화면에 노출하지 않기 위해)
- 조회 경로: 캐시 → DB → 소스 재수집

**캐시 수명**
- `DataCacheService.evictGame()` 추가
- 기존 `BUFFER_RETENTION_SECONDS`(2시간)는 한 게임 안에서만, 그마저 새 프레임이 들어올 때만 돈다.
  경기가 끝나면 폴링이 멈춰 정리가 영영 실행되지 않았다
- 열람용 버퍼에 LRU 상한(4개) 적용 — 사용자가 과거 경기를 여러 개 열면 계속 쌓였다

**백필**
- `POST /api/admin/backfill` — 과거 경기 일괄 적재. 백그라운드로 돌고 즉시 반환한다
- `GET /api/admin/backfill/status` — 진행률·ETA 조회

## DB 변경

- 마이그레이션: `V4__trim_game_stat_columns.sql`, `V5__coupon_trigger_schema.sql`
- 데이터 영향: 기존 데이터 유지 (쿠폰 테이블은 비어 있었고, 경기 테이블은 이번에 처음 채웠다)

**V4 — 화면 미사용 컬럼 정리**

`game_timeline_point` 를 19개 → 7개로 줄였다. 세트당 수백 행이 쌓이는 유일한 테이블이라
정리 효과가 가장 크다.

| 뺀 것 | 이유 |
|---|---|
| 오브젝트 8개 (towers·inhibitors·barons·dragon_count × 2) | 프레임마다 같은 값이 반복된다. 화면은 획득 시점만 쓰고 그건 `objectives_json` 에 있다 |
| `gold_diff` | `blue_gold - red_gold` 파생값 |
| `source_frame_at`, `created_at`, `updated_at` | 조회에 쓰이지 않는다 |

그 외 `blue/red_dragons_json`(objectives_json 과 중복), `data_dragon_version`,
`league_name`, `league_slug`, `esports_match.ended_at` 제거.

**V5 — 쿠폰 트리거 스키마**

이벤트 "정의"와 실제 "발동"을 분리했다.

- 신규 `esports_match_event` — 피드에서 감지한 원본 사건 (판정 근거)
- 신규 `coupon_event_occurrence` — 정의가 실제 발동한 회차 (오픈·마감 시각)
- **매치당 1이벤트 제약 해제** — `(esports_match_id)` → `(esports_match_id, trigger_type)`.
  한 경기에서 퍼블·바론·펜타킬이 모두 터질 수 있다
- 유니크 키를 회차 기준으로 이동 — `(user, item)` → `(user, occurrence)`.
  1세트 퍼블로 받고 2세트 퍼블로 또 받는 경우를 막지 않기 위해
- `coupon_type.discount_type`(RATE/AMOUNT) 추가, `discount_value` 를 `DECIMAL(10,2)` 로 변경

## 확인 방법

```bash
docker compose up -d
./gradlew bootRun

# 과거 경기 적재 (전체는 약 2시간, 테스트는 limit 을 낮게)
curl -X POST "http://localhost:8080/api/admin/backfill?limit=2"
curl "http://localhost:8080/api/admin/backfill/status"

# 적재된 경기 조회 — DB 에서 즉시 응답한다
curl "http://localhost:8080/api/live/{gameId}/scoreboard"
curl "http://localhost:8080/api/live/{gameId}/history?step=60"
```

## 테스트

- [x] `./gradlew build` 통과 (dev 머지 후 팀원 쿠폰 테스트 포함)
- [x] 과거 매치 287건 / 747세트 실제 적재
- [x] 값 대조 — 1세트 16:6 킬, 골드 60366:48132, 타워 11:0, 패치 16.15.800.4844
- [x] API 3종(`/scoreboard` `/history` `/details`) DB 응답 확인
- [x] 적재 후 캐시 버퍼 0개
- [x] 마이그레이션 V1~V5 순차 적용 후 데이터 141,554행 복원 확인

적재 결과

```
esports_match          287행
match_team             574행
esports_game           747행
game_player_stat     7,470행
game_timeline_point 141,554행
```

실패 2건은 소스가 스탯을 제공하지 않는 경기다 (재시도해도 동일).

## 리뷰 포인트

**팀원 쿠폰 코드와의 정합성** — dev 의 `CouponClaimRequest` 엔티티에 V5 가 추가한
`coupon_event_occurrence_id` · `completed_at` · `failure_code` · `failure_reason` 매핑이 없다.
전부 nullable 이라 현재는 무해하지만, 발급 로직을 붙일 때 필요하다.
유니크 키가 `(user, occurrence)` 로 바뀐 것도 함께 확인이 필요하다.

**시계열 저장 간격** — 10초로 잡았다. 화면 기본 `step` 이 10 이라 그보다 촘촘히 저장해도
쓰이지 않는다. 더 세밀한 그래프가 필요해지면 조정해야 한다.

**`/api/admin` 인증 없음** — 현재 누구나 호출할 수 있다. 배포 전 반드시 잠가야 한다.

**LRU 상한 4개** — 열람용 버퍼 기준이다. 게임당 60~80MB 라 잡은 값인데,
동시 사용자가 늘면 재조정이 필요할 수 있다.

## 남은 작업

- 쿠폰 트리거 판정 로직 (`PentakillDetector` TODO) — 적재보다 먼저 실행돼야 한다
- `/api/admin` 인증
- 경기 중 트리거의 시점 기준 — 스코어보드는 기본이 재생 모드라 실제보다 약 45초 뒤처진다.
  최신 프레임 기준으로 쏘면 화면에 나오기 전에 알림이 먼저 뜬다
