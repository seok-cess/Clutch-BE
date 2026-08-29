package com.clutch.coupon.admin.dashboard.repository;

import com.clutch.coupon.claim.recovery.CouponStockRecoveryState;
import com.clutch.coupon.claim.recovery.CouponStockRecoveryStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** 관리자 페이지 운영 홈의 Redis 재고 일괄 판정을 검증한다. */
@ExtendWith(MockitoExtension.class)
class AdminCouponDashboardStockRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CouponStockRecoveryStateManager stateManager;
    private AdminCouponDashboardStockRepository repository;

    @BeforeEach
    void setUp() {
        stateManager = new CouponStockRecoveryStateManager();
        repository = new AdminCouponDashboardStockRepository(
                redisTemplate,
                stateManager
        );
    }

    @Test
    void 모든_항목의_합산_재고가_0인_이벤트만_소진으로_판정한다() {
        List<OpenEventItemRow> items = List.of(
                new OpenEventItemRow(1L, 10L),
                new OpenEventItemRow(1L, 11L),
                new OpenEventItemRow(2L, 12L)
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(
                "coupon:event-item:10:stock",
                "coupon:event-item:11:stock",
                "coupon:event-item:12:stock"
        ))).thenReturn(List.of("0", "0", "3"));

        assertThat(repository.findSoldOutEventIds(items))
                .containsExactly(1L);
    }

    @Test
    void Redis_키가_누락되면_소진으로_간주하지_않고_장애로_전환한다() {
        List<OpenEventItemRow> items = List.of(
                new OpenEventItemRow(1L, 10L),
                new OpenEventItemRow(1L, 11L)
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(
                "coupon:event-item:10:stock",
                "coupon:event-item:11:stock"
        ))).thenReturn(java.util.Arrays.asList("0", null));

        assertThatThrownBy(() -> repository.findSoldOutEventIds(items))
                .isInstanceOf(AdminCouponDashboardStockException.class);
        assertThat(stateManager.current())
                .isEqualTo(CouponStockRecoveryState.UNAVAILABLE);
    }
}
