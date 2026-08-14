package com.clutch.betting.api;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.BetPlacementResult;
import com.clutch.betting.dto.BettingEventView;
import com.clutch.betting.dto.UserBetView;
import com.clutch.betting.service.BettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BettingController.class)
class BettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BettingService bettingService;

    @Test
    void getsCurrentBettingEvent() throws Exception {
        given(bettingService.getCurrentEvent("match-1", 10L)).willReturn(new BettingEventView(
                1L,
                "match-1",
                "game-1",
                1,
                "team-a",
                "team-b",
                BettingEventStatus.OPEN,
                LocalDateTime.of(2026, 8, 14, 10, 2),
                60L,
                true,
                null
        ));

        mockMvc.perform(get("/api/matches/match-1/betting-events/current")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bettingEventId").value(1))
                .andExpect(jsonPath("$.bettingAvailable").value(true));
    }

    @Test
    void placesBet() throws Exception {
        given(bettingService.place(10L, 1L, "team-a", 1_000L)).willReturn(
                new BetPlacementResult(
                        100L,
                        10L,
                        1L,
                        "team-a",
                        1_000L,
                        UserBetStatus.PLACED,
                        9_000L
                )
        );

        mockMvc.perform(post("/api/betting-events/1/bets")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectedTeamId":"team-a","amount":1000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userBetId").value(100))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.remainingPoint").value(9000));
    }

    @Test
    void getsMyBetResult() throws Exception {
        given(bettingService.getMyBet(1L, 10L)).willReturn(new UserBetView(
                100L,
                10L,
                1L,
                "team-a",
                1_000L,
                UserBetStatus.WON,
                11_000L
        ));

        mockMvc.perform(get("/api/betting-events/1/bets/me")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.status").value("WON"))
                .andExpect(jsonPath("$.currentPoint").value(11000));
    }

    @Test
    void rejectsInvalidBetAmount() throws Exception {
        mockMvc.perform(post("/api/betting-events/1/bets")
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectedTeamId":"team-a","amount":999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void requiresCurrentUserHeader() throws Exception {
        mockMvc.perform(get("/api/matches/match-1/betting-events/current"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsNonPositiveCurrentUserHeader() throws Exception {
        mockMvc.perform(get("/api/matches/match-1/betting-events/current")
                        .header("X-User-Id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
