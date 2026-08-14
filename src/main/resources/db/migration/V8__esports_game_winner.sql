-- Flyway V8: 세트별 승리 팀을 기록한다.
--
-- 배경: 소스(LoL Esports)는 세트 승자를 직접 주지 않는다. window 피드·getEventDetails·
--       getGames 를 모두 확인했으나 승패 필드가 없다. 대신 매치의 팀별 gameWins 가
--       세트가 끝날 때마다 오르므로, 폴링 간 증가분으로 승자를 판정한다.
--       (골드·킬·억제기 같은 지표로 추정하지 않는다)
--
-- 2026-08-13 라이브 실측 (KRX vs BFX, matchId 115548147900750225):
--   17:39:34  livestats gameState=finished    실제 종료
--   17:42:31  esports-api 아직 inProgress, gameWins 0:0
--   17:44:43  completed + gameWins 1:0        약 5분 뒤 반영
--
-- 그래서 winner 는 세트 종료 즉시가 아니라 약 5분 뒤에 확정된다.
-- winner_decided_at 이 그 확정 시점이며, NULL 이면 아직 판정되지 않았다는 뜻이다.
-- 포인트·배팅 정산은 이 컬럼이 NOT NULL 인 세트만 신뢰해야 한다.
--
-- 세트 종료 여부·번호·외부 식별자는 기존 컬럼(lifecycle_status, ended_at,
-- game_number, external_game_id)으로 이미 조회할 수 있어 새로 만들지 않는다.
-- 매치 내 세트 번호 중복은 uk_esports_game_match_number 가 이미 막고 있다.

ALTER TABLE `esports_game`
    ADD COLUMN `winner_match_team_id` BIGINT NULL
        COMMENT '세트 승리 팀. gameWins 증가분으로 판정하며 종료 후 약 5분 뒤 확정'
        AFTER `red_match_team_id`,
    ADD COLUMN `winner_decided_at` DATETIME(6) NULL
        COMMENT '세트 승자 확정 시각(UTC). NULL 이면 미확정'
        AFTER `winner_match_team_id`,
    ADD KEY `idx_esports_game_winner` (`winner_match_team_id`, `match_id`),
    ADD CONSTRAINT `fk_esports_game_winner_match_team`
        FOREIGN KEY (`winner_match_team_id`, `match_id`)
        REFERENCES `match_team` (`match_team_id`, `match_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 승자는 이 세트에 참여한 두 진영 중 하나여야 한다
    ADD CONSTRAINT `chk_esports_game_winner_side`
        CHECK (`winner_match_team_id` IS NULL
            OR `winner_match_team_id` = `blue_match_team_id`
            OR `winner_match_team_id` = `red_match_team_id`),
    -- 확정 시각과 승자는 항상 함께 존재한다
    ADD CONSTRAINT `chk_esports_game_winner_decided`
        CHECK ((`winner_match_team_id` IS NULL AND `winner_decided_at` IS NULL)
            OR (`winner_match_team_id` IS NOT NULL AND `winner_decided_at` IS NOT NULL));
