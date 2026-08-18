package com.clutch.betting.api;

import com.clutch.betting.service.BettingResultRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
}
