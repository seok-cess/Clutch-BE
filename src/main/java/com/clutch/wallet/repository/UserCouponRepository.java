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

    /** 쿠폰 발급 요청(claimId) 기준으로 이미 발급된 쿠폰이 있는지 확인한다. */
    boolean existsByClaimId(Long claimId);

    /** 쿠폰 발급 요청(claimId)으로 발급된 사용자 쿠폰을 조회한다. */
    Optional<UserCoupon> findByClaimId(Long claimId);

    /**
     * 쿠폰 이벤트를 통해 실제 발급된 사용자 쿠폰이 있는지 확인한다.
     *
     * @param couponEventId 쿠폰 이벤트 ID
     * @return 발급 이력이 있으면 {@code true}
     */
    boolean existsByCouponEventId(Long couponEventId);

    /** 쿠폰 이벤트 항목별 실제 발급 수량 */
    long countByCouponEventItemId(Long couponEventItemId);

    /** 모든 쿠폰 이벤트 항목의 실제 발급 수량을 한 번에 집계한다. */
    @Query("""
            select coupon.couponEventItemId as couponEventItemId,
                   count(coupon.id) as issuedCouponCount
              from UserCoupon coupon
             group by coupon.couponEventItemId
            """)
    List<CouponEventItemIssuedCount> countIssuedCouponsGroupByEventItem();

    /** 쿠폰 이벤트 회차별 실제 발급 사용자 목록 */
    @Query("""
            select coupon.userId
              from UserCoupon coupon
             where coupon.couponEventOccurrenceId = :occurrenceId
            """)
    List<Long> findUserIdsByOccurrenceId(
            @Param("occurrenceId") Long occurrenceId
    );

    /** 소유자를 함께 검증해 사용자 쿠폰을 단건 조회한다. */
    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

    /**
     * 상태와 커서 조건으로 사용자 쿠폰 목록을 만료일 오름차순으로 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @param status 조회할 쿠폰 상태, 전체 조회 시 {@code null}
     * @param cursorExpiresAt 이전 페이지 마지막 항목의 만료 시각, 첫 조회 시 {@code null}
     * @param cursorId 이전 페이지 마지막 항목의 ID, 첫 조회 시 {@code null}
     * @param pageable 조회 개수 제한
     * @return 조건에 맞는 사용자 쿠폰 목록
     */
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

    /**
     * 만료되지 않은 발급 상태의 쿠폰을 사용 완료 상태로 변경한다.
     *
     * @param id 사용 처리할 사용자 쿠폰 ID
     * @param userId 소유자 검증에 사용할 사용자 ID
     * @param usedAt 사용 처리 시각
     * @return 변경된 행 수, 조건이 맞지 않으면 0
     */
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

    /**
     * 발급 상태의 쿠폰을 취소 상태로 변경한다.
     *
     * @param id 취소할 사용자 쿠폰 ID
     * @param cancelledAt 취소 처리 시각
     * @param reason 취소 사유
     * @return 변경된 행 수, 조건이 맞지 않으면 0
     */
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
