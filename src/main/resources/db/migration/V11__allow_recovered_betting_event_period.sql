-- Flyway V11: 서버 재시작 후 복구한 이벤트는 마감 시각이 오픈 기록 시각보다 앞설 수 있다.
-- 실제 마감 시각은 세트 시작 시각 + 2분이며, 늦게 복구한 경우에도 이 값을 보존한다.

ALTER TABLE `betting_event`
    DROP CHECK `chk_betting_event_period`;
