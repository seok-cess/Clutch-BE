local stockKey = KEYS[1]
local claimedUsersKey = KEYS[2]
local userId = ARGV[1]
local operation = ARGV[2] or 'CLAIM'

if operation == 'ROLLBACK' then
	local removed = redis.call(
		'SREM',
		claimedUsersKey,
		userId
	)

	if removed == 1 then
		redis.call('INCR', stockKey)
		return 1
	end

	return 0
end

local stock = redis.call('GET', stockKey)

if not stock then
	return -3
end

if redis.call(
	'SISMEMBER',
	claimedUsersKey,
	userId
) == 1 then
	return -1
end

if tonumber(stock) <= 0 then
	return -2
end

redis.call('DECR', stockKey)
redis.call('SADD', claimedUsersKey, userId)

return 1