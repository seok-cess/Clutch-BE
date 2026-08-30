package com.clutch.betting.api;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.BetPlacementResult;
import com.clutch.betting.dto.BettingCandidateView;
import com.clutch.betting.dto.BettingEventView;
import com.clutch.betting.dto.MyBetView;
import com.clutch.betting.dto.UserBetView;
import com.clutch.betting.service.BettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BettingController.class)
class BettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BettingService bettingService;

    @Test
    void getsOpenBettingCandidates() throws Exception {
        given(bettingService.findBettingCandidates()).willReturn(List.of(
                new BettingCandidateView(
                        "match-1",
                        "LCK",
                        "week 1",
                        "2026-08-14T10:00:00Z",
                        3,
                        false,
                        null,
                        List.of(new BettingCandidateView.Team(
                                "team-a", "A", "A", null, null, 1, null, null
                        )),
                        List.of(new BettingCandidateView.Game(
                                "game-1", 1, "inProgress", false, null, false
                        )),
                        "game-1"
                )
        ));

        mockMvc.perform(get("/api/betting-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchId").value("match-1"))
                .andExpect(jsonPath("$[0].teams[0].gameWins").value(1))
                .andExpect(jsonPath("$[0].games[0].gameId").value("game-1"));
    }

    @Test
    void recoversVerifiedWinnerAndSettlesEvent() throws Exception {
        mockMvc.perform(put("/api/admin/betting-events/50/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":"team-a"}
                                """))
                .andExpect(status().isNoContent());

        verify(bettingService).recoverWinnerAndSettle(50L, "team-a");
    }

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
                true,
                null
        ));

        mockMvc.perform(get("/api/matches/match-1/betting-events/current")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bettingEventId").value(1))
                .andExpect(jsonPath("$.bettingAvailable").value(true))
                .andExpect(jsonPath("$.closesAt").doesNotExist())
                .andExpect(jsonPath("$.remainingSeconds").doesNotExist());
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
    void getsMyBetHistory() throws Exception {
        given(bettingService.getMyBets(10L)).willReturn(List.of(new MyBetView(
                100L,
                1L,
                "match-1",
                "game-1",
                2,
                "team-a",
                "team-b",
                "A",
                "B",
                "team-a",
                1_000L,
                3_000L,
                2_000L,
                new BigDecimal("3.00"),
                true,
                UserBetStatus.WON,
                BettingEventStatus.SETTLED,
                LocalDateTime.of(2026, 8, 14, 10, 1)
        )));

        mockMvc.perform(get("/api/users/me/bets")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalMatchId").value("match-1"))
                .andExpect(jsonPath("$[0].setNumber").value(2))
                .andExpect(jsonPath("$[0].firstTeamCode").value("A"))
                .andExpect(jsonPath("$[0].secondTeamCode").value("B"))
                .andExpect(jsonPath("$[0].selectedTeamId").value("team-a"))
                .andExpect(jsonPath("$[0].settlementPoint").value(3_000))
                .andExpect(jsonPath("$[0].netPointChange").value(2_000))
                .andExpect(jsonPath("$[0].payoutMultiplier").value(3.0))
                .andExpect(jsonPath("$[0].payoutMultiplierConfirmed").value(true))
                .andExpect(jsonPath("$[0].status").value("WON"));
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

    @Test
    void allowsVitePreflightForBetPlacement() throws Exception {
        mockMvc.perform(options("/api/betting-events/1/bets")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header(
                                "Access-Control-Request-Headers",
                                "Content-Type, X-User-Id"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("X-User-Id")
                ));
    }
}
