package com.hmdp.tool;

import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.dto.ShopProfileDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 店铺 Agent 工具。
 *
 * <p>后续接入 LangChain4j / Spring AI 时，这类 Tool 可以直接暴露给模型调用。
 * 当前先作为普通 Spring 组件给 Facade 使用，把店铺查询和画像组装从编排层拆出来。</p>
 */
@Component
public class ShopAgentTool implements AgentToolDescriptor {

    @Resource
    private IShopService shopService;

    @Override
    public AgentToolDefinitionDTO definition() {
        return new AgentToolDefinitionDTO()
                .setName("shop_profile_tool")
                .setDisplayName("店铺画像工具")
                .setDescription("查询店铺基础信息，并组装成 Agent 可理解的店铺画像。")
                .setCategory("shop")
                .setToolType("readonly")
                .setAccessLevel("read")
                .setRequireMerchantConfirm(false)
                .setWriteDatabase(false)
                .setModelCallable(true)
                .setExecutionPolicy("direct")
                .setConfirmReason("只读取当前店铺公开经营信息，不涉及写操作。")
                .setInputSchema("{\"shopId\":\"店铺ID\"}")
                .setOutputSchema("ShopProfileDTO：店铺名称、分类、商圈、地址、均价、评分、销量、评论数、营业时间")
                .setRiskLevel("low")
                .setExamples(Collections.singletonList("分析某个店铺经营情况前，先读取店铺画像"));
    }

    /**
     * 查询店铺实体。
     */
    public Shop getShop(Long shopId) {
        return shopService.getById(shopId);
    }

    /**
     * 构建面向 Agent 的店铺画像。
     */
    public ShopProfileDTO buildShopProfile(Shop shop) {
        ShopProfileDTO profile = new ShopProfileDTO();
        profile.setName(shop.getName());
        profile.setTypeId(shop.getTypeId());
        profile.setArea(shop.getArea());
        profile.setAddress(shop.getAddress());
        profile.setAvgPrice(shop.getAvgPrice());
        profile.setScore(shop.getScore());
        profile.setSold(shop.getSold());
        profile.setComments(shop.getComments());
        profile.setOpenHours(shop.getOpenHours());
        return profile;
    }
}
