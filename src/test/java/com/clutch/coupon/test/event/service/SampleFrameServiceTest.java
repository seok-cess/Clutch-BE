package com.clutch.coupon.test.event.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTestMatch;
import com.clutch.coupon.test.event.api.dto.SampleFrameRequest;
import com.clutch.lolesports.service.PentakillDetector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시연 프레임이 실제 감지기를 그대로 태우는지 확인한다.
 *
 * <p>화면이 트리거를 지목하던 예전 방식은 감지 로직을 전혀 검증하지 못했다.
 * 여기서는 킬 수만 흘려보내고 판정은 감지기가 하도록 둔다.</p>
 */
class SampleFrameServiceTest {

    private static final String GAME_ID = "sample-757";
    private static final int PENTA_PARTICIPANT = 4;

    /** 발동한 트리거와 그때 넘어간 경기를 기록한다 */
    private record Fired(CouponMatchTrigger trigger, String externalMatchId) {
    }

    private final List<Fired> fired = new ArrayList<>();
    private final SampleFrameService service = new SampleFrameService(
            new PentakillDetector(
                    (trigger, matchId, gameId, gameTime) ->
                            fired.add(new Fired(trigger, matchId))
            )
    );

    /** 참가자 한 명의 누적 킬만 담은 프레임을 보낸다 */
    private void submit(int gameTimeSeconds, int kills) {
        service.submit(new SampleFrameRequest(
                GAME_ID,
                gameTimeSeconds,
                List.of(new SampleFrameRequest.Participant(
                        PENTA_PARTICIPANT, kills
                )),
                List.of()
        ));
    }

    @Test
    void 시간창_안에서_5킬이_쌓이면_감지기가_펜타킬을_발동한다() {
        submit(2170, 4);
        submit(2174, 5);
        submit(2179, 6);
        submit(2184, 7);
        submit(2189, 8);
        submit(2194, 9);

        assertEquals(1, fired.size(), "펜타킬 한 건이 감지되어야 함");
        assertEquals(CouponMatchTrigger.PENTAKILL, fired.get(0).trigger());
    }

    @Test
    void 발동한_트리거는_예약된_테스트_경기로_나간다() {
        submit(2170, 4);
        submit(2174, 5);
        submit(2179, 6);
        submit(2184, 7);
        submit(2189, 8);
        submit(2194, 9);

        assertEquals(
                CouponTestMatch.SAMPLE_EXTERNAL_MATCH_ID,
                fired.get(0).externalMatchId(),
                "요청이 경기를 고르지 못하게 서버가 테스트 경기로 고정해야 함"
        );
    }

    @Test
    void 시간창을_벗어나_쌓인_5킬은_펜타킬이_아니다() {
        // 5킬이 40초에 걸쳐 들어오면 펜타킬로 보지 않는다
        submit(2170, 4);
        submit(2174, 5);
        submit(2184, 6);
        submit(2194, 7);
        submit(2204, 8);
        submit(2214, 9);

        assertTrue(fired.isEmpty(), "30초 창을 넘긴 5킬은 발동하지 않아야 함");
    }

    @Test
    void 되감기_후_초기화하면_다음_바퀴에서_다시_발동한다() {
        submit(2170, 4);
        submit(2174, 5);
        submit(2179, 6);
        submit(2184, 7);
        submit(2189, 8);
        submit(2194, 9);
        assertEquals(1, fired.size());

        // 반복 재생 — 초기화하지 않으면 이미 발동한 참가자로 남아 다시 열리지 않는다
        service.reset(GAME_ID);
        submit(2170, 4);
        submit(2174, 5);
        submit(2179, 6);
        submit(2184, 7);
        submit(2189, 8);
        submit(2194, 9);

        assertEquals(2, fired.size(), "초기화 후에는 다시 발동해야 함");
    }
}
