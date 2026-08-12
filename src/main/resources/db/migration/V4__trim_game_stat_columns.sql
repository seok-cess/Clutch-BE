-- Flyway V4: 화면(API)이 소비하지 않는 경기 통계 컬럼을 정리한다.
--
-- 배경: 실시간 화면은 인메모리 캐시로 응답하고, DB 에는 과거 전적만 적재한다.
--       따라서 "경기 진행 중의 매 프레임"이 아니라 "종료 후 최종값"만 남기면 된다.
--       예외는 골드 추이 그래프로, 이것만 시계열이 필요하다.
--
-- 판단 근거는 프론트 코드다.
--   - GoldChart.jsx  : 툴팁이 쓰는 값은 gameTimeSeconds / blueGold / redGold / blueKills / redKills 뿐이고,
--                      오브젝트 마커는 별도 objectives 배열에서 온다 (OBJ_MARK 은 dragon/baron 만).
--                      타워·억제기는 "수가 많아 축을 뒤덮어" 의도적으로 제외돼 있다.
--   - Scoreboard.jsx : 타워/억제기/바론/용 아이콘은 esports_game 의 최종 총계를 쓴다 → 그쪽은 유지.
--
-- 수집 상태 컬럼(window/details/timeline_collection_status 등)과 game_player_stat.role 은
-- 재수집·향후 확장 여지를 위해 이번 범위에서 건드리지 않는다.

-- ---------------------------------------------------------------------------
-- 1. game_timeline_point — 세트당 수백 행이 쌓이는 유일한 테이블이라 정리 효과가 가장 크다.
-- ---------------------------------------------------------------------------

-- 오브젝트 개수는 프레임마다 저장할 필요가 없다.
-- 그래프는 획득 "시점"만 표시하고, 그 정보는 esports_game.objectives_json 에 이미 있다.
-- CHECK 제약이 이 컬럼들을 참조하므로 컬럼보다 먼저 제거한다.
ALTER TABLE `game_timeline_point`
    DROP CHECK `chk_game_timeline_point_nonnegative`;

ALTER TABLE `game_timeline_point`
    ADD CONSTRAINT `chk_game_timeline_point_nonnegative`
        CHECK (`game_time_seconds` >= 0
            AND `blue_gold` >= 0
            AND `red_gold` >= 0
            AND `blue_kills` >= 0
            AND `red_kills` >= 0);

-- gold_diff 는 STORED 생성 컬럼이라 blue_gold - red_gold 로 언제든 계산된다.
-- 화면도 API 응답(ApiDtos.HistoryPoint)에서 서버가 계산해 내려주므로 저장할 이유가 없다.
ALTER TABLE `game_timeline_point`
    DROP COLUMN `gold_diff`;

ALTER TABLE `game_timeline_point`
    DROP COLUMN `blue_towers`,
    DROP COLUMN `red_towers`,
    DROP COLUMN `blue_inhibitors`,
    DROP COLUMN `red_inhibitors`,
    DROP COLUMN `blue_barons`,
    DROP COLUMN `red_barons`,
    DROP COLUMN `blue_dragon_count`,
    DROP COLUMN `red_dragon_count`;

-- source_frame_at 은 원본 프레임 시각으로 화면에서 쓰지 않는다.
-- 이 컬럼에 걸린 유니크 키도 함께 사라지지만, 중복 적재는
-- uk_timeline_game_time (game_id, game_time_seconds) 이 그대로 막아준다.
ALTER TABLE `game_timeline_point`
    DROP INDEX `uk_timeline_game_source_frame`;

ALTER TABLE `game_timeline_point`
    DROP COLUMN `source_frame_at`;

-- 시계열 지점 하나하나의 적재 시각은 조회에 쓰이지 않는다.
-- 세트당 수백 행에 곱해지는 컬럼이라 제거 효과가 크다
-- (적재 시점이 필요하면 부모인 esports_game.created_at 을 보면 된다).
ALTER TABLE `game_timeline_point`
    DROP COLUMN `created_at`,
    DROP COLUMN `updated_at`;

-- ---------------------------------------------------------------------------
-- 2. game_player_stat — 최종값 1행만 남기므로 "어느 프레임에서 떴는지"는 의미가 없다.
--    세트 단위 기준 시각이 필요하면 esports_game.final_window_frame_at /
--    final_details_frame_at 이 같은 정보를 담고 있다.
-- ---------------------------------------------------------------------------

ALTER TABLE `game_player_stat`
    DROP COLUMN `window_snapshot_at`,
    DROP COLUMN `details_snapshot_at`;

-- ---------------------------------------------------------------------------
-- 3. esports_game — 용 정보 중복 제거.
--    objectives_json 이 {시각, side, type, subtype} 배열로 종류와 순서를 모두 담는다.
--    Scoreboard.jsx 의 용 툴팁은 획득 시각까지 함께 보여주므로 objectives_json 이 있어야 하고,
--    blue/red_dragons_json 은 그 부분집합이라 남길 이유가 없다.
--    (개수만 필요한 곳은 JSON 배열 길이로 구한다.)
-- ---------------------------------------------------------------------------

ALTER TABLE `esports_game`
    DROP CHECK `chk_esports_game_blue_dragons_json`;

ALTER TABLE `esports_game`
    DROP CHECK `chk_esports_game_red_dragons_json`;

ALTER TABLE `esports_game`
    DROP COLUMN `blue_dragons_json`,
    DROP COLUMN `red_dragons_json`;

-- ---------------------------------------------------------------------------
-- 4. 실제 적재 결과로 확인된 미사용 컬럼.
--    148매치 · 378세트를 적재해 본 뒤 값이 한 건도 채워지지 않거나
--    화면이 참조하지 않는 것으로 확인된 컬럼들이다.
-- ---------------------------------------------------------------------------

-- 아이콘 조회용 Data Dragon 버전 — 적재 결과 전 세트가 NULL 이었다.
-- 프론트가 ddragon.js 에서 버전을 자체 관리하므로 서버가 내려줄 필요가 없다.
ALTER TABLE `esports_game`
    DROP COLUMN `data_dragon_version`;

-- league_name 은 값이 채워지지만 화면이 참조하지 않는다
-- (LCK 단일 리그라 league_external_id 로 구분이 끝난다).
-- league_slug 와 매치 종료 시각은 적재 결과 전 건이 NULL 이었다 —
-- 소스가 매치 단위 종료 시각을 주지 않으며, 필요하면 세트의 ended_at 으로 유도한다.
-- chk_esports_match_period 가 ended_at 을 참조하므로 컬럼보다 먼저 제거한다.
-- 매치 시작/종료 순서 검증은 ended_at 이 사라지면서 의미를 잃으므로 다시 만들지 않는다
-- (세트 단위 검증은 chk_esports_game_period 가 그대로 담당한다).
ALTER TABLE `esports_match`
    DROP CHECK `chk_esports_match_period`;

ALTER TABLE `esports_match`
    DROP COLUMN `league_name`,
    DROP COLUMN `league_slug`,
    DROP COLUMN `ended_at`;
