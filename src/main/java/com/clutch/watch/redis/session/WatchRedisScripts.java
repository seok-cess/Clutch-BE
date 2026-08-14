package com.clutch.watch.redis.session;

import com.clutch.watch.redis.heartbeat.HeartbeatResult;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 시청 세션 Redis 작업을 원자적으로 처리하는 Lua 스크립트 모음.
 */
final class WatchRedisScripts {

    /**
     * 포인트 지급 전에 수령 자격을 원자적으로 확인하고 세션 TTL을 연장한다.
     * 이 스크립트는 상태를 다음 회차로 변경하지 않으며, DB 지급 트랜잭션을 시작해도 되는지만 검증한다.
     * 검증과 TTL 연장을 한 명령으로 처리하여 검증 직후 세션이 만료되는 것을 방지한다.
     *
     * <p>Redis 키 입력:</p>
     * <ul>
     *     <li>{@code KEYS[1]}: {@code watch:active:{userId}} — 사용자의 현재 활성 sessionKey</li>
     *     <li>{@code KEYS[2]}: {@code watch:alive:{userId}:{sessionKey}} — heartbeat 생존 키</li>
     *     <li>{@code KEYS[3]}: {@code watch:session:{sessionKey}} — 누적시간과 수령 회차를 가진 Hash</li>
     * </ul>
     *
     * <p>인자 입력:</p>
     * <ul>
     *     <li>{@code ARGV[1]}: 수령 요청의 sessionKey</li>
     *     <li>{@code ARGV[2]}: 수령 요청의 userId</li>
     *     <li>{@code ARGV[3]}: 수령 요청의 rewardSequence</li>
     *     <li>{@code ARGV[4]}: 수령 가능 상태가 되는 누적시간(milliseconds)</li>
     *     <li>{@code ARGV[5]}: Alive TTL(milliseconds)</li>
     *     <li>{@code ARGV[6]}: Active TTL(milliseconds)</li>
     *     <li>{@code ARGV[7]}: Session TTL(milliseconds)</li>
     * </ul>
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>{@code SUCCESS}: 현재 활성 세션이며 요청 회차가 수령 가능함</li>
     *     <li>{@code REPLACED}: active 값이 요청 sessionKey와 다름</li>
     *     <li>{@code EXPIRED}: Alive 키가 만료됨</li>
     *     <li>{@code SESSION_NOT_FOUND}: Session Hash가 없음</li>
     *     <li>{@code USER_MISMATCH}: Session Hash의 userId가 요청 사용자와 다름</li>
     *     <li>{@code INVALID_REWARD_SEQUENCE}: 현재 수령 회차와 요청 회차가 다름</li>
     *     <li>{@code NOT_CLAIMABLE}: 누적시간이 수령 기준에 도달하지 않음</li>
     * </ul>
    */
    static final DefaultRedisScript<String> PREPARE_REWARD_CLAIM = new DefaultRedisScript<>("""
            local activeKey = KEYS[1]                    -- watch:active:{userId}
            local aliveKey = KEYS[2]                     -- watch:alive:{userId}:{sessionKey}
            local sessionKey = KEYS[3]                   -- watch:session:{sessionKey}
            local requestedSessionKey = ARGV[1]          -- 수령 요청 sessionKey
            local requestedUserId = ARGV[2]              -- 수령 요청 userId
            local requestedRewardSequence = tonumber(ARGV[3]) -- 수령 요청 rewardSequence
            local claimIntervalMillis = tonumber(ARGV[4]) -- 수령 기준 누적시간(ms)
            local aliveTtlMillis = ARGV[5]               -- Alive TTL(ms)
            local activeTtlMillis = ARGV[6]              -- Active TTL(ms)
            local sessionTtlMillis = ARGV[7]             -- Session TTL(ms)

            -- 다른 화면으로 교체된 세션에서는 포인트를 수령할 수 없다.
            if redis.call('GET', activeKey) ~= requestedSessionKey then
                return 'REPLACED'
            end

            -- heartbeat가 끊겨 이미 만료된 세션을 수령 요청으로 되살리지 않는다.
            if redis.call('EXISTS', aliveKey) == 0 then
                return 'EXPIRED'
            end

            -- 누적시간과 회차를 검증할 Session Hash가 반드시 존재해야 한다.
            if redis.call('EXISTS', sessionKey) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            -- 다른 사용자의 sessionKey를 이용한 수령 요청을 거부한다.
            if redis.call('HGET', sessionKey, 'userId') ~= requestedUserId then
                return 'USER_MISMATCH'
            end

            -- 프론트가 요청한 회차가 Redis의 현재 수령 회차와 정확히 일치해야 한다.
            local currentRewardSequence = tonumber(redis.call('HGET', sessionKey, 'rewardSequence')) or 1
            if currentRewardSequence ~= requestedRewardSequence then
                return 'INVALID_REWARD_SEQUENCE'
            end

            -- 현재 회차의 유효 시청시간이 수령 기준에 도달했는지 확인한다.
            local eligibleMilliseconds = tonumber(redis.call('HGET', sessionKey, 'eligibleMilliseconds')) or 0
            if eligibleMilliseconds < claimIntervalMillis then
                return 'NOT_CLAIMABLE'
            end

            -- DB 지급 처리 중 세션이 만료되지 않도록 관련 키의 TTL만 갱신한다.
            redis.call('PEXPIRE', aliveKey, aliveTtlMillis)
            redis.call('PEXPIRE', activeKey, activeTtlMillis)
            redis.call('PEXPIRE', sessionKey, sessionTtlMillis)
            return 'SUCCESS'
            """, String.class);

    /**
     * DB 포인트 지급이 확정된 회차의 누적시간을 초기화하고 다음 회차를 시작한다.
     * DB 지급 이후에 실행되므로 동일 요청 재시도 시 이미 다음 회차로 변경된 상태도 성공으로 해석한다.
     * 누적시간 초기화, 다음 회차 증가, 기준 시각 변경과 TTL 갱신을 원자적으로 처리한다.
     *
     * <p>Redis 키 입력:</p>
     * <ul>
     *     <li>{@code KEYS[1]}: {@code watch:active:{userId}} — 사용자의 현재 활성 sessionKey</li>
     *     <li>{@code KEYS[2]}: {@code watch:alive:{userId}:{sessionKey}} — heartbeat 생존 키</li>
     *     <li>{@code KEYS[3]}: {@code watch:session:{sessionKey}} — 누적시간과 수령 회차를 가진 Hash</li>
     * </ul>
     *
     * <p>인자 입력:</p>
     * <ul>
     *     <li>{@code ARGV[1]}: 지급이 완료된 sessionKey</li>
     *     <li>{@code ARGV[2]}: 지급 대상 userId</li>
     *     <li>{@code ARGV[3]}: DB 지급이 완료된 rewardSequence</li>
     *     <li>{@code ARGV[4]}: 수령 가능 상태가 되는 누적시간(milliseconds)</li>
     *     <li>{@code ARGV[5]}: DB 지급 완료 시각(epoch milliseconds)</li>
     *     <li>{@code ARGV[6]}: Alive TTL(milliseconds)</li>
     *     <li>{@code ARGV[7]}: Active TTL(milliseconds)</li>
     *     <li>{@code ARGV[8]}: Session TTL(milliseconds)</li>
     * </ul>
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>{@code SUCCESS:nextRewardSequence}: 초기화와 다음 회차 전환 성공</li>
     *     <li>{@code ALREADY_COMPLETED:currentRewardSequence}: 요청 회차가 이미 반영됨</li>
     *     <li>{@code REPLACED}: active 값이 요청 sessionKey와 다름</li>
     *     <li>{@code EXPIRED}: Alive 키가 만료됨</li>
     *     <li>{@code SESSION_NOT_FOUND}: Session Hash가 없음</li>
     *     <li>{@code USER_MISMATCH}: Session Hash의 userId가 지급 대상과 다름</li>
     *     <li>{@code INVALID_REWARD_SEQUENCE}: Redis 회차가 요청 회차보다 이전임</li>
     *     <li>{@code NOT_CLAIMABLE}: 누적시간이 수령 기준에 도달하지 않음</li>
     * </ul>
    */
    static final DefaultRedisScript<String> COMPLETE_REWARD_CLAIM = new DefaultRedisScript<>("""
            local activeKey = KEYS[1]                    -- watch:active:{userId}
            local aliveKey = KEYS[2]                     -- watch:alive:{userId}:{sessionKey}
            local sessionKey = KEYS[3]                   -- watch:session:{sessionKey}
            local claimedSessionKey = ARGV[1]            -- 지급 완료 sessionKey
            local claimedUserId = ARGV[2]                -- 지급 대상 userId
            local requestedRewardSequence = tonumber(ARGV[3]) -- DB 지급 완료 rewardSequence
            local claimIntervalMillis = tonumber(ARGV[4]) -- 수령 기준 누적시간(ms)
            local claimedAtMillis = ARGV[5]              -- DB 지급 완료 시각(epoch ms)
            local aliveTtlMillis = ARGV[6]               -- Alive TTL(ms)
            local activeTtlMillis = ARGV[7]              -- Active TTL(ms)
            local sessionTtlMillis = ARGV[8]             -- Session TTL(ms)

            -- 지급 처리 중 다른 화면으로 교체된 경우 기존 세션 상태를 변경하지 않는다.
            if redis.call('GET', activeKey) ~= claimedSessionKey then
                return 'REPLACED'
            end

            -- heartbeat가 끊긴 세션을 지급 완료 요청으로 다시 활성화하지 않는다.
            if redis.call('EXISTS', aliveKey) == 0 then
                return 'EXPIRED'
            end

            -- 다음 회차로 변경할 Session Hash가 반드시 존재해야 한다.
            if redis.call('EXISTS', sessionKey) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            -- 다른 사용자의 세션 상태를 변경하지 않는다.
            if redis.call('HGET', sessionKey, 'userId') ~= claimedUserId then
                return 'USER_MISMATCH'
            end

            -- 현재 회차가 더 크면 동일 지급 요청이 이미 반영된 것이므로 멱등 성공으로 처리한다.
            local currentRewardSequence = tonumber(redis.call('HGET', sessionKey, 'rewardSequence')) or 1
            if currentRewardSequence > requestedRewardSequence then
                return 'ALREADY_COMPLETED:' .. tostring(currentRewardSequence)
            end

            -- Redis 회차가 요청 회차보다 이전이면 순서를 건너뛴 잘못된 요청이다.
            if currentRewardSequence < requestedRewardSequence then
                return 'INVALID_REWARD_SEQUENCE'
            end

            -- 지급 전 수령 자격 검증과 동일한 누적시간 조건을 다시 확인한다.
            local eligibleMilliseconds = tonumber(redis.call('HGET', sessionKey, 'eligibleMilliseconds')) or 0
            if eligibleMilliseconds < claimIntervalMillis then
                return 'NOT_CLAIMABLE'
            end

            -- 지급 완료 시각부터 다음 5분을 누적하도록 시간과 회차를 함께 초기화한다.
            local nextRewardSequence = currentRewardSequence + 1
            redis.call('HSET', sessionKey,
                    'lastSeen', claimedAtMillis,
                    'eligibleMilliseconds', '0',
                    'rewardSequence', tostring(nextRewardSequence))
            redis.call('PEXPIRE', aliveKey, aliveTtlMillis)
            redis.call('PEXPIRE', activeKey, activeTtlMillis)
            redis.call('PEXPIRE', sessionKey, sessionTtlMillis)
            return 'SUCCESS:' .. tostring(nextRewardSequence)
            """, String.class);

    /**
     * 동일 경기 재입장 시 누적 상태를 유지하면서 최신 sessionKey로 Redis 키를 교체한다.
     * Session Hash 자체를 RENAME하여 누적시간, heartbeat sequence와 rewardSequence를 그대로 보존하고,
     * active와 alive 키를 새 sessionKey로 변경하여 이전 화면의 요청을 차단한다.
     *
     * <p>Redis 키 입력:</p>
     * <ul>
     *     <li>{@code KEYS[1]}: {@code watch:active:{userId}}</li>
     *     <li>{@code KEYS[2]}: {@code watch:session:{oldSessionKey}}</li>
     *     <li>{@code KEYS[3]}: {@code watch:alive:{userId}:{oldSessionKey}}</li>
     *     <li>{@code KEYS[4]}: {@code watch:session:{newSessionKey}}</li>
     *     <li>{@code KEYS[5]}: {@code watch:alive:{userId}:{newSessionKey}}</li>
     * </ul>
     *
     * <p>인자 입력:</p>
     * <ul>
     *     <li>{@code ARGV[1]}: 이전 sessionKey</li>
     *     <li>{@code ARGV[2]}: 새 sessionKey</li>
     *     <li>{@code ARGV[3]}: 요청 userId</li>
     *     <li>{@code ARGV[4]}: 새 Alive 키에 저장할 값</li>
     *     <li>{@code ARGV[5]}: Alive TTL(milliseconds)</li>
     *     <li>{@code ARGV[6]}: Active TTL(milliseconds)</li>
     *     <li>{@code ARGV[7]}: Session TTL(milliseconds)</li>
     * </ul>
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>{@code SUCCESS}: 상태 보존 및 sessionKey 교체 성공</li>
     *     <li>{@code EXPIRED}: active 키가 없거나 이전 Alive 키가 만료됨</li>
     *     <li>{@code REPLACED}: active 값이 이전 sessionKey와 다름</li>
     *     <li>{@code SESSION_NOT_FOUND}: 이전 Session Hash가 없음</li>
     *     <li>{@code USER_MISMATCH}: Session Hash의 userId가 요청 사용자와 다름</li>
     *     <li>{@code SESSION_KEY_CONFLICT}: 새 sessionKey의 Session Hash가 이미 존재함</li>
     * </ul>
    */
    static final DefaultRedisScript<String> REPLACE_SESSION_KEY = new DefaultRedisScript<>("""
            local activeKey = KEYS[1]                    -- watch:active:{userId}
            local oldSessionHashKey = KEYS[2]            -- watch:session:{oldSessionKey}
            local oldAliveKey = KEYS[3]                  -- watch:alive:{userId}:{oldSessionKey}
            local newSessionHashKey = KEYS[4]            -- watch:session:{newSessionKey}
            local newAliveKey = KEYS[5]                  -- watch:alive:{userId}:{newSessionKey}
            local oldSessionKey = ARGV[1]                -- 이전 sessionKey
            local newSessionKey = ARGV[2]                -- 새 sessionKey
            local requestedUserId = ARGV[3]              -- 재입장 요청 userId
            local aliveValue = ARGV[4]                   -- 새 Alive 키에 저장할 값
            local aliveTtlMillis = ARGV[5]               -- Alive TTL(ms)
            local activeTtlMillis = ARGV[6]              -- Active TTL(ms)
            local sessionTtlMillis = ARGV[7]             -- Session TTL(ms)

            -- active 키가 없으면 사용자의 활성 시청 세션이 만료된 상태다.
            local activeSessionKey = redis.call('GET', activeKey)
            if not activeSessionKey then
                return 'EXPIRED'
            end

            -- active 값이 다르면 다른 화면 또는 세션이 이미 최신 세션으로 교체된 상태다.
            if activeSessionKey ~= oldSessionKey then
                return 'REPLACED'
            end

            -- 보존하여 옮길 이전 Session Hash가 존재해야 한다.
            if redis.call('EXISTS', oldSessionHashKey) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            -- 이미 만료된 세션은 재입장으로 이어받지 않는다.
            if redis.call('EXISTS', oldAliveKey) == 0 then
                return 'EXPIRED'
            end

            -- 다른 사용자의 세션 상태를 가져오지 않는다.
            if redis.call('HGET', oldSessionHashKey, 'userId') ~= requestedUserId then
                return 'USER_MISMATCH'
            end

            -- 새 sessionKey가 이미 사용 중이면 기존 값을 덮어쓰지 않는다.
            if redis.call('EXISTS', newSessionHashKey) == 1 then
                return 'SESSION_KEY_CONFLICT'
            end

            -- Hash를 이름만 바꿔 전체 누적 상태를 보존하고 active/alive를 새 키로 교체한다.
            redis.call('RENAME', oldSessionHashKey, newSessionHashKey)
            redis.call('PEXPIRE', newSessionHashKey, sessionTtlMillis)
            redis.call('DEL', oldAliveKey)
            redis.call('SET', newAliveKey, aliveValue, 'PX', aliveTtlMillis)
            redis.call('SET', activeKey, newSessionKey, 'PX', activeTtlMillis)
            return 'SUCCESS'
            """, String.class);

    /**
     * Heartbeat의 유효성 검증, 시청시간 누적, 세 Redis 키의 TTL 갱신을 원자적으로 처리한다.
     * 검증 중 하나라도 실패하면 누적시간, heartbeat sequence와 TTL을 변경하지 않는다.
     * 수령 기준에 도달한 뒤에는 누적시간을 고정하되 heartbeat sequence와 TTL은 계속 갱신한다.
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
     *     <li>{@code EXPIRED}: Active 또는 Alive 키가 존재하지 않음</li>
     *     <li>{@code SESSION_NOT_FOUND}: Session Hash가 존재하지 않음</li>
     *     <li>{@code USER_MISMATCH}: Session Hash의 사용자와 요청 사용자가 다름</li>
     *     <li>{@code INVALID_SEQUENCE}: 요청 sequence가 마지막 처리값보다 크지 않음</li>
     * </ul>
    */
    static final DefaultRedisScript<String> HEARTBEAT = new DefaultRedisScript<>("""
            local switchLockKey = KEYS[1]                -- watch:switch-lock:{userId}
            local activeKey = KEYS[2]                    -- watch:active:{userId}
            local aliveKey = KEYS[3]                     -- watch:alive:{userId}:{sessionKey}
            local sessionKey = KEYS[4]                   -- watch:session:{sessionKey}
            local requestedSessionKey = ARGV[1]          -- heartbeat 요청 sessionKey
            local requestedUserId = ARGV[2]              -- heartbeat 요청 userId
            local requestSequence = tonumber(ARGV[3])    -- heartbeat 요청 sequence
            local nowMillis = tonumber(ARGV[4])          -- 서버 수신 시각(epoch ms)
            local maxEligibleIntervalMillis = tonumber(ARGV[5]) -- 최대 인정 간격(ms)
            local claimIntervalMillis = tonumber(ARGV[6]) -- 수령 기준 누적시간(ms)
            local aliveTtlMillis = ARGV[7]               -- Alive TTL(ms)
            local activeTtlMillis = ARGV[8]              -- Active TTL(ms)
            local sessionTtlMillis = ARGV[9]             -- Session TTL(ms)

            -- 세션 전환 중에는 기존 heartbeat의 추가 적립을 차단한다.
            if redis.call('EXISTS', switchLockKey) == 1 then
                return 'SWITCHING'
            end

            -- active 키가 없으면 사용자의 활성 시청 세션이 만료된 상태다.
            local activeSessionKey = redis.call('GET', activeKey)
            if not activeSessionKey then
                return 'EXPIRED'
            end

            -- active 값이 다르면 다른 화면 또는 세션이 이미 최신 세션으로 교체된 상태다.
            if activeSessionKey ~= requestedSessionKey then
                return 'REPLACED'
            end

            -- 이미 만료된 세션은 heartbeat로 다시 활성화하지 않는다.
            if redis.call('EXISTS', aliveKey) == 0 then
                return 'EXPIRED'
            end

            -- 최종 정산용 Session Hash가 없으면 상태를 갱신할 수 없다.
            if redis.call('EXISTS', sessionKey) == 0 then
                return 'SESSION_NOT_FOUND'
            end

            -- 다른 사용자의 sessionKey로 heartbeat를 보내는 것을 거부한다.
            if redis.call('HGET', sessionKey, 'userId') ~= requestedUserId then
                return 'USER_MISMATCH'
            end

            -- 중복 또는 순서가 역전된 heartbeat를 거부한다.
            local previousSequence = tonumber(redis.call('HGET', sessionKey, 'sequence')) or 0
            if not requestSequence or requestSequence <= previousSequence then
                return 'INVALID_SEQUENCE'
            end

            -- 서버 시각 차이가 최대 인정 간격 이내인 경우에만 시청시간을 누적한다.
            local previousLastSeen = tonumber(redis.call('HGET', sessionKey, 'lastSeen'))
            local eligibleMilliseconds = tonumber(redis.call('HGET', sessionKey, 'eligibleMilliseconds')) or 0
            local deltaMillis = nowMillis - previousLastSeen

            if eligibleMilliseconds < claimIntervalMillis
                    and deltaMillis > 0 and deltaMillis <= maxEligibleIntervalMillis then
                eligibleMilliseconds = math.min(
                        eligibleMilliseconds + deltaMillis,
                        claimIntervalMillis)
            end

            local rewardSequence = tonumber(redis.call('HGET', sessionKey, 'rewardSequence')) or 1

            -- 정상 heartbeat 상태와 세 Redis 키의 TTL을 함께 갱신한다.
            redis.call('HSET', sessionKey,
                    'lastSeen', tostring(nowMillis),
                    'eligibleMilliseconds', tostring(eligibleMilliseconds),
                    'sequence', tostring(requestSequence))
            redis.call('PEXPIRE', aliveKey, aliveTtlMillis)
            redis.call('PEXPIRE', activeKey, activeTtlMillis)
            redis.call('PEXPIRE', sessionKey, sessionTtlMillis)

            return 'SUCCESS:' .. tostring(eligibleMilliseconds)
                    .. ':' .. tostring(rewardSequence)
            """, String.class);

    /**
     * Redis String 값이 기대값과 일치할 때만 해당 키를 원자적으로 삭제한다.
     * Switch lock 해제와 active 키 조건부 삭제에 공통으로 사용한다.
     * GET과 DEL을 하나의 스크립트에서 실행하여 값 확인 직후 다른 요청이 값을 교체하는 경쟁 조건을 방지한다.
     *
     * <p>Redis 키 입력:</p>
     * <ul>
     *     <li>{@code KEYS[1]}: 조건부로 삭제할 Redis String 키.
     *         {@code watch:switch-lock:{userId}} 또는 {@code watch:active:{userId}}</li>
     * </ul>
     *
     * <p>인자 입력:</p>
     * <ul>
     *     <li>{@code ARGV[1]}: 현재 Redis 값과 비교할 기대값.
     *         lock 해제 시 lockToken, active 삭제 시 sessionKey</li>
     * </ul>
     *
     * <p>반환값:</p>
     * <ul>
     *     <li>{@code 1}: 값이 일치하여 키를 삭제함</li>
     *     <li>{@code 0}: 키가 없거나 값이 일치하지 않아 삭제하지 않음</li>
     * </ul>
     */
    static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>("""
            local targetKey = KEYS[1]       -- 조건부 삭제 대상: switch-lock 키 또는 active 키
            local expectedValue = ARGV[1]   -- 비교값: lockToken 또는 sessionKey

            -- 다른 요청이 소유한 값을 삭제하지 않도록 현재 값을 먼저 비교한다.
            if redis.call('GET', targetKey) == expectedValue then
                return redis.call('DEL', targetKey)
            end
            return 0
            """, Long.class);

    /**
     * 인스턴스 생성을 막는다.
     */
    private WatchRedisScripts() {
    }
}
