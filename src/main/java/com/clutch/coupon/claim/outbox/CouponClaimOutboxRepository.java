package com.clutch.coupon.claim.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쿠폰 발급 Outbox 저장소
 */
public interface CouponClaimOutboxRepository
        extends JpaRepository<CouponClaimOutbox, Long> {

    /**
     * 발행 대기 Outbox 목록 조회
     *
     * @param status Outbox 상태
     * @return 발행 대기 Outbox 목록
     */
    List<CouponClaimOutbox> findTop100ByStatusOrderByIdAsc(
            CouponClaimOutboxStatus status
    );

}
