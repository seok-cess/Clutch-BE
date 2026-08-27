-- 쿠폰 트리거 시연용으로 예약한 경기 행을 만든다.
--
-- 배경: coupon_event.esports_match_id 에는 fk_coupon_event_esports_match (V3) 가 걸려 있어
--       존재하지 않는 경기로는 이벤트를 만들 수 없다.
--
--       그런데 replay 스텁은 실행할 때마다 새 경기 ID(replay-<runId>-m1)를 만든다.
--       그래서 재생 경기에 쿠폰 이벤트를 미리 걸어둘 수 없다 — 다음 실행이면 ID 가 달라진다.
--
-- 해결: 고정 ID(-1)로 실제 경기 행을 하나 만들어 둔다. 테스트 이벤트는 이 행을 참조하므로
--       FK 를 만족하고, replay 재생 중 감지된 트리거가 이 ID 로도 발동한다.
--
--       실제 경기는 모두 양수 auto-increment 라 음수 ID 와 절대 겹치지 않는다.
--       AUTO_INCREMENT 카운터도 음수 값에는 영향받지 않는다.
--
-- 이 행은 지우지 않는다. 지우면 테스트 이벤트가 FK 위반으로 저장되지 않는다.

INSERT INTO `esports_match` (
    `esports_match_id`,
    `external_match_id`,
    `league_external_id`,
    `season_key`,
    `block_name`,
    `scheduled_at`,
    `lifecycle_status`,
    `best_of`
) VALUES (
    -1,
    'clutch-test-match',
    'clutch-test-league',
    'test',
    '쿠폰 트리거 테스트',
    '2000-01-01 00:00:00.000000',
    'unstarted',
    3
) ON DUPLICATE KEY UPDATE `esports_match_id` = `esports_match_id`;
