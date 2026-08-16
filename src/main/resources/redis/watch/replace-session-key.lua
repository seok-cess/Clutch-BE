-- 동일 경기 재입장 시 누적 상태를 유지하면서 최신 sessionKey로 Redis 키를 교체한다.
-- 전환 중 이전 화면의 heartbeat가 적립되지 않도록 모든 변경을 한 명령으로 처리한다.
local activeKey = KEYS[1]
local oldSessionHashKey = KEYS[2]
local oldAliveKey = KEYS[3]
local newSessionHashKey = KEYS[4]
local newAliveKey = KEYS[5]
local oldSessionKey = ARGV[1]
local newSessionKey = ARGV[2]
local requestedUserId = ARGV[3]
local aliveValue = ARGV[4]
local aliveTtlMillis = ARGV[5]
local activeTtlMillis = ARGV[6]
local sessionTtlMillis = ARGV[7]

local activeSessionKey = redis.call('GET', activeKey)
if not activeSessionKey then
    return 'EXPIRED'
end

if activeSessionKey ~= oldSessionKey then
    return 'REPLACED'
end

if redis.call('EXISTS', oldSessionHashKey) == 0 then
    return 'SESSION_NOT_FOUND'
end

if redis.call('EXISTS', oldAliveKey) == 0 then
    return 'EXPIRED'
end

if redis.call('HGET', oldSessionHashKey, 'userId') ~= requestedUserId then
    return 'USER_MISMATCH'
end

if redis.call('EXISTS', newSessionHashKey) == 1 then
    return 'SESSION_KEY_CONFLICT'
end

redis.call('RENAME', oldSessionHashKey, newSessionHashKey)
redis.call('PEXPIRE', newSessionHashKey, sessionTtlMillis)
redis.call('DEL', oldAliveKey)
redis.call('SET', newAliveKey, aliveValue, 'PX', aliveTtlMillis)
redis.call('SET', activeKey, newSessionKey, 'PX', activeTtlMillis)
return 'SUCCESS'
