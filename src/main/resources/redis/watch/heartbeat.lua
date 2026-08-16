-- heartbeat 검증, 시청시간 누적, TTL 갱신을 원자적으로 처리한다.
-- 세트가 실제 진행 중일 때만 canAccumulate가 true이며 수령 가능 상태에서는 시간을 고정한다.
local switchLockKey = KEYS[1]
local activeKey = KEYS[2]
local aliveKey = KEYS[3]
local sessionKey = KEYS[4]
local requestedSessionKey = ARGV[1]
local requestedUserId = ARGV[2]
local requestSequence = tonumber(ARGV[3])
local nowMillis = tonumber(ARGV[4])
local maxEligibleIntervalMillis = tonumber(ARGV[5])
local claimIntervalMillis = tonumber(ARGV[6])
local aliveTtlMillis = ARGV[7]
local activeTtlMillis = ARGV[8]
local sessionTtlMillis = ARGV[9]
local canAccumulate = ARGV[10] == '1'

if redis.call('EXISTS', switchLockKey) == 1 then
    return 'SWITCHING'
end

local activeSessionKey = redis.call('GET', activeKey)
if not activeSessionKey then
    return 'EXPIRED'
end

if activeSessionKey ~= requestedSessionKey then
    return 'REPLACED'
end

if redis.call('EXISTS', aliveKey) == 0 then
    return 'EXPIRED'
end

if redis.call('EXISTS', sessionKey) == 0 then
    return 'SESSION_NOT_FOUND'
end

if redis.call('HGET', sessionKey, 'userId') ~= requestedUserId then
    return 'USER_MISMATCH'
end

local previousSequence = tonumber(redis.call('HGET', sessionKey, 'sequence')) or 0
if not requestSequence or requestSequence <= previousSequence then
    return 'INVALID_SEQUENCE'
end

local previousLastSeen = tonumber(redis.call('HGET', sessionKey, 'lastSeen'))
local eligibleMilliseconds = tonumber(redis.call('HGET', sessionKey, 'eligibleMilliseconds')) or 0
local deltaMillis = nowMillis - previousLastSeen

if canAccumulate and eligibleMilliseconds < claimIntervalMillis
        and deltaMillis > 0 and deltaMillis <= maxEligibleIntervalMillis then
    eligibleMilliseconds = math.min(
            eligibleMilliseconds + deltaMillis,
            claimIntervalMillis)
end

local rewardSequence = tonumber(redis.call('HGET', sessionKey, 'rewardSequence')) or 1

redis.call('HSET', sessionKey,
        'lastSeen', tostring(nowMillis),
        'eligibleMilliseconds', tostring(eligibleMilliseconds),
        'sequence', tostring(requestSequence))
redis.call('PEXPIRE', aliveKey, aliveTtlMillis)
redis.call('PEXPIRE', activeKey, activeTtlMillis)
redis.call('PEXPIRE', sessionKey, sessionTtlMillis)

return 'SUCCESS:' .. tostring(eligibleMilliseconds)
        .. ':' .. tostring(rewardSequence)
