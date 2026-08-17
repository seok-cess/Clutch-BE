package com.clutch.coupon.claim.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 발급 Outbox 저장소
 */
public interface CouponClaimOutboxRepository
        extends JpaRepository<CouponClaimOutbox, Long> {
}