package com.clutch.coupon.test.event.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTestMatch;
import com.clutch.coupon.test.event.api.dto.SampleFrameRequest;
import com.clutch.lolesports.service.FirstBloodDetector;
import com.clutch.lolesports.service.PentakillDetector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시연 화면이 실제로 보내는 프레임 흐름으로 첫 킬이 열리는지 확인한다.
 *
 * <p>단위 테스트는 참가자 한둘로 판정 규칙만 본다. 여기서는 샘플 경기가 보내는 모양
 * 그대로 — 양 팀 5명씩, 10초 간격, 경과 초 기준 — 흘려 실제 시연에서 트리거가 걸리는지
 * 검증한다.</p>
 *
 * <p>샘플 경기(2026-08-15 GEN vs T1)는 원본 프레임이 10초 간격이라 첫 한타 3킬이 한
 * 프레임에 뭉쳐 들어왔다. 화면에서 그 구간을 펼쳐 보내도록 고쳤으므로, 펼친 흐름과
 * 뭉친 흐름 양쪽에서 모두 한 번씩만 발동해야 한다.</p>
 */
class SampleFirstBloodScenarioTest {

    private static final String GAME_ID = "sample-scenario-1";

    /** 샘플 경기의 첫 킬 시각 (원본 프레임 t=247s) */
    private static final int FIRST_BLOOD_AT = 247;

    private record Fired(CouponMatchTrigger trigger, String externalMatchId) {
    }

    private final List<Fired> fired = new ArrayList<>();

    private final SampleFrameService service = new SampleFrameService(
            new PentakillDetector(
                    (trigger, matchId, gameId, gameTime) ->
                            fired.add(new Fired(trigger, matchId))
            ),
            new FirstBloodDetector(
                    (trigger, matchId, gameId, gameTime) ->
                            fired.add(new Fired(trigger, matchId))
            )
    );

    /**
     * 팀 킬 합을 참가자에게 나눠 담아 한 프레임을 보낸다.
     *
     * <p>화면은 참가자별 누적 킬만 보낸다. 팀 누적 킬은 서버가 합산하므로,
     * 여기서도 같은 방식으로 첫 번째 참가자에게 몰아 담는다.</p>
     */
    private void submit(int gameTimeSeconds, int blueKills, int redKills) {
        service.submit(new SampleFrameRequest(
                GAME_ID,
                gameTimeSeconds,
                roster(1, blueKills),
                roster(6, redKills)
        ));
    }

    /** 참가자 5명. 킬은 첫 번째 선수에게 몰아 담는다 */
    private static List<SampleFrameRequest.Participant> roster(
            int firstParticipantId, int kills
    ) {
        List<SampleFrameRequest.Participant> participants = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            participants.add(new SampleFrameRequest.Participant(
                    firstParticipantId + i,
                    i == 0 ? kills : 0
            ));
        }
        return participants;
    }

    @Test
    void 샘플_첫_한타에서_첫_킬이_한_번_열린다() {
        // 화면이 첫 한타를 펼쳐 보내는 흐름 (240s red 1킬 → 244s blue 1 → 247s blue 2)
        submit(230, 0, 0);
        submit(240, 0, 1);
        submit(244, 1, 1);
        submit(FIRST_BLOOD_AT, 2, 1);

        assertEquals(1, fired.size(), "첫 킬 한 건만 열려야 함");
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.get(0).trigger());
        assertEquals(
                CouponTestMatch.SAMPLE_EXTERNAL_MATCH_ID,
                fired.get(0).externalMatchId(),
                "예약된 테스트 경기로 열려야 함 — 실제 경기 쿠폰이 열리면 안 됨"
        );
    }

    @Test
    void 뭉쳐서_들어와도_첫_킬이_한_번_열린다() {
        // 화면 수정 전처럼 0킬에서 곧장 3킬로 뛰는 경우.
        // 첫 킬은 0 에서 벗어나는 전이라 뭉침에 강해야 한다
        submit(237, 0, 0);
        submit(FIRST_BLOOD_AT, 2, 1);

        assertEquals(1, fired.size(), "뭉쳐 들어와도 첫 킬은 열려야 함");
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.get(0).trigger());
    }

    @Test
    void 경기가_진행돼도_첫_킬은_다시_열리지_않는다() {
        submit(230, 0, 0);
        submit(240, 0, 1);
        submit(FIRST_BLOOD_AT, 2, 1);

        // 이후 경기가 계속 진행된다 (샘플 최종 스코어는 blue 23 / red 13)
        submit(600, 6, 4);
        submit(1200, 12, 8);
        submit(2606, 23, 13);

        assertEquals(1, fired.size(),
                "세트 내내 첫 킬은 한 번뿐이어야 함 — 발동: " + fired);
    }

    @Test
    void 시연을_다시_재생하면_첫_킬이_다시_열린다() {
        submit(230, 0, 0);
        submit(240, 0, 1);
        assertEquals(1, fired.size());

        // 처음부터 다시 재생 — 감지 상태를 버리지 않으면 다음 바퀴에서 영영 안 열린다
        service.reset(GAME_ID);
        fired.clear();

        submit(230, 0, 0);
        submit(240, 0, 1);

        assertEquals(1, fired.size(), "재생 후에는 다시 열려야 함");
        assertEquals(CouponMatchTrigger.FIRST_BLOOD, fired.get(0).trigger());
    }

    @Test
    void 첫_킬_전에는_아무것도_열리지_않는다() {
        submit(0, 0, 0);
        submit(60, 0, 0);
        submit(120, 0, 0);
        submit(230, 0, 0);

        assertTrue(fired.isEmpty(), "킬이 나기 전에는 열리면 안 됨");
    }
}
