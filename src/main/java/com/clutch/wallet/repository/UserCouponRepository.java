package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UserCoupon c
        SET c.status = com.clutch.wallet.domain.UserCouponStatus.USED, c.usedAt = :usedAt
        WHERE c.id = :id
        AND c.userId = :userId
        AND c.status = com.clutch.wallet.domain.UserCouponStatus.ISSUED
        AND c.expiresAt > :usedAt
    """)
    int markAsUsed(@Param("id") Long id,
                   @Param("userId") Long userId,
                   @Param("usedAt") Instant usedAt);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UserCoupon c
        SET c.status = com.clutch.wallet.domain.UserCouponStatus.CANCELLED,
            c.cancelledAt = :cancelledAt,
            c.cancelReason = :reason
        WHERE c.id = :id
        AND c.status = com.clutch.wallet.domain.UserCouponStatus.ISSUED
""")
int cancel(@Param("id") Long id,
           @Param("cancelledAt") Instant cancelledAt,
           @Param("reason") String reason);
}
