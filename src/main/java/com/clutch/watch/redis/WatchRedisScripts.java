package com.clutch.watch.redis;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 시청 세션 Redis 작업을 원자적으로 처리하는 Lua 스크립트 모음.
 */
final class WatchRedisScripts {

    /**
     * 포인트 지급 전에 수령 자격을 원자적으로 확인하고 세션 TTL을 연장한다.
     */
    static final DefaultRedisScript<String> PREPARE_REWARD_CLAIM = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 'REPLACED'
            end
            if redis.call('EXISTS', KEYS[2]) == 0 then
                return 'EXPIRED'
            end
            if redis.call('EXISTS', KEYS[3]) == 0 then
                return 'SESSION_NOT_FOUND'
            end
            if redis.call('HGET', KEYS[3], 'userId') ~= ARGV[2] then
                return 'USER_MISMATCH'
            end

            local currentRewardSequence = tonumber(redis.call('HGET', KEYS[3], 'rewardSequence')) or 1
            if currentRewardSequence ~= tonumber(ARGV[3]) then
                return 'INVALID_REWARD_SEQUENCE'
            end
            local eligibleMilliseconds = tonumber(redis.call('HGET', KEYS[3], 'eligibleMilliseconds')) or 0
            if eligibleMilliseconds < tonumber(ARGV[4]) then
                return 'NOT_CLAIMABLE'
            end

            redis.call('PEXPIRE', KEYS[2], ARGV[5])
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            redis.call('PEXPIRE', KEYS[3], ARGV[7])
            return 'SUCCESS'
            """, String.class);

    /**
     * DB 포인트 지급이 확정된 회차의 누적시간을 초기화하고 다음 회차를 시작한다.
     */
    static final DefaultRedisScript<String> COMPLETE_REWARD_CLAIM = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 'REPLACED'
            end

            if redis.call('EXISTS', KEYS[2]) == 0 then
                return 'EXPIRED'
            end

            if redis.call('EXISTS', KEYS[3]) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            if redis.call('HGET', KEYS[3], 'userId') ~= ARGV[2] then
                return 'USER_MISMATCH'
            end

            local currentRewardSequence = tonumber(redis.call('HGET', KEYS[3], 'rewardSequence')) or 1
            local requestedRewardSequence = tonumber(ARGV[3])
            if currentRewardSequence > requestedRewardSequence then
                return 'ALREADY_COMPLETED:' .. tostring(currentRewardSequence)
            end
            if currentRewardSequence < requestedRewardSequence then
                return 'INVALID_REWARD_SEQUENCE'
            end

            local eligibleMilliseconds = tonumber(redis.call('HGET', KEYS[3], 'eligibleMilliseconds')) or 0
            if eligibleMilliseconds < tonumber(ARGV[4]) then
                return 'NOT_CLAIMABLE'
            end

            local nextRewardSequence = currentRewardSequence + 1
            redis.call('HSET', KEYS[3],
                    'lastSeen', ARGV[5],
                    'eligibleMilliseconds', '0',
                    'rewardSequence', tostring(nextRewardSequence))
            redis.call('PEXPIRE', KEYS[2], ARGV[6])
            redis.call('PEXPIRE', KEYS[1], ARGV[7])
            redis.call('PEXPIRE', KEYS[3], ARGV[8])
            return 'SUCCESS:' .. tostring(nextRewardSequence)
            """, String.class);

    /**
     * 동일 경기 재입장 시 누적 상태를 유지하면서 최신 sessionKey로 Redis 키를 교체한다.
     */
    static final DefaultRedisScript<String> REPLACE_SESSION_KEY = new DefaultRedisScript<>("""
            local activeSessionKey = redis.call('GET', KEYS[1])
            if not activeSessionKey or activeSessionKey ~= ARGV[1] then
                return 'REPLACED'
            end

            if redis.call('EXISTS', KEYS[2]) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            if redis.call('EXISTS', KEYS[3]) == 0 then
                return 'EXPIRED'
            end

            if redis.call('HGET', KEYS[2], 'userId') ~= ARGV[3] then
                return 'USER_MISMATCH'
            end

            if redis.call('EXISTS', KEYS[4]) == 1 then
                return 'SESSION_KEY_CONFLICT'
            end

            redis.call('RENAME', KEYS[2], KEYS[4])
            redis.call('DEL', KEYS[3])
            redis.call('SET', KEYS[5], ARGV[4], 'PX', ARGV[5])
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[6])
            redis.call('PEXPIRE', KEYS[4], ARGV[7])
            return 'SUCCESS'
            """, String.class);

    /**
     * Heartbeat의 유효성 검증, 시청시간 누적, 세 Redis 키의 TTL 갱신을 원자적으로 처리한다.
     *
     * <p>Redis 키 입력:</p>
     * <ul>
     *     <li>{@code KEYS[1]}: {@code watch:switch-lock:{userId}}</li>
     *     <li>{@code KEYS[2]}: {@code watch:active:{userId}}</li>
     *     <li>{@code KEYS[3]}: {@code watch:alive:{userId}:{sessionKey}}</li>
     *     <li>{@code KEYS[4]}: {@code watch:session:{sessionKey}}</li>
     * </ul>
     *
     * <p>인자 입력:</p>
     * <ul>
     *     <li>{@code ARGV[1]}: 요청 sessionKey</li>
     *     <li>{@code ARGV[2]}: 요청 userId</li>
     *     <li>{@code ARGV[3]}: 요청 heartbeat sequence</li>
     *     <li>{@code ARGV[4]}: 서버 heartbeat 수신 시각(epoch milliseconds)</li>
     *     <li>{@code ARGV[5]}: 한 번에 적립할 최대 heartbeat 간격(milliseconds)</li>
     *     <li>{@code ARGV[6]}: 수령 가능 상태가 되는 누적시간(milliseconds)</li>
     *     <li>{@code ARGV[7]}: Alive TTL(milliseconds)</li>
     *     <li>{@code ARGV[8]}: Active TTL(milliseconds)</li>
     *     <li>{@code ARGV[9]}: Session TTL(milliseconds)</li>
     * </ul>
     *
     * <p>성공 시 {@code SUCCESS:eligibleMilliseconds:rewardSequence}, 실패 시
     * {@link HeartbeatResult} enum 이름을 반환한다.</p>
     * <ul>
     *     <li>{@code SUCCESS}: 시간 및 TTL 갱신 성공</li>
     *     <li>{@code SWITCHING}: 세션 전환 lock이 존재함</li>
     *     <li>{@code REPLACED}: 요청 sessionKey가 현재 active와 다름</li>
     *     <li>{@code EXPIRED}: Alive 키가 존재하지 않음</li>
     *     <li>{@code SESSION_NOT_FOUND}: Session Hash가 존재하지 않음</li>
     *     <li>{@code USER_MISMATCH}: Session Hash의 사용자와 요청 사용자가 다름</li>
     *     <li>{@code INVALID_SEQUENCE}: 요청 sequence가 마지막 처리값보다 크지 않음</li>
     * </ul>
     */
    static final DefaultRedisScript<String> HEARTBEAT = new DefaultRedisScript<>("""
            -- 세션 전환 중에는 기존 heartbeat의 추가 적립을 차단한다.
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 'SWITCHING'
            end

            -- 현재 사용자에게 적립 가능한 active 세션인지 확인한다.
            local activeSessionKey = redis.call('GET', KEYS[2])
            if not activeSessionKey or activeSessionKey ~= ARGV[1] then
                return 'REPLACED'
            end

            -- 이미 만료된 세션은 heartbeat로 다시 활성화하지 않는다.
            if redis.call('EXISTS', KEYS[3]) == 0 then
                return 'EXPIRED'
            end

            -- 최종 정산용 Session Hash가 없으면 상태를 갱신할 수 없다.
            if redis.call('EXISTS', KEYS[4]) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            -- 다른 사용자의 sessionKey로 heartbeat를 보내는 것을 거부한다.
            if redis.call('HGET', KEYS[4], 'userId') ~= ARGV[2] then
                return 'USER_MISMATCH'
            end

            -- 중복 또는 순서가 역전된 heartbeat를 거부한다.
            local previousSequence = tonumber(redis.call('HGET', KEYS[4], 'sequence')) or 0
            local requestSequence = tonumber(ARGV[3])
            if not requestSequence or requestSequence <= previousSequence then
                return 'INVALID_SEQUENCE'
            end

            -- 서버 시각 차이가 최대 인정 간격 이내인 경우에만 시청시간을 누적한다.
            local nowMillis = tonumber(ARGV[4])
            local previousLastSeen = tonumber(redis.call('HGET', KEYS[4], 'lastSeen'))
            local eligibleMilliseconds = tonumber(redis.call('HGET', KEYS[4], 'eligibleMilliseconds')) or 0
            local deltaMillis = nowMillis - previousLastSeen

            local claimIntervalMillis = tonumber(ARGV[6])
            if eligibleMilliseconds < claimIntervalMillis
                    and deltaMillis > 0 and deltaMillis <= tonumber(ARGV[5]) then
                eligibleMilliseconds = math.min(
                        eligibleMilliseconds + deltaMillis,
                        claimIntervalMillis)
            end

            local rewardSequence = tonumber(redis.call('HGET', KEYS[4], 'rewardSequence')) or 1

            -- 정상 heartbeat 상태와 세 Redis 키의 TTL을 함께 갱신한다.
            redis.call('HSET', KEYS[4],
                    'lastSeen', ARGV[4],
                    'eligibleMilliseconds', tostring(eligibleMilliseconds),
                    'sequence', ARGV[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[7])
            redis.call('PEXPIRE', KEYS[2], ARGV[8])
            redis.call('PEXPIRE', KEYS[4], ARGV[9])

            return 'SUCCESS:' .. tostring(eligibleMilliseconds)
                    .. ':' .. tostring(rewardSequence)
            """, String.class);

    /**
     * Redis String 값이 기대값과 일치할 때만 해당 키를 원자적으로 삭제한다.
     * Switch lock 해제와 active 키 조건부 삭제에 공통으로 사용한다.
     *
     * <p>입력:</p>
     * <ul>
     *     <li>{@code KEYS[1]}: 조건부로 삭제할 Redis String 키</li>
     *     <li>{@code ARGV[1]}: 현재 Redis 값과 비교할 기대값</li>
     * </ul>
     *
     * <p>반환:</p>
     * <ul>
     *     <li>{@code 1}: 값이 일치하여 키를 삭제함</li>
     *     <li>{@code 0}: 키가 없거나 값이 일치하지 않아 삭제하지 않음</li>
     * </ul>
     */
    static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>("""
            -- 다른 요청이 소유한 값을 삭제하지 않도록 현재 값을 먼저 비교한다.
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    /**
     * 인스턴스 생성을 막는다.
     */
    private WatchRedisScripts() {
    }
}
