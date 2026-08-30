package com.clutch.coupon.integrity.repository;

import com.clutch.coupon.integrity.domain.CouponIntegrityCheck;
import com.clutch.coupon.integrity.domain.IntegrityExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponIntegrityCheckRepository extends JpaRepository<CouponIntegrityCheck, Long> {
    boolean existsByExecutionStatus(IntegrityExecutionStatus status);
    Slice<CouponIntegrityCheck> findAllByOrderByIdDesc(Pageable pageable);
    Slice<CouponIntegrityCheck> findByIdLessThanOrderByIdDesc(Long id, Pageable pageable);
    List<CouponIntegrityCheck> findByExecutionStatusAndStartedAtBefore(
            IntegrityExecutionStatus status,
            LocalDateTime cutoff
    );
}
