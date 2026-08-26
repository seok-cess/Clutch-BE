package com.clutch.coupon.claim.repository;

import jakarta.persistence.EntityManager;
import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.domain.CouponClaimRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 요청 저장소 테스트
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class CouponClaimRequestRepositoryTest {

    private static final Long ESPORTS_MATCH_ID = 9_000_001L;
    private static final Long COUPON_EVENT_ID = 9_000_001L;
    private static final Long COUPON_EVENT_OCCURRENCE_ID = 9_000_001L;

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
     * SQL 실행기
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 저장소 테스트 선행 데이터 구성
     */
    @BeforeEach
    void setUp() {
        LocalDateTime currentTime =
                LocalDateTime.now(ZoneOffset.UTC);

        jdbcTemplate.update(
                """
                INSERT INTO esports_match (
                    esports_match_id,
                    external_match_id,
                    league_external_id,
                    season_key,
                    scheduled_at,
                    lifecycle_status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                ESPORTS_MATCH_ID,
                "claim-repository-match",
                "claim-test-league",
                "2026",
                currentTime,
                "inProgress"
        );

        jdbcTemplate.update(
                """
                INSERT INTO coupon_event (
                    coupon_event_id,
                    esports_match_id,
                    event_name,
                    trigger_type,
                    event_status,
                    claim_window_seconds
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                COUPON_EVENT_ID,
                ESPORTS_MATCH_ID,
                "저장소 테스트 쿠폰 이벤트",
                "FIRST_BLOOD",
                "OPEN",
                600
        );

        jdbcTemplate.update(
                """
                INSERT INTO coupon_event_occurrence (
                    coupon_event_occurrence_id,
                    coupon_event_id,
                    source_event_key,
                    detected_at,
                    opened_at,
                    expires_at,
                    occurrence_status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                COUPON_EVENT_OCCURRENCE_ID,
                COUPON_EVENT_ID,
                "claim-repository-occurrence",
                currentTime.minusMinutes(1),
                currentTime.minusMinutes(1),
                currentTime.plusMinutes(10),
                "OPEN"
        );
    }

    /**
     * 쿠폰 발급 요청 저장 및 조회 검증
     */
    @Test
    void saveAndFindById() {
        // given
        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
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
                .isEqualTo(COUPON_EVENT_ID);
        assertThat(foundClaimRequest.getCouponEventOccurrenceId())
                .isEqualTo(COUPON_EVENT_OCCURRENCE_ID);
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
     * 사용자별 쿠폰 이벤트 회차 발급 요청 존재 여부 검증
     */
    @Test
    void existsByUserIdAndCouponEventOccurrenceId() {
        // given
        CouponClaimRequest claimRequest =
                CouponClaimRequest.create(
                        COUPON_EVENT_ID,
                        COUPON_EVENT_OCCURRENCE_ID,
                        9_000_002L,
                        9_000_002L
                );

        couponClaimRequestRepository.saveAndFlush(claimRequest);

        // when
        boolean exists =
                couponClaimRequestRepository
                        .existsByUserIdAndCouponEventOccurrenceId(
                                9_000_002L,
                                COUPON_EVENT_OCCURRENCE_ID
                        );

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void DB의_CANCELLED_상태를_JPA로_조회할_수_있다() {
        CouponClaimRequest claimRequest = CouponClaimRequest.create(
                COUPON_EVENT_ID,
                COUPON_EVENT_OCCURRENCE_ID,
                9_000_003L,
                9_000_003L
        );
        CouponClaimRequest saved =
                couponClaimRequestRepository.saveAndFlush(claimRequest);

        jdbcTemplate.update(
                """
                UPDATE coupon_claim_request
                   SET request_status = 'CANCELLED'
                 WHERE coupon_claim_request_id = ?
                """,
                saved.getId()
        );
        entityManager.clear();

        assertThat(couponClaimRequestRepository.findById(saved.getId()))
                .get()
                .extracting(CouponClaimRequest::getRequestStatus)
                .isEqualTo(ClaimRequestStatus.CANCELLED);
    }
}
