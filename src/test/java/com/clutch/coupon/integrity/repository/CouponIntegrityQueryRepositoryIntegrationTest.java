package com.clutch.coupon.integrity.repository;

import com.clutch.coupon.integrity.domain.CouponIntegritySnapshot;
import com.clutch.coupon.integrity.domain.IntegrityVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CouponIntegrityQueryRepositoryIntegrationTest {
    @Autowired private CouponIntegrityQueryRepository queryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void MySQL_일관_스냅샷에서_40개_검사를_읽기_전용으로_실행한다() {
        long claimCountBefore = count("coupon_claim_request");
        long couponCountBefore = count("user_coupon");

        CouponIntegritySnapshot snapshot = queryRepository.execute();

        assertNotNull(snapshot.asOfUtc());
        assertEquals(40, snapshot.results().size());
        assertEquals(claimCountBefore, count("coupon_claim_request"));
        assertEquals(couponCountBefore, count("user_coupon"));
        Set<String> codes = snapshot.results().stream()
                .map(result -> result.checkCode())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(40, codes.size());
        assertTrue(codes.contains("LOGICALLY_EXPIRED_ISSUED"));
        assertTrue(codes.contains("USER_COUPON_ITEM_LEADING_INDEX"));
        assertEquals(
                IntegrityVerdict.INFO,
                snapshot.results().stream()
                        .filter(result -> result.checkCode().equals("LOGICALLY_EXPIRED_ISSUED"))
                        .findFirst().orElseThrow().verdict()
        );
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
