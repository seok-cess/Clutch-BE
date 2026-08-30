package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventCreateRequest;
import com.clutch.coupon.event.api.dto.CouponEventCreateResponse;
import com.clutch.coupon.event.api.dto.CouponEventDetailResponse;
import com.clutch.coupon.event.api.dto.CouponEventListResponse;
import com.clutch.coupon.event.api.dto.CouponEventUpdateRequest;
import com.clutch.coupon.event.api.dto.CouponEventUpdateResponse;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.service.CouponEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 쿠폰 이벤트 CRUD API를 제공하는 컨트롤러.
 *
 * <p>경기 트리거 기반 쿠폰 이벤트의 등록, 목록·상세 조회,
 * 설정 수정 및 물리 삭제 요청을 처리한다.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/coupon-events")
@RequiredArgsConstructor
public class CouponEventAdminController {

    private final CouponEventService couponEventService;

    /**
     * 새로운 쿠폰 이벤트를 등록한다.
     *
     * @param request 이벤트 및 쿠폰 단계 설정
     * @return 등록된 쿠폰 이벤트 정보와 HTTP 201 응답
     */
    @PostMapping
    public ResponseEntity<CouponEventCreateResponse> create(
            @Valid @RequestBody CouponEventCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(couponEventService.create(request));
    }

    /**
     * 쿠폰 이벤트 목록을 최신순으로 조회한다.
     *
     * @param status 조회할 이벤트 상태, 전체 조회 시 {@code null}
     * @param page 조회할 페이지 번호, 0부터 시작
     * @param size 한 번에 조회할 이벤트 수
     * @return 이벤트 목록과 번호형 페이지네이션 정보
     */
    @GetMapping
    public CouponEventListResponse findAll(
            @RequestParam(required = false) CouponEventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return couponEventService.findAll(status, page, size);
    }

    /**
     * 쿠폰 이벤트의 설정, 단계별 재고 및 최근 발생 회차를 조회한다.
     *
     * @param couponEventId 조회할 쿠폰 이벤트 ID
     * @return 쿠폰 이벤트 상세 정보
     */
    @GetMapping("/{couponEventId}")
    public CouponEventDetailResponse findById(
            @PathVariable Long couponEventId
    ) {
        return couponEventService.findById(couponEventId);
    }

    /**
     * 대기 상태인 쿠폰 이벤트의 설정과 쿠폰 단계를 수정한다.
     *
     * @param couponEventId 수정할 쿠폰 이벤트 ID
     * @param request 변경할 이벤트 및 쿠폰 단계 설정
     * @return 수정된 쿠폰 이벤트 정보
     */
    @PatchMapping("/{couponEventId}")
    public CouponEventUpdateResponse update(
            @PathVariable Long couponEventId,
            @Valid @RequestBody CouponEventUpdateRequest request
    ) {
        return couponEventService.update(couponEventId, request);
    }

    /**
     * 발생·발급 이력이 없는 대기 상태의 쿠폰 이벤트를 물리 삭제한다.
     *
     * @param couponEventId 삭제할 쿠폰 이벤트 ID
     * @return HTTP 204 응답
     */
    @DeleteMapping("/{couponEventId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long couponEventId
    ) {
        couponEventService.delete(couponEventId);
        return ResponseEntity.noContent().build();
    }
}
