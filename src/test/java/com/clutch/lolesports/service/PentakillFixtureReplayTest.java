package com.clutch.lolesports.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTriggerPort;
import com.clutch.lolesports.dto.external.WindowResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * replay fixture 를 감지기에 그대로 흘려 펜타킬이 잡히는지 확인한다.
 *
 * <p>replay 스텁을 띄우고 폴링을 기다리는 방식은 재생 속도에 좌우돼 느리고 불안정하다.
 * 대신 fixture 프레임을 같은 순서로 감지기에 먹여 판정 결과만 검증한다.</p>
 *
 * <p>원본 fixture 에는 펜타킬이 없어야 하고(30초 최대 3킬), 주입 fixture 에는
 * 있어야 한다. 둘 다 확인해야 "주입이 실제로 효과가 있다"가 증명된다.</p>
 */
class PentakillFixtureReplayTest {

    private static final Path ORIGINAL =
            Path.of("replay/fixtures/sample-match-bo3-001/window.jsonl");
    private static final Path INJECTED =
            Path.of("replay/fixtures/sample-match-pentakill/window.jsonl");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** fixture 를 감지기에 순서대로 먹이고 발동 횟수를 센다 */
    private static List<CouponMatchTrigger> replay(Path fixture) throws Exception {
        List<CouponMatchTrigger> fired = new ArrayList<>();
        PentakillDetector detector = new PentakillDetector(
                (trigger, matchId, gameId, gameTime) -> fired.add(trigger)
        );

        try (BufferedReader reader = Files.newBufferedReader(fixture)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode body = MAPPER.readTree(line).get("body");
                if (body == null || body.isNull()) {
                    continue;
                }
                WindowResponse window = MAPPER.treeToValue(body, WindowResponse.class);
                if (window.frames() == null) {
                    continue;
                }
                String gameId = window.esportsGameId();
                for (WindowResponse.Frame frame : window.frames()) {
                    detector.onNewWindowFrame(
                            window.esportsMatchId(), gameId, frame, null
                    );
                }
            }
        }
        return fired;
    }

    @Test
    void 원본_fixture_에서는_펜타킬이_감지되지_않는다() throws Exception {
        // 실제 GEN-KT 녹화에는 펜타킬이 없다. 여기서 잡히면 판정이 헐거운 것이다
        assertTrue(Files.exists(ORIGINAL), "원본 fixture 가 있어야 함: " + ORIGINAL);

        assertTrue(replay(ORIGINAL).isEmpty(), "원본에는 펜타킬이 없어야 함");
    }

    @Test
    void 주입_fixture_에서는_펜타킬이_감지된다() throws Exception {
        assertTrue(Files.exists(INJECTED),
                "주입 fixture 가 있어야 함 — node replay/inject-pentakill.js 로 생성한다: " + INJECTED);

        List<CouponMatchTrigger> fired = replay(INJECTED);

        assertEquals(1, fired.size(), "주입한 펜타킬 한 건이 감지되어야 함");
        assertEquals(CouponMatchTrigger.PENTAKILL, fired.get(0));
    }
}
