package com.clutch.coupon.event.repository;

import com.clutch.coupon.event.domain.CouponEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 이벤트 저장소
 */
public interface CouponEventRepository
        extends JpaRepository<CouponEvent, Long> {
}