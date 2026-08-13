package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByClaimId(Long claimId);

    Optional<UserCoupon> findByClaimId(Long claimId);

    boolean existsByCouponEventId(Long couponEventId);

}
