package com.clutch.coupon.integrity.repository;

import com.clutch.coupon.integrity.domain.CouponIntegrityCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponIntegrityCheckResultRepository
        extends JpaRepository<CouponIntegrityCheckResult, Long> {
    List<CouponIntegrityCheckResult> findByCheckIdOrderByDisplayOrder(Long checkId);
}
