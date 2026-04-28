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

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result GetTypeid() {
        // 1. 从 redis 查询缓存 (修正了 key 的拼写，保持规范)
        String key = "cache:shop-type";
        String shoptypejson = stringRedisTemplate.opsForValue().get(key);

        // 2. 🚨 核心修复：isNotBlank！有真实数据才去转换并返回
        if (StrUtil.isNotBlank(shoptypejson)) {
            List<ShopType> typeList = JSONUtil.toList(shoptypejson, ShopType.class);
            return Result.ok(typeList);
        }

        // 3. 如果 Redis 没命中，去查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 4. 数据库是否存在？ 不存在报错
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("分类不存在");
        }

        // 5. 存在，写入 Redis，然后返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}