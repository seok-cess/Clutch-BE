local claimedUsersKey = KEYS[1]
local claimedUserCount = tonumber(ARGV[1])

for index = 1, #KEYS do
    redis.call('DEL', KEYS[index])
end

for index = 1, claimedUserCount do
    redis.call('SADD', claimedUsersKey, ARGV[index + 1])
end

local stockOffset = claimedUserCount + 1

for keyIndex = 2, #KEYS do
    local stockArgumentIndex = stockOffset + keyIndex - 1
    redis.call('SET', KEYS[keyIndex], ARGV[stockArgumentIndex])
end

return #KEYS
