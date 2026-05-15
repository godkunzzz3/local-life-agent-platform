package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryByID(Long id);
    Result update(Shop shop);

    /**
     * 按分类查询店铺列表。
     *
     * <p>带经纬度时优先走 Redis GEO 附近商铺查询；不带经纬度时走普通分页。
     * sortBy 用于支持前端按人气、评分等字段排序。</p>
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, String sortBy);
}
