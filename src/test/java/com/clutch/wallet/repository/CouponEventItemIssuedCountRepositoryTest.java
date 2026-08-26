package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** 사용자 쿠폰의 항목별 단일 GROUP BY 집계를 검증한다. */
@SpringBootTest
@Transactional
class CouponEventItemIssuedCountRepositoryTest {

    private static final long FIRST_ITEM_ID = 9_400_001L;
    private static final long SECOND_ITEM_ID = 9_400_002L;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Test
    void 쿠폰_이벤트_항목별_발급_수량을_한번에_집계한다() {
        userCouponRepository.saveAll(List.of(
                newCoupon(9_400_001L, FIRST_ITEM_ID),
                newCoupon(9_400_002L, FIRST_ITEM_ID),
                newCoupon(9_400_003L, SECOND_ITEM_ID)
        ));
        userCouponRepository.flush();

        List<CouponEventItemIssuedCount> counts =
                userCouponRepository.countIssuedCouponsGroupByEventItem();

        assertThat(counts)
                .extracting(
                        CouponEventItemIssuedCount::getCouponEventItemId,
                        CouponEventItemIssuedCount::getIssuedCouponCount
                )
                .contains(
                        tuple(FIRST_ITEM_ID, 2L),
                        tuple(SECOND_ITEM_ID, 1L)
                );
    }

    private UserCoupon newCoupon(long claimId, long couponEventItemId) {
        return new UserCoupon(
                claimId,
                claimId,
                9_400_000L,
                null,
                couponEventItemId,
                "CPN-GROUP-" + claimId,
                "RATE",
                new BigDecimal("50.00"),
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
    }
}
