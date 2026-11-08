-- KEYS[1]: key của client
-- ARGV[1]: limit, ARGV[2]: windowMs, ARGV[3]: now (ms)
-- Trả về {allowed(1/0), remaining, retryAfterMs}
local key      = KEYS[1]
local limit    = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local windowStart = now - (now % windowMs)

local state = redis.call('HMGET', key, 'ws', 'count')
local ws    = tonumber(state[1])
local count = tonumber(state[2])

if ws == nil or ws ~= windowStart then
	ws = windowStart
	count = 0
end

if count < limit then
	count = count + 1
	redis.call('HSET', key, 'ws', ws, 'count', count)
	redis.call('PEXPIRE', key, windowMs)
	return {1, limit - count, 0}
end

return {0, 0, ws + windowMs - now}
