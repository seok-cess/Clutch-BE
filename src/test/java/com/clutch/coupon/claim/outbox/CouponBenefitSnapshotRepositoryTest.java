package com.clutch.coupon.claim.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 혜택 스냅샷 조회 테스트
 */
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(CouponBenefitSnapshotRepository.class)
class CouponBenefitSnapshotRepositoryTest {

    @Autowired
    private CouponBenefitSnapshotRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 쿠폰 혜택 스냅샷 조회 검증
     */
    @Test
    void findByCouponEventItemId() {
        // given
        jdbcTemplate.update(
                """
                INSERT INTO coupon_type (
                    coupon_type_id,
                    coupon_name,
                    discount_type,
                    discount_value,
                    status
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                9_100_001L,
                "금액 할인 쿠폰",
                "AMOUNT",
                3_000,
                "ACTIVE"
        );

        jdbcTemplate.update(
                """
                INSERT INTO coupon_event_item (
                    coupon_event_item_id,
                    coupon_event_id,
                    coupon_type_id,
                    quantity,
                    success_count
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                9_100_001L,
                9_100_001L,
                9_100_001L,
                100,
                0
        );

        // when
        CouponBenefitSnapshot snapshot =
                repository
                        .findByCouponEventItemId(9_100_001L)
                        .orElseThrow();

        // then
        assertThat(snapshot.discountType())
                .isEqualTo("AMOUNT");
        assertThat(snapshot.discountValue())
                .isEqualByComparingTo("3000.00");
    }
}