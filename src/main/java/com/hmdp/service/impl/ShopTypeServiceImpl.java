package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Tardigrade
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeByList() {
        // 1. 从redis中查询缓存,返回String格式的json。
        String shopTypeJson = stringRedisTemplate.opsForValue().get("shop-type");
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 如果存在json,反序列化为 对象List并返回。
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 2. 没有命中，查询数据库.
        List<ShopType> sort = query().orderByAsc("sort").list();
        if (sort == null) {
            stringRedisTemplate.opsForValue().set("shop-type","");
            return Result.fail("分类不存在");
        }
        stringRedisTemplate.opsForValue().set("shop-type",JSONUtil.toJsonStr(sort),30, TimeUnit.MINUTES);

        return Result.ok(sort);
    }
}
