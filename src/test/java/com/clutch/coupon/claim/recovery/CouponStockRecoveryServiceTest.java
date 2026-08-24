package com.clutch.coupon.claim.recovery;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import com.clutch.coupon.claim.redis.CouponStockInitializer;
import com.clutch.coupon.claim.repository.CouponClaimRequestRepository;
import com.clutch.coupon.contract.issuance.CouponIssuanceRecoveryReader;
import com.clutch.coupon.event.domain.CouponEventItem;
import com.clutch.coupon.event.domain.CouponEventOccurrence;
import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;
import com.clutch.coupon.event.repository.CouponEventItemRepository;
import com.clutch.coupon.event.repository.CouponEventOccurrenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** MySQL 기준 쿠폰 Redis 재구축 테스트 */
@ExtendWith(MockitoExtension.class)
class CouponStockRecoveryServiceTest {

    private static final Long EVENT_ID = 9_999_970L;
    private static final Long OCCURRENCE_ID = 9_999_971L;
    private static final Long ITEM_ID = 9_999_972L;

    @Mock
    private CouponEventOccurrenceRepository occurrenceRepository;

    @Mock
    private CouponEventItemRepository itemRepository;

    @Mock
    private CouponClaimRequestRepository claimRequestRepository;

    @Mock
    private CouponIssuanceRecoveryReader issuanceRecoveryReader;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisScript<Long> recoveryScript;

    @Mock
    private CouponStockInitializer couponStockInitializer;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private CouponStockRecoveryStateManager stateManager;
    private CouponStockRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        stateManager = new CouponStockRecoveryStateManager();
        recoveryService = new CouponStockRecoveryService(
                stateManager,
                occurrenceRepository,
                itemRepository,
                claimRequestRepository,
                issuanceRecoveryReader,
                stringRedisTemplate,
                couponStockInitializer,
                recoveryScript
        );
    }

    @Test
    void rebuildsStockAndClaimedUsersFromMysql() {
        givenOpenOccurrenceAndItem(10);
        when(claimRequestRepository
                .countByCouponEventItemIdAndRequestStatus(
                        ITEM_ID,
                        ClaimRequestStatus.SUCCEEDED
                ))
                .thenReturn(3L);
        when(issuanceRecoveryReader.countIssuedCoupons(ITEM_ID))
                .thenReturn(3L);
        when(claimRequestRepository
                .findUserIdsByOccurrenceIdAndStatus(
                        OCCURRENCE_ID,
                        ClaimRequestStatus.SUCCEEDED
                ))
                .thenReturn(List.of(101L, 102L, 103L));
        when(issuanceRecoveryReader.findIssuedUserIds(OCCURRENCE_ID))
                .thenReturn(List.of(101L, 102L, 103L));
        when(stringRedisTemplate.execute(
                eq(recoveryScript),
                anyList(),
                any(Object[].class)
        )).thenReturn(2L);
        when(stringRedisTemplate.opsForValue())
                .thenReturn(valueOperations);
        when(valueOperations.get(CouponClaimRedisKeys.stock(ITEM_ID)))
                .thenReturn("7");
        when(stringRedisTemplate.opsForSet())
                .thenReturn(setOperations);
        when(setOperations.size(
                CouponClaimRedisKeys.claimedUsers(OCCURRENCE_ID)
        )).thenReturn(3L);

        CouponStockRecoveryResult result =
                recoveryService.recoverOpenOccurrences();

        assertThat(result.state())
                .isEqualTo(CouponStockRecoveryState.READY);
        assertThat(result.recoveredOccurrences()).isOne();
        assertThat(result.recoveredItems()).isOne();
        assertThat(result.recoveredUsers()).isEqualTo(3);
        assertThat(stateManager.current())
                .isEqualTo(CouponStockRecoveryState.READY);
    }

    @Test
    void stopsRecoveryWhenMysqlCountsDoNotMatch() {
        givenOpenOccurrenceAndItem(10);
        when(claimRequestRepository
                .countByCouponEventItemIdAndRequestStatus(
                        ITEM_ID,
                        ClaimRequestStatus.SUCCEEDED
                ))
                .thenReturn(3L);
        when(issuanceRecoveryReader.countIssuedCoupons(ITEM_ID))
                .thenReturn(2L);

        assertThatThrownBy(recoveryService::recoverOpenOccurrences)
                .isInstanceOfSatisfying(
                        CouponClaimException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        CouponClaimErrorCode
                                                .COUPON_STOCK_INCONSISTENT
                                )
                );

        assertThat(stateManager.current())
                .isEqualTo(CouponStockRecoveryState.FAILED);
    }

    private void givenOpenOccurrenceAndItem(int quantity) {
        CouponEventOccurrence occurrence =
                mock(CouponEventOccurrence.class);
        lenient().when(occurrence.getId()).thenReturn(OCCURRENCE_ID);
        when(occurrence.getCouponEventId()).thenReturn(EVENT_ID);
        when(occurrenceRepository.findAllByOccurrenceStatus(
                CouponEventOccurrenceStatus.OPEN
        )).thenReturn(List.of(occurrence));

        CouponEventItem item = mock(CouponEventItem.class);
        when(item.getId()).thenReturn(ITEM_ID);
        lenient().when(item.getQuantity()).thenReturn(quantity);
        when(itemRepository.findAllByCouponEventId(EVENT_ID))
                .thenReturn(List.of(item));
    }
}
