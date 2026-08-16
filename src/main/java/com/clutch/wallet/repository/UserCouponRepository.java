package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자에게 발급된 쿠폰의 저장과 발급 이력 조회를 담당하는 저장소.
 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByClaimId(Long claimId);

    Optional<UserCoupon> findByClaimId(Long claimId);

    /**
     * 쿠폰 이벤트를 통해 실제 발급된 사용자 쿠폰이 있는지 확인한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 발급 이력이 있으면 {@code true}
     */
    boolean existsByCouponEventId(Long couponEventId);

}
