package com.clutch.coupon.type.service;

import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.type.api.dto.CouponTypeCreateRequest;
import com.clutch.coupon.type.api.dto.CouponTypeListResponse;
import com.clutch.coupon.type.api.dto.CouponTypeOptionListResponse;
import com.clutch.coupon.type.api.dto.CouponTypeOptionResponse;
import com.clutch.coupon.type.api.dto.CouponTypeResponse;
import com.clutch.coupon.type.api.dto.CouponTypeUpdateRequest;
import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.coupon.type.domain.CouponType;
import com.clutch.coupon.type.domain.CouponTypeStatus;
import com.clutch.coupon.type.exception.CouponTypeErrorCode;
import com.clutch.coupon.type.exception.CouponTypeException;
import com.clutch.coupon.type.repository.CouponTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 관리자 쿠폰 종류의 등록, 조회, 수정, 상태 변경 및 삭제를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class CouponTypeService {

    private final CouponTypeRepository couponTypeRepository;
    private final CouponEventItemRepository couponEventItemRepository;

    /**
     * 활성 상태의 쿠폰 종류를 등록한다.
     *
     * @param request 쿠폰 이름과 할인 혜택
     * @return 등록된 쿠폰 종류
     */
    @Transactional
    public CouponTypeResponse create(CouponTypeCreateRequest request) {
        CouponType couponType = createDomain(
                request.couponName(),
                request.discountType(),
                request.discountValue()
        );
        CouponType savedCouponType = couponTypeRepository.save(couponType);
        return toResponse(savedCouponType, false);
    }

    /**
     * 상태 조건에 맞는 쿠폰 종류를 최신순으로 조회한다.
     *
     * @param status 조회할 상태, 전체 조회 시 {@code null}
     * @return 쿠폰 종류 목록
     */
    @Transactional(readOnly = true)
    public CouponTypeListResponse findAll(
            CouponTypeStatus status,
            Long cursor,
            int size
    ) {
        validateListCondition(cursor, size);
        Slice<CouponType> couponTypeSlice = findCouponTypeSlice(
                status,
                cursor,
                PageRequest.of(0, size)
        );
        List<CouponType> couponTypes = couponTypeSlice.getContent();
        Set<Long> usedCouponTypeIds = couponTypes.isEmpty()
                ? Set.of()
                : couponEventItemRepository.findUsedCouponTypeIds(
                        couponTypes.stream().map(CouponType::getId).toList()
                );

        List<CouponTypeResponse> responses = couponTypes.stream()
                .map(couponType -> toResponse(
                        couponType,
                        usedCouponTypeIds.contains(couponType.getId())
                ))
                .toList();
        return new CouponTypeListResponse(
                responses,
                nextCursor(couponTypeSlice, couponTypes),
                couponTypeSlice.hasNext()
        );
    }

    /**
     * 이벤트 생성 화면에서 선택할 활성 쿠폰 종류를 검색한다.
     *
     * @param keyword 쿠폰 이름 검색어
     * @param cursor 이 값보다 작은 ID를 조회하며 첫 조회 시 {@code null}
     * @param size 조회할 선택 항목 수, 1 이상 100 이하
     * @return 활성 쿠폰 종류 선택 항목과 다음 커서 정보
     */
    @Transactional(readOnly = true)
    public CouponTypeOptionListResponse findOptions(
            String keyword,
            Long cursor,
            int size
    ) {
        validateListCondition(cursor, size);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        PageRequest pageable = PageRequest.of(0, size);
        Slice<CouponType> couponTypeSlice;
        if (normalizedKeyword.isEmpty()) {
            couponTypeSlice = cursor == null
                    ? couponTypeRepository.findByStatusOrderByIdDesc(
                            CouponTypeStatus.ACTIVE,
                            pageable
                    )
                    : couponTypeRepository
                            .findByStatusAndIdLessThanOrderByIdDesc(
                                    CouponTypeStatus.ACTIVE,
                                    cursor,
                                    pageable
                            );
        } else {
            couponTypeSlice = cursor == null
                    ? couponTypeRepository
                            .findByStatusAndCouponNameContainingIgnoreCaseOrderByIdDesc(
                                    CouponTypeStatus.ACTIVE,
                                    normalizedKeyword,
                                    pageable
                            )
                    : couponTypeRepository
                            .findByStatusAndCouponNameContainingIgnoreCaseAndIdLessThanOrderByIdDesc(
                                    CouponTypeStatus.ACTIVE,
                                    normalizedKeyword,
                                    cursor,
                                    pageable
                            );
        }

        List<CouponType> couponTypes = couponTypeSlice.getContent();
        List<CouponTypeOptionResponse> options = couponTypes.stream()
                .map(this::toOptionResponse)
                .toList();
        return new CouponTypeOptionListResponse(
                options,
                nextCursor(couponTypeSlice, couponTypes),
                couponTypeSlice.hasNext()
        );
    }

    /**
     * 쿠폰 종류의 혜택과 사용 여부를 조회한다.
     *
     * @param couponTypeId 조회할 쿠폰 종류 ID
     * @return 쿠폰 종류 상세 정보
     */
    @Transactional(readOnly = true)
    public CouponTypeResponse findById(Long couponTypeId) {
        CouponType couponType = findCouponType(couponTypeId);
        return toResponse(
                couponType,
                couponEventItemRepository.existsByCouponTypeId(couponTypeId)
        );
    }

    /**
     * 이벤트에서 사용되지 않은 쿠폰 종류의 혜택을 수정한다.
     *
     * @param couponTypeId 수정할 쿠폰 종류 ID
     * @param request 변경할 쿠폰 이름과 할인 혜택
     * @return 수정된 쿠폰 종류
     */
    @Transactional
    public CouponTypeResponse update(
            Long couponTypeId,
            CouponTypeUpdateRequest request
    ) {
        CouponType couponType = findCouponType(couponTypeId);
        if (couponEventItemRepository.existsByCouponTypeId(couponTypeId)) {
            throw new CouponTypeException(
                    CouponTypeErrorCode.COUPON_TYPE_NOT_EDITABLE
            );
        }

        try {
            couponType.updateDefinition(
                    request.couponName(),
                    request.discountType(),
                    request.discountValue()
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }

        CouponType savedCouponType = couponTypeRepository.saveAndFlush(
                couponType
        );
        return toResponse(savedCouponType, false);
    }

    /**
     * 쿠폰 종류의 신규 이벤트 선택 가능 상태를 변경한다.
     *
     * @param couponTypeId 상태를 변경할 쿠폰 종류 ID
     * @param status 변경할 상태
     * @return 상태가 변경된 쿠폰 종류
     */
    @Transactional
    public CouponTypeResponse changeStatus(
            Long couponTypeId,
            CouponTypeStatus status
    ) {
        CouponType couponType = findCouponType(couponTypeId);
        if (status == null) {
            throw new CouponTypeException(
                    CouponTypeErrorCode.INVALID_COUPON_TYPE_CONFIGURATION,
                    "쿠폰 종류 상태는 필수입니다."
            );
        }
        if (status == CouponTypeStatus.ACTIVE) {
            couponType.activate();
        } else {
            couponType.deactivate();
        }

        CouponType savedCouponType = couponTypeRepository.saveAndFlush(
                couponType
        );
        return toResponse(
                savedCouponType,
                couponEventItemRepository.existsByCouponTypeId(couponTypeId)
        );
    }

    /**
     * 이벤트에서 사용되지 않은 쿠폰 종류를 물리 삭제한다.
     *
     * @param couponTypeId 삭제할 쿠폰 종류 ID
     */
    @Transactional
    public void delete(Long couponTypeId) {
        CouponType couponType = findCouponType(couponTypeId);
        if (couponEventItemRepository.existsByCouponTypeId(couponTypeId)) {
            throw new CouponTypeException(
                    CouponTypeErrorCode.COUPON_TYPE_NOT_DELETABLE
            );
        }

        try {
            couponTypeRepository.delete(couponType);
            couponTypeRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CouponTypeException(
                    CouponTypeErrorCode.COUPON_TYPE_NOT_DELETABLE
            );
        }
    }

    private CouponType findCouponType(Long couponTypeId) {
        return couponTypeRepository.findById(couponTypeId)
                .orElseThrow(() -> new CouponTypeException(
                        CouponTypeErrorCode.COUPON_TYPE_NOT_FOUND
                ));
    }

    private Slice<CouponType> findCouponTypeSlice(
            CouponTypeStatus status,
            Long cursor,
            PageRequest pageable
    ) {
        if (status == null && cursor == null) {
            return couponTypeRepository.findAllByOrderByIdDesc(pageable);
        }
        if (status == null) {
            return couponTypeRepository.findByIdLessThanOrderByIdDesc(
                    cursor,
                    pageable
            );
        }
        if (cursor == null) {
            return couponTypeRepository.findByStatusOrderByIdDesc(
                    status,
                    pageable
            );
        }
        return couponTypeRepository.findByStatusAndIdLessThanOrderByIdDesc(
                status,
                cursor,
                pageable
        );
    }

    private void validateListCondition(Long cursor, int size) {
        if (cursor != null && cursor <= 0) {
            throw new CouponTypeException(
                    CouponTypeErrorCode.INVALID_COUPON_TYPE_LIST_CONDITION,
                    "커서는 1 이상이어야 합니다."
            );
        }
        if (size < 1 || size > 100) {
            throw new CouponTypeException(
                    CouponTypeErrorCode.INVALID_COUPON_TYPE_LIST_CONDITION,
                    "조회 크기는 1 이상 100 이하여야 합니다."
            );
        }
    }

    private Long nextCursor(
            Slice<CouponType> couponTypeSlice,
            List<CouponType> couponTypes
    ) {
        return couponTypeSlice.hasNext() && !couponTypes.isEmpty()
                ? couponTypes.getLast().getId()
                : null;
    }

    private CouponTypeOptionResponse toOptionResponse(CouponType couponType) {
        return new CouponTypeOptionResponse(
                couponType.getId(),
                couponType.getCouponName(),
                couponType.getDiscountType(),
                couponType.getDiscountValue()
        );
    }

    private CouponType createDomain(
            String couponName,
            CouponDiscountType discountType,
            BigDecimal discountValue
    ) {
        try {
            return CouponType.create(couponName, discountType, discountValue);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private CouponTypeException invalid(IllegalArgumentException exception) {
        return new CouponTypeException(
                CouponTypeErrorCode.INVALID_COUPON_TYPE_CONFIGURATION,
                exception.getMessage()
        );
    }

    private CouponTypeResponse toResponse(CouponType couponType, boolean used) {
        return new CouponTypeResponse(
                couponType.getId(),
                couponType.getCouponName(),
                couponType.getDiscountType(),
                couponType.getDiscountValue(),
                couponType.getStatus(),
                used,
                couponType.getCreatedAt(),
                couponType.getUpdatedAt()
        );
    }
}
