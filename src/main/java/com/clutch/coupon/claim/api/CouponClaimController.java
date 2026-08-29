package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.CouponClaimCreateResponse;
import com.clutch.coupon.claim.service.CouponClaimApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 발급 요청 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/coupon-events")
@RequiredArgsConstructor
public class CouponClaimController {

    private final CouponClaimApplicationService couponClaimApplicationService;

    /**
     * 쿠폰 발급 요청 생성
     *
     * @param userId 사용자 식별자
     * @param couponEventId 쿠폰 이벤트 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @return 쿠폰 발급 요청 생성 응답
     */
    @PostMapping(
            "/{couponEventId}/occurrences/"
                    + "{couponEventOccurrenceId}/claims"
    )
    public ResponseEntity<CouponClaimCreateResponse> claim(
            @RequestHeader("X-User-Id")
            Long userId,

            @PathVariable
            Long couponEventId,

            @PathVariable
            Long couponEventOccurrenceId
    ) {
        CouponClaimCreateResponse response =
                couponClaimApplicationService.claim(
                        userId,
                        couponEventId,
                        couponEventOccurrenceId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
