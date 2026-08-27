package com.clutch.wallet.repository;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
        return newCoupon(
                claimId,
                1L,
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
    }

    private UserCoupon newCoupon(
            long claimId,
            long userId,
            Instant expiresAt
    ){
        return new UserCoupon(
                claimId, userId, 10L, null, 100L,
                "CPN-" + claimId, "RATE", new BigDecimal("50.00"),
                expiresAt
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

    @Test
    void 유효상태_필터는_시간상_만료된_ISSUED를_EXPIRED로_분류한다() {
        Instant referenceTime = Instant.parse("2026-08-21T12:00:00Z");
        long userId = CLAIM_BASE + 100;
        UserCoupon logicallyExpired = userCouponRepository.save(
                newCoupon(
                        CLAIM_BASE + 10,
                        userId,
                        referenceTime.minusSeconds(1)
                )
        );
        UserCoupon available = userCouponRepository.save(
                newCoupon(
                        CLAIM_BASE + 11,
                        userId,
                        referenceTime.plusSeconds(1)
                )
        );
        UserCoupon storedExpired = newCoupon(
                CLAIM_BASE + 12,
                userId,
                referenceTime.plusSeconds(2)
        );
        ReflectionTestUtils.setField(
                storedExpired,
                "status",
                UserCouponStatus.EXPIRED
        );
        userCouponRepository.saveAndFlush(storedExpired);

        List<UserCoupon> issued = userCouponRepository.findPage(
                userId,
                UserCouponStatus.ISSUED,
                referenceTime,
                null,
                null,
                PageRequest.of(0, 10)
        );
        List<UserCoupon> expired = userCouponRepository.findPage(
                userId,
                UserCouponStatus.EXPIRED,
                referenceTime,
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(List.of(available.getId()),
                issued.stream().map(UserCoupon::getId).toList());
        assertEquals(
                List.of(logicallyExpired.getId(), storedExpired.getId()),
                expired.stream().map(UserCoupon::getId).toList()
        );
    }

    @Test
    void 만료시각에_도달한_쿠폰은_취소할_수_없다() {
        Instant cancelledAt = Instant.parse("2026-08-21T12:00:00Z");
        UserCoupon expired = userCouponRepository.saveAndFlush(
                newCoupon(
                        CLAIM_BASE + 20,
                        CLAIM_BASE + 200,
                        cancelledAt
                )
        );

        int updated = userCouponRepository.cancel(
                expired.getId(),
                cancelledAt,
                "만료 후 취소 시도"
        );

        assertEquals(0, updated);
        assertEquals(
                UserCouponStatus.ISSUED,
                userCouponRepository.findById(expired.getId())
                        .orElseThrow()
                        .getStatus()
        );
    }
}
