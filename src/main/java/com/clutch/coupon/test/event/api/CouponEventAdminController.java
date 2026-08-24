package com.clutch.coupon.test.event.api;

import com.clutch.coupon.test.event.api.dto.CouponEventActivationResponse;
import com.clutch.coupon.test.event.domain.CouponEventTrigger;
import com.clutch.coupon.test.event.service.CouponEventActivationService;
import com.clutch.coupon.test.event.service.CouponEventTestCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 관리자가 쿠폰 발급을 수동으로 테스트하는 API. */
@RestController("couponTestEventAdminController")
@RequestMapping("/api/v1/admin/coupon-events")
@RequiredArgsConstructor
public class CouponEventAdminController {

    private final CouponEventActivationService activationService;
    private final CouponEventTestCleanupService cleanupService;

    /** 경기 트리거와 무관하게 쿠폰 이벤트 회차를 즉시 연다. */
    @PostMapping("/{couponEventId}/occurrences/manual-open")
    public ResponseEntity<CouponEventActivationResponse> manualOpen(
            @PathVariable Long couponEventId
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(activationService.manualOpen(couponEventId));
    }

    /**
     * 시연·테스트로 생긴 회차와 발급 이력을 지우고 이벤트를 READY 로 되돌린다.
     *
     * 이벤트 정의(항목·단계)는 남긴다 — 같은 설정으로 바로 다시 시연하기 위해서다.
     * 일반 삭제는 이력이 있으면 막히므로 반복 시연에는 쓸 수 없다.
     *
     * @return 테이블별 삭제 건수
     */
    @PostMapping("/{couponEventId}/test-reset")
    public ResponseEntity<Map<String, Integer>> resetForTest(
            @PathVariable Long couponEventId
    ) {
        return ResponseEntity.ok(cleanupService.resetEvent(couponEventId));
    }

    /**
     * 선택 가능한 트리거 종류.
     *
     * 관리자 화면의 드롭다운이 이 목록을 쓴다. 프론트에 값을 복사해두면
     * 트리거가 늘어날 때 양쪽을 고쳐야 하므로 서버가 내려준다.
     */
    @GetMapping("/triggers")
    public ResponseEntity<List<TriggerOption>> triggers() {
        return ResponseEntity.ok(
                Arrays.stream(CouponEventTrigger.values())
                        .map(t -> new TriggerOption(t.name(), t.displayName()))
                        .toList()
        );
    }

    /** 드롭다운 한 항목 — 저장할 값과 화면에 보일 이름 */
    public record TriggerOption(String value, String label) {
    }

    /**
     * 지정한 경기에서 사건이 감지된 것으로 보고 그 트리거의 이벤트를 연다.
     *
     * 실제 라이브 감지는 폴링이 {@code CouponTriggerPort} 로 직접 호출한다.
     * 이 엔드포인트는 관리자 화면의 트리거 시뮬레이션 전용이다 —
     * 실제 감지와 같은 경로(openByTrigger)를 타므로 테스트가 실제와 어긋나지 않는다.
     *
     * 경기 ID 를 필수로 받는다. 없으면 트리거만 보고 아무 경기의 이벤트나 열려
     * 전혀 다른 경기가 발동한다.
     *
     * 같은 사건이 두 번 들어와도 sourceEventKey 가 같아 중복 오픈되지 않는다.
     *
     * @return 열렸으면 201, 조건에 맞는 이벤트가 없거나 이미 열려 있으면 204
     */
    @PostMapping("/occurrences/trigger")
    public ResponseEntity<CouponEventActivationResponse> openByTrigger(
            @RequestParam CouponEventTrigger trigger,
            @RequestParam Long esportsMatchId,
            @RequestParam(required = false) String gameId,
            @RequestParam(required = false) Integer gameTimeSeconds
    ) {
        return activationService
                .openByTrigger(trigger, esportsMatchId, gameId, gameTimeSeconds)
                .map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
