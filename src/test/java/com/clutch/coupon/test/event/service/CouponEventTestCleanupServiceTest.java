package com.clutch.coupon.test.event.service;

import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponEventTestCleanupServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private CouponEventTestCleanupService cleanupService;

    @Test
    void 테스트_초기화는_발급요청보다_Outbox를_먼저_삭제한다() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        )).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("coupon_event_item")
                    ? List.of(20L)
                    : List.of(30L);
        });
        when(jdbcTemplate.update(
                anyString(),
                any(MapSqlParameterSource.class)
        )).thenReturn(1);

        Map<String, Integer> result = cleanupService.resetEvent(1L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(
                String.class
        );
        verify(jdbcTemplate, times(7)).update(
                sqlCaptor.capture(),
                any(MapSqlParameterSource.class)
        );

        List<String> statements = sqlCaptor.getAllValues().stream()
                .map(sql -> sql.replaceAll("\\s+", " ").trim())
                .toList();

        assertThat(statements.get(0)).startsWith(
                "DELETE FROM wallet_outbox"
        );
        assertThat(statements.get(1)).startsWith(
                "DELETE FROM coupon_claim_outbox"
        );
        assertThat(statements.get(2)).startsWith(
                "DELETE FROM user_coupon"
        );
        assertThat(statements.get(3)).startsWith(
                "DELETE FROM coupon_claim_request"
        );
        assertThat(result)
                .containsEntry("walletOutbox", 1)
                .containsEntry("claimOutbox", 1)
                .containsEntry("userCoupon", 1)
                .containsEntry("claimRequest", 1)
                .containsEntry("occurrence", 1)
                .containsEntry("eventReopened", 1);
        verify(redisTemplate).delete(CouponClaimRedisKeys.stock(20L));
        verify(redisTemplate).delete(
                CouponClaimRedisKeys.claimedUsers(30L)
        );
        verify(redisTemplate).delete(CouponClaimRedisKeys.context(30L));
    }
}
