package com.clutch.lolesports.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.lolesports.dto.external.WindowResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * replay fixture 를 감지기에 그대로 흘려 첫 킬이 잡히는지 확인한다.
 *
 * <p>단위 테스트는 우리가 만든 프레임으로 판정 규칙만 검증한다. 여기서는 실제 녹화
 * 프레임을 먹여 두 가지를 확인한다 — {@code totalKills} 가 실제로 채워져 들어오는지,
 * 그리고 세트마다 정확히 한 번씩만 발동하는지.</p>
 *
 * <p>펜타킬과 달리 주입 fixture 가 필요 없다. 첫 킬은 모든 경기에 반드시 있으므로
 * 원본 녹화만으로 검증된다.</p>
 */
class FirstBloodFixtureReplayTest {

    private static final Path ORIGINAL =
            Path.of("replay/fixtures/sample-match-bo3-001/window.jsonl");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 한 번의 발동 기록 */
    private record Fired(String gameId, CouponMatchTrigger trigger) {
    }

    /** fixture 를 감지기에 순서대로 먹이고 발동 내역을 모은다 */
    private static List<Fired> replay(Path fixture) throws Exception {
        List<Fired> fired = new ArrayList<>();
        FirstBloodDetector detector = new FirstBloodDetector(
                (trigger, matchId, gameId, gameTime) ->
                        fired.add(new Fired(gameId, trigger))
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
    void 실제_녹화에서_첫_킬이_감지된다() throws Exception {
        assertTrue(Files.exists(ORIGINAL), "원본 fixture 가 있어야 함: " + ORIGINAL);

        List<Fired> fired = replay(ORIGINAL);

        assertTrue(!fired.isEmpty(), "실제 경기에는 첫 킬이 반드시 있어야 함");
        fired.forEach(f -> assertEquals(
                CouponMatchTrigger.FIRST_BLOOD, f.trigger(),
                "첫 킬 외의 트리거가 발동하면 안 됨"));
    }

    @Test
    void 세트마다_한_번씩만_발동한다() throws Exception {
        assertTrue(Files.exists(ORIGINAL), "원본 fixture 가 있어야 함: " + ORIGINAL);

        List<Fired> fired = replay(ORIGINAL);
        Set<String> games = new LinkedHashSet<>(fired.stream().map(Fired::gameId).toList());

        assertEquals(games.size(), fired.size(),
                "세트당 첫 킬은 한 번뿐이어야 함 — 발동: " + fired);
    }
}
