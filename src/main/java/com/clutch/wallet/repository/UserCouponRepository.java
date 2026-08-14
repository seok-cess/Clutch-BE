package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByClaimId(Long claimId);

    Optional<UserCoupon> findByClaimId(Long claimId);

<<<<<<< Updated upstream
    boolean existsByCouponEventId(Long couponEventId);

=======
    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

    @Query("""
        SELECT c FROM UserCoupon c
        WHERE c.userId = :userId
                AND (:status IS NULL OR c.status = :status)
                AND (:cursorExpiresAt IS NULL
                        OR c.expiresAt > :cursorExpiresAt
                        OR (c.expiresAt = :cursorExpiresAt
                                AND c.id > :cursorId))
        ORDER BY c.expiresAt, c.id
                """)
    List<UserCoupon> findPage(@Param("userId") Long userId,
                              @Param("status") UserCouponStatus status,
                              @Param("cursorExpiresAt")Instant cursorExpiresAt,
                              @Param("cursorId") Long cursorId,
                              Pageable pageable
                              );
>>>>>>> Stashed changes
}
