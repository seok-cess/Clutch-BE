package com.clutch.coupon.claim.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 쿠폰 재고 복구 Lua 통합 테스트 */
@SpringBootTest
class CouponStockRecoveryRedisIntegrationTest {

    private static final Long OCCURRENCE_ID = 9_999_961L;
    private static final Long FIRST_ITEM_ID = 9_999_962L;
    private static final Long SECOND_ITEM_ID = 9_999_963L;

    private static final String CLAIMED_USERS_KEY =
            CouponClaimRedisKeys.claimedUsers(OCCURRENCE_ID);
    private static final String FIRST_STOCK_KEY =
            CouponClaimRedisKeys.stock(FIRST_ITEM_ID);
    private static final String SECOND_STOCK_KEY =
            CouponClaimRedisKeys.stock(SECOND_ITEM_ID);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("couponStockRecoveryScript")
    private RedisScript<Long> recoveryScript;

    @BeforeEach
    void setUp() {
        deleteKeys();
        stringRedisTemplate.opsForSet().add(
                CLAIMED_USERS_KEY,
                "old-user"
        );
        stringRedisTemplate.opsForValue().set(FIRST_STOCK_KEY, "99");
        stringRedisTemplate.opsForValue().set(SECOND_STOCK_KEY, "99");
    }

    @AfterEach
    void tearDown() {
        deleteKeys();
    }

    @Test
    void replacesStocksAndClaimedUsersAtomically() {
        Long result = stringRedisTemplate.execute(
                recoveryScript,
                List.of(
                        CLAIMED_USERS_KEY,
                        FIRST_STOCK_KEY,
                        SECOND_STOCK_KEY
                ),
                "2",
                "101",
                "102",
                "7",
                "4"
        );

        assertThat(result).isEqualTo(3L);
        assertThat(stringRedisTemplate.opsForSet().members(
                CLAIMED_USERS_KEY
        )).containsExactlyInAnyOrder("101", "102");
        assertThat(stringRedisTemplate.opsForValue().get(FIRST_STOCK_KEY))
                .isEqualTo("7");
        assertThat(stringRedisTemplate.opsForValue().get(SECOND_STOCK_KEY))
                .isEqualTo("4");
    }

    private void deleteKeys() {
        stringRedisTemplate.delete(
                List.of(
                        CLAIMED_USERS_KEY,
                        FIRST_STOCK_KEY,
                        SECOND_STOCK_KEY
                )
        );
    }
}
