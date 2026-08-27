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
 * 펜타킬 판정 검증.
 *
 * 실제 피드 특성을 재현한다 — window 프레임은 약 10초 간격이라 5킬이 프레임
 * 하나에 다 들어오는 일은 드물고, 폴링이 밀리면 프레임이 통째로 빠진다.
 */
class PentakillDetectorTest {

    private static final String MATCH = "match-1";
    private static final String GAME = "game-1";
    private static final Instant T0 = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant GAME_START = T0.minusSeconds(600);

    private RecordingTriggerPort trigger;
    private PentakillDetector detector;

    @BeforeEach
    void setUp() {
        trigger = new RecordingTriggerPort();
        detector = new PentakillDetector(trigger);
    }

    /** 참가자 4번이 kills 만큼 누적한, T0+offsetSeconds 시점의 프레임 */
    private static WindowResponse.Frame frame(long offsetSeconds, int kills) {
        WindowResponse.ParticipantFrame participant = new WindowResponse.ParticipantFrame(
                4, 10_000L, 15, kills, 1, 3, 200, 2000, 2000);
        WindowResponse.TeamFrame team = new WindowResponse.TeamFrame(
                50_000L, 0, 0, 0, kills, List.of(), List.of(participant));
        return new WindowResponse.Frame(
                T0.plusSeconds(offsetSeconds).toString(), "in_game", team, null);
    }

    private void feed(long offsetSeconds, int kills) {
        detector.onNewWindowFrame(MATCH, GAME, frame(offsetSeconds, kills), GAME_START);
    }

    @Test
    void 첫_관측은_기준만_세우고_발동하지_않는다() {
        // 게임 도중 합류하면 누적 킬이 이미 크다. 이를 증가분으로 세면 즉시 오탐
        feed(0, 7);

        assertTrue(trigger.fired.isEmpty(), "첫 프레임만으로는 발동하면 안 됨");
    }

    @Test
    void 프레임에_걸쳐_5킬이_쌓이면_발동한다() {
        // 실제 펜타킬은 10~20초에 걸쳐 일어나 프레임 여러 개로 나뉜다.
        // 프레임 하나의 delta 만 보면 3킬+2킬로 쪼개져 영영 안 잡힌다
        feed(0, 0);
        feed(10, 3);
        feed(20, 5);

        assertEquals(1, trigger.fired.size(), "창 안에 5킬이면 발동해야 함");
        Fired fired = trigger.fired.get(0);
        assertEquals(CouponMatchTrigger.PENTAKILL, fired.trigger());
        assertEquals(MATCH, fired.externalMatchId());
        assertEquals(GAME, fired.externalGameId());
    }

    @Test
    void 한_프레임에_5킬이_들어와도_발동한다() {
        feed(0, 0);
        feed(10, 5);

        assertEquals(1, trigger.fired.size());
    }

    @Test
    void 창보다_느리게_쌓인_5킬은_발동하지_않는다() {
        // 30초 창을 넘겨 천천히 쌓인 킬은 펜타킬이 아니다
        feed(0, 0);
        feed(10, 2);
        feed(25, 3);
        feed(45, 5);

        assertTrue(trigger.fired.isEmpty(), "창을 벗어난 킬은 합산되면 안 됨");
    }

    @Test
    void 폴링_공백_뒤_뭉쳐_들어온_킬은_버린다() {
        // 프레임이 통째로 빠지면 30초치 킬이 한 델타로 들어온다.
        // 이를 "짧은 시간에 5킬"로 보면 느리게 쌓인 킬이 펜타킬로 둔갑한다
        feed(0, 0);
        feed(120, 6);

        assertTrue(trigger.fired.isEmpty(), "신뢰할 수 없는 간격의 델타는 버려야 함");
    }

    @Test
    void 같은_참가자를_한_게임에서_두_번_발동하지_않는다() {
        feed(0, 0);
        feed(10, 5);
        feed(20, 8);
        feed(30, 12);

        assertEquals(1, trigger.fired.size(), "게임당 참가자 1회만 발동해야 함");
    }

    @Test
    void 킬이_줄어들면_기준만_갱신한다() {
        // 소스가 값을 되돌리는 경우가 있다(재접속·보정)
        feed(0, 5);
        feed(10, 2);
        feed(20, 4);

        assertTrue(trigger.fired.isEmpty(), "음수 델타로 발동하면 안 됨");
    }

    @Test
    void 게임_경과_초를_함께_넘긴다() {
        feed(0, 0);
        feed(10, 5);

        // GAME_START 는 T0 의 600초 전이므로 T0+10 은 610초 지점이다
        assertEquals(610, trigger.fired.get(0).gameTimeSeconds());
    }

    @Test
    void 시작_시각을_모르면_경과_초는_비운다() {
        detector.onNewWindowFrame(MATCH, GAME, frame(0, 0), null);
        detector.onNewWindowFrame(MATCH, GAME, frame(10, 5), null);

        assertEquals(1, trigger.fired.size());
        assertEquals(null, trigger.fired.get(0).gameTimeSeconds());
    }

    @Test
    void 게임_상태를_비우면_다시_발동할_수_있다() {
        feed(0, 0);
        feed(10, 5);
        detector.clearGame(GAME);
        feed(20, 0);
        feed(30, 5);

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
            fired.add(new Fired(trigger, externalMatchId, externalGameId, gameTimeSeconds));
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
