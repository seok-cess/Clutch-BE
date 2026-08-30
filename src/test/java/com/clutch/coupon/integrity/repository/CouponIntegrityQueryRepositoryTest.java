package com.clutch.coupon.integrity.repository;

import com.clutch.coupon.integrity.domain.CouponIntegrityResult;
import com.clutch.coupon.integrity.domain.IntegrityVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CouponIntegrityQueryRepositoryTest {
    @Test
    void INFO는_전체_판정을_변경하지_않는다() {
        assertEquals(IntegrityVerdict.PASS, CouponIntegrityQueryRepository.overallVerdict(List.of(
                result(IntegrityVerdict.INFO)
        )));
    }

    @Test
    void INFO는_건수가_0이어도_INFO로_판정한다() {
        assertEquals(
                IntegrityVerdict.INFO,
                CouponIntegrityQueryRepository.verdict(IntegrityVerdict.INFO, 0)
        );
    }

    @Test
    void WARN은_FAIL이_없을_때_전체_WARN이다() {
        assertEquals(IntegrityVerdict.WARN, CouponIntegrityQueryRepository.overallVerdict(List.of(
                result(IntegrityVerdict.PASS), result(IntegrityVerdict.WARN)
        )));
    }

    @Test
    void FAIL은_다른_판정보다_우선한다() {
        assertEquals(IntegrityVerdict.FAIL, CouponIntegrityQueryRepository.overallVerdict(List.of(
                result(IntegrityVerdict.WARN), result(IntegrityVerdict.FAIL)
        )));
    }

    private CouponIntegrityResult result(IntegrityVerdict verdict) {
        return new CouponIntegrityResult("TEST", IntegrityVerdict.FAIL, verdict, 0, "테스트", 1);
    }
}
