-- 현재 값이 기대값과 같을 때만 키를 삭제해 다른 요청의 lock 또는 active 값을 보호한다.
local targetKey = KEYS[1]
local expectedValue = ARGV[1]

if redis.call('GET', targetKey) == expectedValue then
    return redis.call('DEL', targetKey)
end
return 0
