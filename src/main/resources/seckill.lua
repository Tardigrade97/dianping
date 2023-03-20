-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 脚本业务
if tonumber(redis.call('get', stockKey)) <= 0 then
    -- 库存不足
    return 1
end
-- 判断用户是否下单
if redis.call('sismember', orderKey, userId) == 1 then
    -- 已经下单
    return 2
end
-- 扣减库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 添加用户到已下单集合 sadd orderKey userId
redis.call('sadd', orderKey, userId)
return 0