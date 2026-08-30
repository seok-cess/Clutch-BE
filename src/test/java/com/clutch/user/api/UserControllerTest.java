package com.clutch.user.api;

import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.dto.PointRanking;
import com.clutch.user.dto.UserPointSummary;
import com.clutch.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getsCurrentPoint() throws Exception {
        given(userService.getPoint(10L)).willReturn(12_000L);

        mockMvc.perform(get("/api/users/me/points")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.point").value(12000));
    }

    @Test
    void returnsNotFoundForUnknownUser() throws Exception {
        given(userService.getPoint(99L)).willThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/users/me/points")
                        .header("X-User-Id", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void requiresCurrentUserHeader() throws Exception {
        mockMvc.perform(get("/api/users/me/points"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getsCurrentUserPointSummary() throws Exception {
        given(userService.getPointSummary(10L)).willReturn(new UserPointSummary(
                12_450L,
                26L,
                15L,
                3_600L
        ));

        mockMvc.perform(get("/api/users/me/point-summary")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(12450))
                .andExpect(jsonPath("$.predictionCount").value(26))
                .andExpect(jsonPath("$.predictionSuccessCount").value(15))
                .andExpect(jsonPath("$.maxEarnedPoint").value(3600));
    }

    @Test
    void getsTopTenPointRankings() throws Exception {
        given(userService.getPointRankings()).willReturn(List.of(
                new PointRanking(1, "김*정", 48_200L),
                new PointRanking(2, "이*", 41_500L)
        ));

        mockMvc.perform(get("/api/users/point-rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].displayName").value("김*정"))
                .andExpect(jsonPath("$[0].point").value(48200))
                .andExpect(jsonPath("$[1].rank").value(2));
    }
}
