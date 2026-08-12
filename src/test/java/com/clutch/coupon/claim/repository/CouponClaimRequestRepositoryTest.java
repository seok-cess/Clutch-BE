package com.clutch.coupon.claim.repository;

import jakarta.persistence.EntityManager;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 요청 저장소 테스트
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class CouponClaimRequestRepositoryTest {

    /**
     * 쿠폰 발급 요청 저장소
     */
    @Autowired
    private CouponClaimRequestRepository couponClaimRequestRepository;

    /**
     * JPA 엔티티 관리자
     */
    @Autowired
    private EntityManager entityManager;

    /**
     * 쿠폰 발급 요청 저장 및 조회 검증
     */
    @Test
    void saveAndFindById() {
        // given
        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        9_000_001L,
                        9_000_001L,
                        9_000_001L
                );

        // when
        CouponClaimRequest savedClaimRequest =
                couponClaimRequestRepository.saveAndFlush(claimRequest);

        Long savedClaimRequestId = savedClaimRequest.getId();

        entityManager.clear();

        CouponClaimRequest foundClaimRequest =
                couponClaimRequestRepository
                        .findById(savedClaimRequestId)
                        .orElseThrow();

        // then
        assertThat(foundClaimRequest.getId()).isNotNull();
        assertThat(foundClaimRequest.getCouponEventId())
                .isEqualTo(9_000_001L);
        assertThat(foundClaimRequest.getCouponEventItemId())
                .isEqualTo(9_000_001L);
        assertThat(foundClaimRequest.getUserId())
                .isEqualTo(9_000_001L);
        assertThat(foundClaimRequest.getRequestStatus())
                .isEqualTo(ClaimRequestStatus.PENDING);
        assertThat(foundClaimRequest.getCreatedAt()).isNotNull();
        assertThat(foundClaimRequest.getUpdatedAt()).isNotNull();
    }

    /**
     * 사용자별 쿠폰 이벤트 항목 발급 요청 존재 여부 검증
     */
    @Test
    void existsByUserIdAndCouponEventItemId() {
        // given
        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        9_000_002L,
                        9_000_002L,
                        9_000_002L
                );

        couponClaimRequestRepository.saveAndFlush(claimRequest);

        // when
        boolean exists =
                couponClaimRequestRepository
                        .existsByUserIdAndCouponEventItemId(
                                9_000_002L,
                                9_000_002L
                        );

        // then
        assertThat(exists).isTrue();
    }
}