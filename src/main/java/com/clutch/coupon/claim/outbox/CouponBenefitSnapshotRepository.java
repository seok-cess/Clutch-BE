package com.clutch.coupon.claim.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 쿠폰 혜택 스냅샷 조회 저장소
 */
@Repository
@RequiredArgsConstructor
public class CouponBenefitSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 쿠폰 이벤트 항목별 혜택 스냅샷 조회
     *
     * @param couponEventItemId 쿠폰 이벤트 항목 식별자
     * @return 쿠폰 혜택 스냅샷
     */
    public Optional<CouponBenefitSnapshot> findByCouponEventItemId(
            Long couponEventItemId
    ) {
        return jdbcTemplate.query(
                        """
                        SELECT type.discount_type,
                               type.discount_value
                          FROM coupon_event_item item
                          JOIN coupon_type type
                            ON type.coupon_type_id =
                               item.coupon_type_id
                         WHERE item.coupon_event_item_id = ?
                        """,
                        (resultSet, rowNumber) ->
                                new CouponBenefitSnapshot(
                                        resultSet.getString(
                                                "discount_type"
                                        ),
                                        resultSet.getBigDecimal(
                                                "discount_value"
                                        )
                                ),
                        couponEventItemId
                )
                .stream()
                .findFirst();
    }
}