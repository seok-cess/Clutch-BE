package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Autowired
    private UserCouponRepository userCouponRepository;

    private UserCoupon newCoupon(long claimId){
        return new UserCoupon(
                claimId, 1L, 10L, null, 100L,
                "CPN-" + claimId, "RATE", new BigDecimal("50.00"),
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
    }

    @Test
    void 저장하면_기본상태와_식별자가_채워진다() {
        UserCoupon saved = userCouponRepository.save(newCoupon(1001L));

        assertNotNull(saved.getId());
        assertEquals(UserCouponStatus.ISSUED, saved.getStatus());
    }

    @Test
    void claimId로_존재_여부와_조회가_된다() {
        userCouponRepository.save(newCoupon(2001L));

        assertTrue(userCouponRepository.existsByClaimId(2001L));
        assertFalse(userCouponRepository.existsByClaimId(9999L));

        Optional<UserCoupon> found = userCouponRepository.findByClaimId(2001L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getUserId());
    }

    @Test
    void 같은_claimId로_두번_저장하면_실패한다(){
        userCouponRepository.saveAndFlush(newCoupon(3001L));

        UserCoupon duplicate = newCoupon(3001L);
        assertThrows(DataIntegrityViolationException.class,
                () -> userCouponRepository.saveAndFlush(duplicate));
    }
}
