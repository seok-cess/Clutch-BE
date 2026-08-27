package com.clutch.lolesports.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTriggerPort;
import com.clutch.lolesports.dto.external.WindowResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 첫 킬 판정 검증.
 *
 * 펜타킬과 달리 시간창이 없다 — "경기 통틀어 처음"이라는 단발 조건이라
 * 프레임이 뭉치거나 빠져도 0 에서 벗어났다는 사실은 변하지 않는다.
 */
class FirstBloodDetectorTest {

    private static final String MATCH = "match-1";
    private static final String GAME = "game-1";
    private static final Instant T0 = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant GAME_START = T0.minusSeconds(600);

    private RecordingTriggerPort trigger;
    private FirstBloodDetector detector;

    @BeforeEach
    void setUp() {
        trigger = new RecordingTriggerPort();
        detector = new FirstBloodDetector(trigger);
    }

    /** 양 팀 누적 킬이 blueKills, redKills 인 T0+offsetSeconds 시점의 프레임 */
    private static WindowResponse.Frame frame(
            long offsetSeconds, int blueKills, int redKills
    ) {
        return new WindowResponse.Frame(
                T0.plusSeconds(offsetSeconds).toString(), "in_game",
                teamFrame(blueKills), teamFrame(redKills));
    }

    private static WindowResponse.TeamFrame teamFrame(int kills) {
        WindowResponse.ParticipantFrame participant =
                new WindowResponse.ParticipantFrame(
                        4, 10_000L, 15, kills, 1, 3, 200, 2000, 2000);
        return new WindowResponse.TeamFrame(
                50_000L, 0, 0, 0, kills, List.of(), List.of(participant));
    }

    private void feed(long offsetSeconds, int blueKills, int redKills) {
        detector.onNewWindowFrame(
                MATCH, GAME, frame(offsetSeconds, blueKills, redKills), GAME_START);
    }

    @Test
    void 킬이_0에서_1이_되면_발동한다() {
        feed(0, 0, 0);
        feed(10, 1, 0);

        assertEquals(1, trigger.fired.size());
        Fired fired = trigger.fired.get(0);
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.trigger());
        assertEquals(MATCH, fired.externalMatchId());
        assertEquals(GAME, fired.externalGameId());
    }

    @Test
    void 첫_관측이_0킬이면_발동하지_않는다() {
        // 기준만 세우는 프레임이다
        feed(0, 0, 0);

        assertTrue(trigger.fired.isEmpty(), "첫 프레임만으로는 발동하면 안 됨");
    }

    @Test
    void 중도_합류로_이미_킬이_있으면_발동하지_않는다() {
        // 폴링이 경기 도중 시작되면 첫 킬은 이미 지나갔다.
        // 지나간 사건으로 쿠폰을 여는 것보다 놓치는 편이 낫다
        feed(0, 3, 2);
        feed(10, 4, 2);

        assertTrue(trigger.fired.isEmpty(), "지나간 첫 킬로 발동하면 안 됨");
    }

    @Test
    void 폴링이_밀려_여러_킬이_뭉쳐_들어와도_발동한다() {
        // 첫 킬은 0 에서 벗어나는 상태 전이라 뭉침에 강하다.
        // 펜타킬과 달리 프레임 간격을 따로 검사할 필요가 없다
        feed(0, 0, 0);
        feed(120, 2, 1);

        assertEquals(1, trigger.fired.size(), "뭉쳐 들어와도 첫 킬은 첫 킬이다");
    }

    @Test
    void 한_게임에서_두_번_발동하지_않는다() {
        feed(0, 0, 0);
        feed(10, 1, 0);
        feed(20, 3, 2);
        feed(30, 5, 4);

        assertEquals(1, trigger.fired.size(), "게임당 1회만 발동해야 함");
    }

    @Test
    void 레드팀이_먼저_잡아도_발동한다() {
        feed(0, 0, 0);
        feed(10, 0, 1);

        assertEquals(1, trigger.fired.size());
    }

    @Test
    void 팀_프레임이_없으면_판정하지_않는다() {
        // 한쪽만 세면 빠진 팀의 킬을 0 으로 오판해 첫 킬을 뒤늦게 발동시킬 수 있다
        WindowResponse.Frame partial = new WindowResponse.Frame(
                T0.toString(), "in_game", teamFrame(0), null);
        detector.onNewWindowFrame(MATCH, GAME, partial, GAME_START);
        feed(10, 1, 0);

        assertTrue(trigger.fired.isEmpty(), "기준 프레임이 없으면 발동하면 안 됨");
    }

    @Test
    void 게임_경과_초를_함께_넘긴다() {
        feed(0, 0, 0);
        feed(10, 1, 0);

        // GAME_START 는 T0 의 600초 전이므로 T0+10 은 610초 지점이다
        assertEquals(610, trigger.fired.get(0).gameTimeSeconds());
    }

    @Test
    void 시작_시각을_모르면_경과_초는_비운다() {
        detector.onNewWindowFrame(MATCH, GAME, frame(0, 0, 0), null);
        detector.onNewWindowFrame(MATCH, GAME, frame(10, 1, 0), null);

        assertEquals(1, trigger.fired.size());
        assertEquals(null, trigger.fired.get(0).gameTimeSeconds());
    }

    @Test
    void 게임_상태를_비우면_다시_발동할_수_있다() {
        feed(0, 0, 0);
        feed(10, 1, 0);
        detector.clearGame(GAME);
        feed(20, 0, 0);
        feed(30, 1, 0);

        assertEquals(2, trigger.fired.size(), "게임 상태 정리 후에는 다시 판정해야 함");
    }

    /** 발동 기록만 남기는 테스트용 포트 */
    private static final class RecordingTriggerPort implements CouponTriggerPort {
        private final List<Fired> fired = new ArrayList<>();

        @Override
        public void fire(
                CouponMatchTrigger trigger,
                String externalMatchId,
                String externalGameId,
                Integer gameTimeSeconds
        ) {
            fired.add(new Fired(
                    trigger, externalMatchId, externalGameId, gameTimeSeconds));
        }
    }

    private record Fired(
            CouponMatchTrigger trigger,
            String externalMatchId,
            String externalGameId,
            Integer gameTimeSeconds
    ) {
    }
}
