package com.clutch.coupon.test.event.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.test.event.api.dto.SampleFrameRequest;
import com.clutch.lolesports.service.FirstBloodDetector;
import com.clutch.lolesports.service.PentakillDetector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시연 화면이 프레임을 흘려보내는 방식 그대로 재생해 트리거가 열리는지 확인한다.
 *
 * <p>화면은 트리거를 지목하지 않고 10초 간격으로 참가자별 누적 킬만 보낸다. 이 테스트는
 * 그 전송 순서를 그대로 재현해, 서버 감지기만으로 첫 킬과 펜타킬이 열리는지 본다.</p>
 *
 * <p>여기서 쓰는 킬 수치는 프론트 {@code sampleMatch.js} 가 같은 시각에 만들어내는 값이다.
 * 화면 계산이 바뀌면 이 값도 함께 바뀌어야 한다.</p>
 */
class SamplePlaybackScenarioTest {

    private static final String GAME_ID = "sample-playback";

    /** 화면이 프레임을 보내는 주기 (게임 내 초) */
    private static final int FRAME_INTERVAL = 10;

    private record Fired(CouponMatchTrigger trigger, Integer gameTimeSeconds) {
    }

    private final List<Fired> fired = new ArrayList<>();

    private final SampleFrameService service = new SampleFrameService(
            new PentakillDetector(
                    (trigger, matchId, gameId, gameTime) ->
                            fired.add(new Fired(trigger, gameTime))
            ),
            new FirstBloodDetector(
                    (trigger, matchId, gameId, gameTime) ->
                            fired.add(new Fired(trigger, gameTime))
            )
    );

    /**
     * 한 프레임을 보낸다.
     *
     * @param blueKills 블루팀 참가자별 누적 킬 (1~5번)
     * @param redKills 레드팀 참가자별 누적 킬 (6~10번)
     */
    private void submit(int gameTimeSeconds, int[] blueKills, int[] redKills) {
        service.submit(new SampleFrameRequest(
                GAME_ID,
                gameTimeSeconds,
                roster(1, blueKills),
                roster(6, redKills)
        ));
    }

    private static List<SampleFrameRequest.Participant> roster(
            int firstParticipantId, int[] kills
    ) {
        List<SampleFrameRequest.Participant> participants = new ArrayList<>();
        for (int i = 0; i < kills.length; i++) {
            participants.add(new SampleFrameRequest.Participant(
                    firstParticipantId + i, kills[i]
            ));
        }
        return participants;
    }

    /** 킬이 없는 팀 */
    private static int[] noKills() {
        return new int[]{0, 0, 0, 0, 0};
    }

    @Test
    void 재생_초반에_첫_킬이_열린다() {
        // 화면이 첫 한타를 10초 버킷으로 펼쳐 보낸다 (t=240 에 레드 1킬)
        for (int t = 0; t <= 230; t += FRAME_INTERVAL) {
            submit(t, noKills(), noKills());
        }
        submit(240, noKills(), new int[]{1, 0, 0, 0, 0});

        assertEquals(1, fired.size(), "첫 킬 한 건이 열려야 함");
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.get(0).trigger());
        assertEquals(240, fired.get(0).gameTimeSeconds());
    }

    @Test
    void 첫_킬_전에는_아무것도_열리지_않는다() {
        for (int t = 0; t <= 230; t += FRAME_INTERVAL) {
            submit(t, noKills(), noKills());
        }

        assertTrue(fired.isEmpty(), "킬이 나기 전에는 열리면 안 됨");
    }

    @Test
    void 펜타킬_구간에서_펜타킬이_열린다() {
        // 기준 프레임 — 4번 선수가 이미 7킬 (펜타킬 5킬 전)
        submit(2170, new int[]{0, 0, 0, 7, 0}, noKills());

        // 화면이 만드는 값: 2180→9, 2190→11, 2200→12
        // 펜타킬 5킬이 20초(2174~2194)에 몰려 있어 30초 창 안에 다 들어온다
        submit(2180, new int[]{0, 0, 0, 9, 0}, noKills());
        submit(2190, new int[]{0, 0, 0, 11, 0}, noKills());
        submit(2200, new int[]{0, 0, 0, 12, 0}, noKills());

        assertTrue(
                fired.stream().anyMatch(
                        f -> f.trigger() == CouponMatchTrigger.PENTAKILL),
                "펜타킬이 열려야 함 — 발동: " + fired
        );
    }

    @Test
    void 기준_프레임_없이_킬부터_들어오면_열리지_않는다() {
        // 배속이 높거나 재생 위치를 옮겨 앞 구간을 건너뛰면 이런 순서가 된다.
        // 감지기는 첫 관측을 기준으로만 삼으므로, 0킬 프레임 없이 킬이 있는
        // 프레임부터 받으면 "이미 지나간 첫 킬" 로 보고 아무것도 열지 않는다.
        submit(250, new int[]{2, 0, 0, 0, 0}, new int[]{1, 0, 0, 0, 0});

        assertTrue(fired.isEmpty(), "기준 없이 들어온 킬로는 열리지 않는다");
    }

    @Test
    void 건너뛴_구간을_채워_보내면_열린다() {
        // 화면은 마지막 구간만 보내지 않고 그 사이 구간을 모두 채워 보낸다.
        // 그래야 0킬 기준 프레임이 서버에 도착해 첫 킬이 판정된다.
        for (int t = 0; t <= 230; t += FRAME_INTERVAL) {
            submit(t, noKills(), noKills());
        }
        submit(240, noKills(), new int[]{1, 0, 0, 0, 0});

        assertEquals(1, fired.size(), "채워 보내면 첫 킬이 열려야 함");
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.get(0).trigger());
    }

    @Test
    void 반복_재생하면_감지_상태를_비워_다시_열린다() {
        for (int t = 0; t <= 230; t += FRAME_INTERVAL) {
            submit(t, noKills(), noKills());
        }
        submit(240, noKills(), new int[]{1, 0, 0, 0, 0});
        assertEquals(1, fired.size());

        // 화면이 되감기를 감지해 reset 을 부른다
        service.reset(GAME_ID);
        fired.clear();

        for (int t = 0; t <= 230; t += FRAME_INTERVAL) {
            submit(t, noKills(), noKills());
        }
        submit(240, noKills(), new int[]{1, 0, 0, 0, 0});

        assertEquals(1, fired.size(), "다음 바퀴에서도 첫 킬이 열려야 함");
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.get(0).trigger());
    }

    @Test
    void 되감지_않고_계속_재생하면_첫_킬은_한_번뿐이다() {
        for (int t = 0; t <= 230; t += FRAME_INTERVAL) {
            submit(t, noKills(), noKills());
        }
        submit(240, noKills(), new int[]{1, 0, 0, 0, 0});
        submit(250, new int[]{2, 0, 0, 0, 0}, new int[]{1, 0, 0, 0, 0});
        submit(300, new int[]{3, 0, 0, 0, 0}, new int[]{2, 0, 0, 0, 0});

        long firstBloods = fired.stream()
                .filter(f -> f.trigger() == CouponMatchTrigger.FIRST_BLOOD)
                .count();
        assertEquals(1, firstBloods, "첫 킬은 세트당 한 번뿐이어야 함");
    }
}
