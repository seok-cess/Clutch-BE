package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class UserCouponRepositoryTest {

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private EntityManager entityManager;

    private UserCoupon newCoupon(long claimId){
        return new UserCoupon(
                claimId, 1L, 10L, null, 100L,
                "CPN-TEST-" + UUID.randomUUID(),
                "RATE", new BigDecimal("50.00"),
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
    }

    private long uniqueClaimId() {
        return UUID.randomUUID().getMostSignificantBits()
                & Long.MAX_VALUE;
    }

    @Test
    void 저장하면_기본상태와_식별자가_채워진다() {
        UserCoupon saved = userCouponRepository.save(
                newCoupon(uniqueClaimId())
        );

        assertNotNull(saved.getId());
        assertEquals(UserCouponStatus.ISSUED, saved.getStatus());
    }

    @Test
    void claimId로_존재_여부와_조회가_된다() {
        long claimId = uniqueClaimId();
        long missingClaimId = uniqueClaimId();
        userCouponRepository.save(newCoupon(claimId));

        assertTrue(userCouponRepository.existsByClaimId(claimId));
        assertFalse(userCouponRepository.existsByClaimId(missingClaimId));

        Optional<UserCoupon> found = userCouponRepository.findByClaimId(claimId);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getUserId());
    }

    @Test
    void 같은_claimId로_두번_저장하면_실패한다(){
        long claimId = uniqueClaimId();
        userCouponRepository.saveAndFlush(newCoupon(claimId));

        UserCoupon duplicate = newCoupon(claimId);
        assertThrows(DataIntegrityViolationException.class,
                () -> userCouponRepository.saveAndFlush(duplicate));
    }

    @Test
    void EXPIRED_상태를_저장하고_조회할_수_있다() {
        long claimId = uniqueClaimId();
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
