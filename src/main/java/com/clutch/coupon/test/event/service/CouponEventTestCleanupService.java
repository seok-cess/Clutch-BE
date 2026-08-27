package com.clutch.coupon.test.event.service;

import com.clutch.coupon.claim.redis.CouponClaimRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 트리거 시연·테스트로 생긴 흔적을 지운다.
 *
 * 일반 삭제({@code CouponEventService.delete})는 READY 이고 이력이 없을 때만 된다.
 * 트리거를 한 번 돌리면 회차와 발급 이력이 남아 그 조건을 벗어나므로, 같은 이벤트로
 * 다시 시연하려면 이력째 지워야 한다.
 *
 * 시연을 반복할 때마다 DB 에 직접 붙는 것을 피하려고 API 로 둔다. 운영 데이터까지
 * 지우지 않도록 대상은 항상 이벤트 하나로 한정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponEventTestCleanupService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    /**
     * 이벤트 하나와 거기서 나온 발급 이력을 모두 지우고, 이벤트는 READY 로 되돌린다.
     *
     * 이벤트 정의(항목·단계)는 남긴다 — 같은 설정으로 바로 다시 시연하기 위해서다.
     * 지우는 것은 "이번 시연에서 생긴 것"뿐이다.
     *
     * @return 테이블별 삭제 건수
     */
    @Transactional
    public Map<String, Integer> resetEvent(Long couponEventId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("eventId", couponEventId);

        Map<String, Integer> deleted = new LinkedHashMap<>();

        // Redis 를 먼저 비운다. DB 만 지우면 재고 카운터와 발급자 목록이 남아
        // 다시 시연할 때 "이미 받았다" 로 막힌다
        clearRedis(couponEventId, params);

        // Outbox는 claimId만 값으로 들고 FK가 없어 발급 요청보다 먼저 명시적으로
        // 지우지 않으면 테스트 초기화 뒤 고아 데이터로 남는다.
        deleted.put("walletOutbox", jdbcTemplate.update("""
                DELETE FROM wallet_outbox
                 WHERE aggregate_id IN (
                    SELECT coupon_claim_request_id
                      FROM coupon_claim_request
                     WHERE coupon_event_id = :eventId
                 )
                """, params));
        deleted.put("claimOutbox", jdbcTemplate.update("""
                DELETE FROM coupon_claim_outbox
                 WHERE aggregate_id IN (
                    SELECT coupon_claim_request_id
                      FROM coupon_claim_request
                     WHERE coupon_event_id = :eventId
                 )
                """, params));

        // 발급된 쿠폰 → 발급 요청 → 회차 순. FK 가 이 방향으로 걸려 있다
        deleted.put("userCoupon", jdbcTemplate.update("""
                DELETE FROM user_coupon WHERE coupon_event_id = :eventId
                """, params));
        deleted.put("claimRequest", jdbcTemplate.update("""
                DELETE FROM coupon_claim_request WHERE coupon_event_id = :eventId
                """, params));
        deleted.put("occurrence", jdbcTemplate.update("""
                DELETE FROM coupon_event_occurrence WHERE coupon_event_id = :eventId
                """, params));

        // 발급 수량을 되돌려야 재고가 다시 찬다
        jdbcTemplate.update("""
                UPDATE coupon_event_item SET success_count = 0
                 WHERE coupon_event_id = :eventId
                """, params);

        deleted.put("eventReopened", jdbcTemplate.update("""
                UPDATE coupon_event SET event_status = 'READY'
                 WHERE coupon_event_id = :eventId
                """, params));

        log.info("테스트 이벤트 {} 초기화 — {}", couponEventId, deleted);
        return deleted;
    }

    /** 이 이벤트가 쓰던 재고 카운터와 회차별 발급자 목록을 지운다 */
    private void clearRedis(Long couponEventId, MapSqlParameterSource params) {
        List<Long> itemIds = jdbcTemplate.queryForList("""
                SELECT coupon_event_item_id FROM coupon_event_item
                 WHERE coupon_event_id = :eventId
                """, params, Long.class);
        List<Long> occurrenceIds = jdbcTemplate.queryForList("""
                SELECT coupon_event_occurrence_id FROM coupon_event_occurrence
                 WHERE coupon_event_id = :eventId
                """, params, Long.class);

        itemIds.forEach(id -> redisTemplate.delete(CouponClaimRedisKeys.stock(id)));
        occurrenceIds.forEach(id -> {
            redisTemplate.delete(CouponClaimRedisKeys.claimedUsers(id));
            redisTemplate.delete(CouponClaimRedisKeys.context(id));
        });
    }
}
