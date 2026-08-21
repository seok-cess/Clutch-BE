package com.clutch.coupon.type.api;

import com.clutch.coupon.type.api.dto.CouponTypeCreateRequest;
import com.clutch.coupon.type.api.dto.CouponTypeListResponse;
import com.clutch.coupon.type.api.dto.CouponTypeOptionListResponse;
import com.clutch.coupon.type.api.dto.CouponTypeResponse;
import com.clutch.coupon.type.api.dto.CouponTypeStatusUpdateRequest;
import com.clutch.coupon.type.api.dto.CouponTypeUpdateRequest;
import com.clutch.coupon.type.domain.CouponTypeStatus;
import com.clutch.coupon.type.service.CouponTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 쿠폰 종류 CRUD API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/admin/coupon-types")
@RequiredArgsConstructor
public class CouponTypeAdminController {

    private final CouponTypeService couponTypeService;

    /**
     * 새로운 쿠폰 종류를 활성 상태로 등록한다.
     *
     * @param request 쿠폰 이름과 할인 혜택 정보
     * @return 생성된 쿠폰 종류와 HTTP 201 응답
     */
    @PostMapping
    public ResponseEntity<CouponTypeResponse> create(
            @Valid @RequestBody CouponTypeCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(couponTypeService.create(request));
    }

    /**
     * 쿠폰 종류를 최신순으로 조회한다.
     *
     * @param status 조회할 상태, 전체 조회 시 {@code null}
     * @param cursor 이전 페이지의 마지막 쿠폰 종류 ID, 첫 조회 시 {@code null}
     * @param size 한 번에 조회할 쿠폰 종류 수
     * @return 조건에 맞는 쿠폰 종류와 다음 커서 정보
     */
    @GetMapping
    public CouponTypeListResponse findAll(
            @RequestParam(required = false) CouponTypeStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return couponTypeService.findAll(status, cursor, size);
    }

    /**
     * 이벤트 생성 화면에서 선택할 활성 쿠폰 종류를 조회한다.
     *
     * @param keyword 쿠폰 이름 검색어, 전체 활성 목록 조회 시 {@code null}
     * @param cursor 이전 페이지의 마지막 쿠폰 종류 ID, 첫 조회 시 {@code null}
     * @param size 한 번에 조회할 선택 항목 수
     * @return 활성 쿠폰 종류 선택 항목과 다음 커서 정보
     */
    @GetMapping("/options")
    public CouponTypeOptionListResponse findOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return couponTypeService.findOptions(keyword, cursor, size);
    }

    /**
     * 식별자로 쿠폰 종류의 상세 정보와 이벤트 사용 여부를 조회한다.
     *
     * @param couponTypeId 쿠폰 종류 ID
     * @return 쿠폰 종류 상세 정보
     */
    @GetMapping("/{couponTypeId}")
    public CouponTypeResponse findById(
            @PathVariable Long couponTypeId
    ) {
        return couponTypeService.findById(couponTypeId);
    }

    /**
     * 아직 이벤트에서 사용되지 않은 쿠폰 종류의 혜택 정의를 수정한다.
     *
     * @param couponTypeId 수정할 쿠폰 종류 ID
     * @param request 변경할 이름과 할인 혜택 정보
     * @return 수정된 쿠폰 종류
     */
    @PatchMapping("/{couponTypeId}")
    public CouponTypeResponse update(
            @PathVariable Long couponTypeId,
            @Valid @RequestBody CouponTypeUpdateRequest request
    ) {
        return couponTypeService.update(couponTypeId, request);
    }

    /**
     * 쿠폰 종류의 신규 이벤트 선택 가능 상태를 변경한다.
     *
     * @param couponTypeId 상태를 변경할 쿠폰 종류 ID
     * @param request 변경할 상태
     * @return 상태가 변경된 쿠폰 종류
     */
    @PatchMapping("/{couponTypeId}/status")
    public CouponTypeResponse changeStatus(
            @PathVariable Long couponTypeId,
            @Valid @RequestBody CouponTypeStatusUpdateRequest request
    ) {
        return couponTypeService.changeStatus(couponTypeId, request.status());
    }

    /**
     * 이벤트에서 사용되지 않은 쿠폰 종류를 물리 삭제한다.
     *
     * @param couponTypeId 삭제할 쿠폰 종류 ID
     * @return 본문이 없는 HTTP 204 응답
     */
    @DeleteMapping("/{couponTypeId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long couponTypeId
    ) {
        couponTypeService.delete(couponTypeId);
        return ResponseEntity.noContent().build();
    }
}
