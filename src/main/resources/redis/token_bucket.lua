-- KEYS[1]: key của client
-- ARGV[1]: capacity, ARGV[2]: refillPerSecond, ARGV[3]: now (ms)
-- Trả về {allowed(1/0), remaining, retryAfterMs}
local key          = KEYS[1]
local capacity     = tonumber(ARGV[1])
local refillPerSec = tonumber(ARGV[2])
local now          = tonumber(ARGV[3])

local s      = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(s[1])
local ts     = tonumber(s[2])

if tokens == nil then
  tokens = capacity      -- key mới: xô đầy
  ts = now
end

local elapsedMs = math.max(0, now - ts)
tokens = math.min(capacity, tokens + (elapsedMs / 1000.0) * refillPerSec)
ts = math.max(ts, now)   -- mốc nạp không bao giờ lùi

-- sau khoảng thời gian đủ để nạp đầy xô, trạng thái giống hệt key mới
local ttlMs = math.ceil(capacity / refillPerSec * 1000) + 1000

local allowed = 0
if tokens >= 1.0 then
  tokens = tokens - 1.0
  allowed = 1
end

redis.call('HSET', key, 'tokens', string.format('%.17g', tokens), 'ts', ts)
redis.call('PEXPIRE', key, ttlMs)

if allowed == 1 then
  return {1, math.floor(tokens), 0}
end
return {0, 0, math.ceil((1.0 - tokens) / refillPerSec * 1000)}
