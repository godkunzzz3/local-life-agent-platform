package com.hmdp.tool;

import com.hmdp.dto.AgentToolDefinitionDTO;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 优惠券结构只读工具定义。
 *
 * <p>学习重点：这里把“查询优惠券结构”和“生成优惠券草稿”拆成两个 Tool。
 * 查询工具可以暴露给模型直接调用；草稿工具会写入数据库，必须继续走人工确认流程。</p>
 */
@Component
public class VoucherAnalysisToolDescriptor implements AgentToolDescriptor {

    @Override
    public AgentToolDefinitionDTO definition() {
        return new AgentToolDefinitionDTO()
                .setName("voucher_analysis_tool")
                .setModelToolName("getShopVouchers")
                .setDisplayName("优惠券结构分析工具")
                .setDescription("查询当前店铺优惠券和秒杀券结构，适合判断券力度、库存、是否需要新增活动。")
                .setCategory("voucher")
                .setToolType("readonly")
                .setAccessLevel("read")
                .setRequireMerchantConfirm(false)
                .setWriteDatabase(false)
                .setModelCallable(true)
                .setExecutionPolicy("direct")
                .setConfirmReason("只读取店铺优惠券结构，不创建或修改真实活动。")
                .setInputSchema("{\"shopId\":\"店铺ID\"}")
                .setOutputSchema("VoucherStatsDTO：优惠券总数、在线券数量、秒杀券数量、秒杀库存等")
                .setRiskLevel("low")
                .setExamples(Collections.singletonList("商家询问优惠券力度是否合适时，先查询当前券结构"));
    }
}
