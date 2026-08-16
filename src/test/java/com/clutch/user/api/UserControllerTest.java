package com.clutch.user.api;

import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    void getsCurrentPoint() throws Exception {
        given(userQueryService.getPoint(10L)).willReturn(12_000L);

        mockMvc.perform(get("/api/users/me/points")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.point").value(12000));
    }

    @Test
    void returnsNotFoundForUnknownUser() throws Exception {
        given(userQueryService.getPoint(99L)).willThrow(new UserNotFoundException());

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
}
