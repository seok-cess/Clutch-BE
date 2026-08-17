package com.clutch.coupon.claim.outbox;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.lolesports.service.PollingScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쿠폰 발급 Outbox 트랜잭션 통합 테스트
 */
@SpringBootTest
class CouponClaimOutboxTransactionIntegrationTest {

    private static final Long USER_ID = 9_200_001L;

    @Autowired
    private CouponClaimRequestRepository claimRequestRepository;

    @Autowired
    private CouponClaimOutboxRepository outboxRepository;

    @Autowired
    private CouponClaimOutboxWriter outboxWriter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    /**
     * 발급 요청 및 Outbox 동시 롤백 검증
     */
    @Test
    void claimAndOutboxRollbackTogether() {
        // given
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        AtomicReference<Long> claimId =
                new AtomicReference<>();

        CouponBenefitSnapshot benefitSnapshot =
                new CouponBenefitSnapshot(
                        "RATE",
                        new BigDecimal("20.00")
                );

        // when
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(
                        status -> {
                            CouponClaimRequest claimRequest =
                                    CouponClaimRequest.create(
                                            9_200_001L,
                                            null,
                                            9_200_001L,
                                            USER_ID
                                    );

                            CouponClaimRequest savedClaimRequest =
                                    claimRequestRepository
                                            .saveAndFlush(claimRequest);

                            claimId.set(savedClaimRequest.getId());

                            outboxWriter.writeAcceptedEvent(
                                    savedClaimRequest,
                                    benefitSnapshot,
                                    Instant.parse(
                                            "2026-08-17T00:00:00Z"
                                    )
                            );

                            outboxRepository.flush();

                            throw new IllegalStateException(
                                    "트랜잭션 롤백 테스트"
                            );
                        }
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("트랜잭션 롤백 테스트");

        // then
        Integer claimCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM coupon_claim_request
                WHERE coupon_claim_request_id = ?
                """,
                Integer.class,
                claimId.get()
        );

        Integer outboxCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM coupon_claim_outbox
                WHERE aggregate_id = ?
                """,
                Integer.class,
                claimId.get()
        );

        assertThat(claimCount).isZero();
        assertThat(outboxCount).isZero();
    }
}