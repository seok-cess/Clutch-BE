-- 포인트 지급 전 수령 자격을 검증하고 DB 처리 중 만료되지 않도록 TTL을 연장한다.
-- KEYS: active, alive, session / ARGV: sessionKey, userId, rewardSequence, claimInterval, TTL 3종
local activeKey = KEYS[1]
local aliveKey = KEYS[2]
local sessionKey = KEYS[3]
local requestedSessionKey = ARGV[1]
local requestedUserId = ARGV[2]
local requestedRewardSequence = tonumber(ARGV[3])
local claimIntervalMillis = tonumber(ARGV[4])
local aliveTtlMillis = ARGV[5]
local activeTtlMillis = ARGV[6]
local sessionTtlMillis = ARGV[7]

if redis.call('GET', activeKey) ~= requestedSessionKey then
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

local currentRewardSequence = tonumber(redis.call('HGET', sessionKey, 'rewardSequence')) or 1
if currentRewardSequence ~= requestedRewardSequence then
    return 'INVALID_REWARD_SEQUENCE'
end

local eligibleMilliseconds = tonumber(redis.call('HGET', sessionKey, 'eligibleMilliseconds')) or 0
if eligibleMilliseconds < claimIntervalMillis then
    return 'NOT_CLAIMABLE'
end

redis.call('PEXPIRE', aliveKey, aliveTtlMillis)
redis.call('PEXPIRE', activeKey, activeTtlMillis)
redis.call('PEXPIRE', sessionKey, sessionTtlMillis)
return 'SUCCESS'
