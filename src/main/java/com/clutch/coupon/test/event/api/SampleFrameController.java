package com.clutch.coupon.test.event.api;

import com.clutch.coupon.test.event.api.dto.SampleFrameRequest;
import com.clutch.coupon.test.event.service.SampleFrameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시연 화면이 만든 경기 프레임을 받는 API.
 *
 * <p>시연 화면은 실제 피드가 없어도 동작해야 하므로 지표를 스스로 만든다.
 * 그 값을 감지기에 넣어 주면 트리거 판정만은 실제 경로와 같아진다.</p>
 */
@RestController
@RequestMapping("/api/v1/test/sample-frames")
@RequiredArgsConstructor
public class SampleFrameController {

    private final SampleFrameService sampleFrameService;

    /**
     * 프레임 한 장을 감지기에 전달한다.
     *
     * <p>트리거를 지목하지 않는다 — 펜타킬인지는 감지기가 판단한다. 그래서 응답은
     * 항상 204 이며, 쿠폰이 열렸는지는 활성 회차 조회로 확인한다.</p>
     */
    @PostMapping
    public ResponseEntity<Void> submit(
            @Valid @RequestBody SampleFrameRequest request
    ) {
        sampleFrameService.submit(request);
        return ResponseEntity.noContent().build();
    }

    /** 시연을 처음부터 다시 재생할 때 감지 상태를 비운다. */
    @DeleteMapping
    public ResponseEntity<Void> reset(@RequestParam String gameId) {
        sampleFrameService.reset(gameId);
        return ResponseEntity.noContent().build();
    }
}
