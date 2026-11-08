-- KEYS[1]: key của client
-- ARGV[1]: limit, ARGV[2]: windowMs, ARGV[3]: now (ms)
-- Trả về {allowed(1/0), remaining, retryAfterMs}
local key      = KEYS[1]
local limit    = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local currStart = now - (now % windowMs)

local s    = redis.call('HMGET', key, 'cs', 'prev', 'curr')
local cs   = tonumber(s[1])
local prev = tonumber(s[2]) or 0
local curr = tonumber(s[3]) or 0

if cs == nil then
  cs = currStart
  prev = 0
  curr = 0
elseif cs ~= currStart then
  if currStart - cs == windowMs then
    prev = curr          -- cửa sổ liền kề
  else
    prev = 0             -- đã bỏ qua >= 1 cửa sổ trống
  end
  curr = 0
  cs = currStart
end

local ratio     = (now - currStart) / windowMs
local estimated = prev * (1 - ratio) + curr

if estimated < limit then
  curr = curr + 1
  redis.call('HSET', key, 'cs', cs, 'prev', prev, 'curr', curr)
  redis.call('PEXPIRE', key, windowMs * 2)
  local remaining = math.floor(limit - (estimated + 1))
  if remaining < 0 then remaining = 0 end
  return {1, remaining, 0}
end

return {0, 0, currStart + windowMs - now + 1}
