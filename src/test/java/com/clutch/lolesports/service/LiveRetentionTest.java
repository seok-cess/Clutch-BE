package com.clutch.lolesports.service;

import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 종료된 매치를 라이브 목록에서 내리는 시점 검증.
 *
 * 소스의 getLive 는 경기가 끝나도 언제 내려갈지 예측할 수 없다 (2026-08-14 OME vs DOG:
 * 3세트 종료·outcome 확정 후 4.8분이 지나도 잔류). 그래서 우리가 종료로 판정한 시각부터
 * 세어 유지 시간이 지나면 직접 뺀다.
 */
class LiveRetentionTest {

    private static ScheduleResponse.Team team(String id, String code, Integer gameWins) {
        return new ScheduleResponse.Team(id, code, code, null,
                new ScheduleResponse.Result(null, gameWins), null);
    }

    private static DataCacheService.LiveMatch match(String matchId, int winsA, int winsB) {
        return new DataCacheService.LiveMatch(
                matchId, "1주 차", "LCK", "2026-08-14T00:00:00Z", 3,
                List.of(team("tA", "AAA", winsA), team("tB", "BBB", winsB)),
                List.of(new EventDetailsResponse.Game("g1", 1, "completed", List.of())),
                null);
    }

    /** retainSeconds 만 바꾼 스케줄러 (나머지 협력자는 이 테스트에서 쓰지 않는다) */
    private static PollingScheduler scheduler(long retainSeconds) {
        LolesportsProperties props = new LolesportsProperties(
                null, null, null, null, null, null, 0, 0, retainSeconds, null);
        return new PollingScheduler(null, null, null, null, null,
                new SetWinnerTracker(), props);
    }

    @Test
    void 진행중인_매치는_유지한다() {
        PollingScheduler s = scheduler(300);
        List<DataCacheService.LiveMatch> kept = s.dropLongFinished(List.of(match("m1", 1, 1)));
        assertEquals(1, kept.size());
    }

    /** 종료 직후에는 남긴다 — 사용자가 최종 스코어를 볼 시간이 필요하다 */
    @Test
    void 종료_직후에는_라이브에_남긴다() {
        PollingScheduler s = scheduler(300);
        List<DataCacheService.LiveMatch> kept = s.dropLongFinished(List.of(match("m1", 2, 1)));
        assertEquals(1, kept.size());
    }

    /** 유지 시간이 0 이면(경계) 종료 판정과 동시에 내려간다 */
    @Test
    void 유지_시간이_지나면_라이브에서_내린다() throws InterruptedException {
        PollingScheduler s = scheduler(1);
        DataCacheService.LiveMatch finished = match("m1", 2, 1);

        assertEquals(1, s.dropLongFinished(List.of(finished)).size());   // 타이머 시작
        Thread.sleep(1100);
        assertTrue(s.dropLongFinished(List.of(finished)).isEmpty());
    }

    /**
     * 종료 판정 기준 시각은 "처음 종료로 본 시점"이다.
     * 폴링마다 새로 잡으면 타이머가 계속 리셋되어 영영 안 내려간다.
     */
    @Test
    void 종료_시각은_처음_판정한_시점으로_고정한다() throws InterruptedException {
        PollingScheduler s = scheduler(1);
        DataCacheService.LiveMatch finished = match("m1", 2, 1);

        s.dropLongFinished(List.of(finished));
        Thread.sleep(600);
        s.dropLongFinished(List.of(finished));   // 중간 폴링 — 여기서 리셋되면 안 된다
        Thread.sleep(600);
        assertTrue(s.dropLongFinished(List.of(finished)).isEmpty());
    }
}
