package com.clutch.coupon.claim.repository;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;

import java.util.List;

/**
 * 쿠폰 발급 요청 저장소
 */
public interface CouponClaimRequestRepository
        extends JpaRepository<CouponClaimRequest, Long> {

    /**
     * 사용자별 쿠폰 이벤트 회차 발급 요청 존재 여부
     *
     * @param userId            사용자 식별자
     * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
     * @return 발급 요청 존재 여부
     */
    boolean existsByUserIdAndCouponEventOccurrenceId(
            Long userId,
            Long couponEventOccurrenceId
    );

    /**
     * 쿠폰 이벤트에 발급 요청 이력이 있는지 확인한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 발급 요청 이력이 있으면 {@code true}
     */
    boolean existsByCouponEventId(Long couponEventId);

    /** 쿠폰 이벤트 항목별 상태 요청 수 */
    long countByCouponEventItemIdAndRequestStatus(
            Long couponEventItemId,
            ClaimRequestStatus requestStatus
    );

    /** 쿠폰 이벤트 회차별 상태 사용자 목록 */
    @Query("""
            select request.userId
              from CouponClaimRequest request
             where request.couponEventOccurrenceId = :occurrenceId
               and request.requestStatus = :status
            """)
    List<Long> findUserIdsByOccurrenceIdAndStatus(
            @Param("occurrenceId") Long occurrenceId,
            @Param("status") ClaimRequestStatus status
    );
}
