package com.clutch.betting.api;

import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.service.BettingResultRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BettingAdminController.class)
class BettingAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BettingResultRecoveryService recoveryService;

    @Test
    void recoversVerifiedWinnerAndSettlesEvent() throws Exception {
        mockMvc.perform(put("/api/admin/betting-events/50/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":"team-a"}
                                """))
                .andExpect(status().isNoContent());

        verify(recoveryService).recoverAndSettle(50L, "team-a");
    }

    @Test
    void rejectsBlankWinnerTeamId() throws Exception {
        mockMvc.perform(put("/api/admin/betting-events/50/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(recoveryService, never()).recoverAndSettle(anyLong(), anyString());
    }

    @Test
    void rejectsMissingWinnerTeamId() throws Exception {
        mockMvc.perform(put("/api/admin/betting-events/50/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(recoveryService, never()).recoverAndSettle(anyLong(), anyString());
    }

    @Test
    void rejectsNonPositiveBettingEventId() throws Exception {
        mockMvc.perform(put("/api/admin/betting-events/0/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":"team-a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(recoveryService, never()).recoverAndSettle(anyLong(), anyString());
    }

    @Test
    void mapsWinnerAlreadyDecidedExceptionToConflict() throws Exception {
        willThrow(new BettingException(BettingErrorCode.WINNER_ALREADY_DECIDED))
                .given(recoveryService).recoverAndSettle(50L, "team-b");

        mockMvc.perform(put("/api/admin/betting-events/50/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":"team-b"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WINNER_ALREADY_DECIDED"));
    }

    @Test
    void mapsEventNotFoundExceptionToNotFound() throws Exception {
        willThrow(new BettingException(BettingErrorCode.EVENT_NOT_FOUND))
                .given(recoveryService).recoverAndSettle(999L, "team-a");

        mockMvc.perform(put("/api/admin/betting-events/999/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":"team-a"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void mapsResultNotReadyExceptionToBadRequest() throws Exception {
        willThrow(new BettingException(BettingErrorCode.RESULT_NOT_READY))
                .given(recoveryService).recoverAndSettle(50L, "team-a");

        mockMvc.perform(put("/api/admin/betting-events/50/winner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"winnerTeamId":"team-a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
    }
}
