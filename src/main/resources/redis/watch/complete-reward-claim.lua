-- DB 지급이 끝난 회차를 원자적으로 초기화하고 다음 수령 회차를 시작한다.
-- 이미 반영된 같은 요청은 ALREADY_COMPLETED로 반환해 멱등성을 보장한다.
local activeKey = KEYS[1]
local aliveKey = KEYS[2]
local sessionKey = KEYS[3]
local claimedSessionKey = ARGV[1]
local claimedUserId = ARGV[2]
local requestedRewardSequence = tonumber(ARGV[3])
local claimIntervalMillis = tonumber(ARGV[4])
local claimedAtMillis = ARGV[5]
local aliveTtlMillis = ARGV[6]
local activeTtlMillis = ARGV[7]
local sessionTtlMillis = ARGV[8]

if redis.call('GET', activeKey) ~= claimedSessionKey then
    return 'REPLACED'
end

if redis.call('EXISTS', aliveKey) == 0 then
    return 'EXPIRED'
end

if redis.call('EXISTS', sessionKey) == 0 then
    return 'SESSION_NOT_FOUND'
end

if redis.call('HGET', sessionKey, 'userId') ~= claimedUserId then
    return 'USER_MISMATCH'
end

local currentRewardSequence = tonumber(redis.call('HGET', sessionKey, 'rewardSequence')) or 1
if currentRewardSequence > requestedRewardSequence then
    return 'ALREADY_COMPLETED:' .. tostring(currentRewardSequence)
end

if currentRewardSequence < requestedRewardSequence then
    return 'INVALID_REWARD_SEQUENCE'
end

local eligibleMilliseconds = tonumber(redis.call('HGET', sessionKey, 'eligibleMilliseconds')) or 0
if eligibleMilliseconds < claimIntervalMillis then
    return 'NOT_CLAIMABLE'
end

local nextRewardSequence = currentRewardSequence + 1
redis.call('HSET', sessionKey,
        'lastSeen', claimedAtMillis,
        'eligibleMilliseconds', '0',
        'rewardSequence', tostring(nextRewardSequence))
redis.call('PEXPIRE', aliveKey, aliveTtlMillis)
redis.call('PEXPIRE', activeKey, activeTtlMillis)
redis.call('PEXPIRE', sessionKey, sessionTtlMillis)
return 'SUCCESS:' .. tostring(nextRewardSequence)
