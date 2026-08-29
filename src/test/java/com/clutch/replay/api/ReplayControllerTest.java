package com.clutch.replay.api;

import com.clutch.replay.service.ReplayControlService;
import com.clutch.replay.service.ReplayMatchResult;
import com.clutch.replay.service.ReplayStartResult;
import com.clutch.replay.service.ReplaySourceModeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReplayController.class)
@ActiveProfiles("operator-routing")
class ReplayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReplayControlService replayControlService;

    @Test
    void startsNewReplayRun() throws Exception {
        given(replayControlService.start()).willReturn(new ReplayStartResult(
                "a8f31c",
                List.of(new ReplayMatchResult(
                        "replay-a8f31c-m1",
                        321L,
                        List.of("replay-a8f31c-g1", "replay-a8f31c-g2")
                ), new ReplayMatchResult(
                        "replay-a8f31c-m2",
                        322L,
                        List.of("replay-a8f31c-g3")
                ))
        ));

        mockMvc.perform(post("/api/replay/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("a8f31c"))
                .andExpect(jsonPath("$.matches[0].matchId").value("replay-a8f31c-m1"))
                .andExpect(jsonPath("$.matches[0].gameIds[0]").value("replay-a8f31c-g1"))
                .andExpect(jsonPath("$.matches[1].matchId").value("replay-a8f31c-m2"));
    }

    @Test
    void 실제_소스에서는_test_경기를_시작할_수_없다() throws Exception {
        given(replayControlService.start()).willThrow(new ReplaySourceModeException());

        mockMvc.perform(post("/api/replay/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STUB 소스 모드에서만 test 경기를 시작할 수 있다"));
    }

    @Test
    void returnsReplayTimelinePosition() throws Exception {
        given(replayControlService.status()).willReturn(new com.clutch.replay.service.ReplayStatusResult(
                "a8f31c",
                List.of(new ReplayMatchResult("replay-a8f31c-m1", 321L, List.of("replay-a8f31c-g1"))),
                1350,
                8400,
                16.1,
                "2026-01-01T00:22:30Z",
                3.0
        ));

        mockMvc.perform(get("/api/replay/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].esportsMatchId").value(321))
                .andExpect(jsonPath("$.elapsedSeconds").value(1350))
                .andExpect(jsonPath("$.totalSeconds").value(8400))
                .andExpect(jsonPath("$.progressPercent").value(16.1));
    }

    @Test
    void changesReplaySpeedWithoutRestartingRun() throws Exception {
        given(replayControlService.changeSpeed(5.0)).willReturn(new com.clutch.replay.service.ReplayStatusResult(
                "a8f31c",
                List.of(new ReplayMatchResult("replay-a8f31c-m1", 321L, List.of("replay-a8f31c-g1"))),
                1350,
                8400,
                16.1,
                "2026-01-01T00:22:30Z",
                5.0
        ));

        mockMvc.perform(post("/api/replay/speed").param("value", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speed").value(5.0));
    }
}
