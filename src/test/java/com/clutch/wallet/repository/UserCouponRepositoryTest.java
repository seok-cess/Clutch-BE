package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class UserCouponRepositoryTest {

    /**
     * 테스트 전용 claim_id 대역의 시작점.
     *
     * <p>이 테스트는 개발 DB 를 그대로 쓴다. 성능 측정용 시드가 들어 있는 환경에서는
     * 작은 값을 쓰면 시드와 claim_id 가 겹쳐 유니크 제약에 걸린다(실제로 1001/2001/3001
     * 이 겹쳤다). 시드가 만들지 않을 만큼 큰 대역을 잡아 충돌을 피한다.</p>
     */
    private static final long CLAIM_BASE = 9_900_000_000L;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private EntityManager entityManager;

    private UserCoupon newCoupon(long claimId){
        return new UserCoupon(
                claimId, 1L, 10L, null, 100L,
                "CPN-" + claimId, "RATE", new BigDecimal("50.00"),
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
    }

    @Test
    void 저장하면_기본상태와_식별자가_채워진다() {
        UserCoupon saved = userCouponRepository.save(newCoupon(CLAIM_BASE + 1));

        assertNotNull(saved.getId());
        assertEquals(UserCouponStatus.ISSUED, saved.getStatus());
    }

    @Test
    void claimId로_존재_여부와_조회가_된다() {
        userCouponRepository.save(newCoupon(CLAIM_BASE + 2));

        assertTrue(userCouponRepository.existsByClaimId(CLAIM_BASE + 2));
        assertFalse(userCouponRepository.existsByClaimId(CLAIM_BASE + 999));

        Optional<UserCoupon> found = userCouponRepository.findByClaimId(CLAIM_BASE + 2);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getUserId());
    }

    @Test
    void 같은_claimId로_두번_저장하면_실패한다(){
        userCouponRepository.saveAndFlush(newCoupon(CLAIM_BASE + 3));

        UserCoupon duplicate = newCoupon(CLAIM_BASE + 3);
        assertThrows(DataIntegrityViolationException.class,
                () -> userCouponRepository.saveAndFlush(duplicate));
    }

    @Test
    void EXPIRED_상태를_저장하고_조회할_수_있다() {
        long claimId = CLAIM_BASE + 4;
        UserCoupon coupon = newCoupon(claimId);
        ReflectionTestUtils.setField(
                coupon,
                "status",
                UserCouponStatus.EXPIRED
        );

        userCouponRepository.saveAndFlush(coupon);
        entityManager.clear();

        assertEquals(
                UserCouponStatus.EXPIRED,
                userCouponRepository.findByClaimId(claimId)
                        .orElseThrow()
                        .getStatus()
        );
    }
}
