package com.clutch.lolesports.api;

import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceStatus;
import com.clutch.lolesports.source.ExternalSourceSwitchException;
import com.clutch.lolesports.source.ExternalSourceSwitchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalSourceController.class)
@TestPropertySource(properties = "external-source.enabled=true")
class ExternalSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalSourceSwitchService sourceSwitchService;

    @Test
    void 현재_외부_소스를_반환한다() throws Exception {
        given(sourceSwitchService.currentStatus()).willReturn(new ExternalSourceStatus(ExternalSourceMode.REAL));

        mockMvc.perform(get("/api/operator/external-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REAL"));
    }

    @Test
    void 요청한_외부_소스로_전환한다() throws Exception {
        given(sourceSwitchService.switchTo(ExternalSourceMode.STUB))
                .willReturn(new ExternalSourceStatus(ExternalSourceMode.STUB));

        mockMvc.perform(put("/api/operator/external-source")
                        .contentType("application/json")
                        .content("{\"mode\":\"STUB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("STUB"));
    }

    @Test
    void vite에서_보낸_put_사전_요청을_허용한다() throws Exception {
        mockMvc.perform(options("/api/operator/external-source")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PUT")
                ));
    }

    @Test
    void replay_서버가_없으면_스텁_전환을_거절한다() throws Exception {
        given(sourceSwitchService.switchTo(ExternalSourceMode.STUB))
                .willThrow(new ExternalSourceSwitchException("상태 확인 실패", null));

        mockMvc.perform(put("/api/operator/external-source")
                        .contentType("application/json")
                        .content("{\"mode\":\"STUB\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("상태 확인 실패"));
    }
}
