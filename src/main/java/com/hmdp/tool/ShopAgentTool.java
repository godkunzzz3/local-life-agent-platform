package com.hmdp.tool;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 店铺 Agent 工具。
 *
 * <p>后续接入 LangChain4j / Spring AI 时，这类 Tool 可以直接暴露给模型调用。
 * 当前先作为普通 Spring 组件给 Facade 使用，把店铺查询和画像组装从编排层拆出来。</p>
 */
@Component
public class ShopAgentTool {

    @Resource
    private IShopService shopService;

    /**
     * 查询店铺实体。
     */
    public Shop getShop(Long shopId) {
        return shopService.getById(shopId);
    }

    /**
     * 构建面向 Agent 的店铺画像。
     */
    public Map<String, Object> buildShopProfile(Shop shop) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", shop.getName());
        profile.put("typeId", shop.getTypeId());
        profile.put("area", shop.getArea());
        profile.put("address", shop.getAddress());
        profile.put("avgPrice", shop.getAvgPrice());
        profile.put("score", shop.getScore());
        profile.put("sold", shop.getSold());
        profile.put("comments", shop.getComments());
        profile.put("openHours", shop.getOpenHours());
        return profile;
    }
}
