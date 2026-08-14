-- Flyway V12: 승리 팀은 종료 또는 정산 상태에서만 기록할 수 있다.

ALTER TABLE `betting_event`
    ADD CONSTRAINT `chk_betting_event_winner_status`
        CHECK (`winner_external_team_id` IS NULL OR `status` IN ('CLOSED', 'SETTLED'));
