package com.hmdp.agent;

import com.hmdp.tool.AgentToolDescriptor;
import com.hmdp.tool.AgentToolRegistry;
import com.hmdp.tool.OperationDiagnosisToolDescriptor;
import com.hmdp.tool.OrderAgentTool;
import com.hmdp.tool.ReviewAgentTool;
import com.hmdp.tool.ShopAgentTool;
import com.hmdp.tool.VoucherAgentTool;
import com.hmdp.tool.VoucherAnalysisToolDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantAgentRulePolicyServiceTest {

    private MerchantAgentRulePolicyService rulePolicyService;

    @BeforeEach
    void setUp() {
        List<AgentToolDescriptor> descriptors = Arrays.asList(
                new ShopAgentTool(),
                new OrderAgentTool(),
                new ReviewAgentTool(),
                new VoucherAnalysisToolDescriptor(),
                new OperationDiagnosisToolDescriptor(),
                new VoucherAgentTool()
        );
        rulePolicyService = new MerchantAgentRulePolicyService(new AgentToolRegistry(descriptors));
    }

    @Test
    void shouldResolveOrderAnalysisIntent() {
        String intent = rulePolicyService.resolveIntent("帮我分析最近7天订单情况");

        assertEquals("order_analysis", intent);
        assertEquals("order_analysis_tool", rulePolicyService.resolveToolName(intent));
    }

    @Test
    void shouldResolveReviewAnalysisIntent() {
        String intent = rulePolicyService.resolveIntent("最近用户主要吐槽什么");

        assertEquals("review_analysis", intent);
        assertEquals("review_content_tool", rulePolicyService.resolveToolName(intent));
    }

    @Test
    void shouldResolveVoucherCampaignIntent() {
        String intent = rulePolicyService.resolveIntent("帮我设计一个周末秒杀活动");

        assertEquals("voucher_plan", intent);
        assertEquals("voucher_campaign_tool", rulePolicyService.resolveToolName(intent));
    }

    @Test
    void shouldResolveOperationDiagnosisIntent() {
        String intent = rulePolicyService.resolveIntent("帮我分析一下最近经营情况");

        assertEquals("operation_chat", intent);
        assertEquals("operation_diagnosis_tool", rulePolicyService.resolveToolName(intent));
    }

    @Test
    void shouldDetectProhibitedOperation() {
        assertTrue(rulePolicyService.isProhibitedOperation("帮我删除所有活动"));
        assertTrue(rulePolicyService.isProhibitedOperation("直接退款"));
        assertTrue(rulePolicyService.isProhibitedOperation("修改库存"));
    }

    @Test
    void shouldDetectExpandedProhibitedOperations() {
        assertTrue(rulePolicyService.isProhibitedOperation("帮我取消用户订单"));
        assertTrue(rulePolicyService.isProhibitedOperation("帮我修改核销状态"));
        assertTrue(rulePolicyService.isProhibitedOperation("帮我群发优惠券给所有用户"));
        assertTrue(rulePolicyService.isProhibitedOperation("帮我直接创建 10000 张 1 元秒杀券"));
        assertTrue(rulePolicyService.isProhibitedOperation("帮我把支付状态改成已支付"));
        assertTrue(rulePolicyService.isProhibitedOperation("帮我删除用户差评"));
        assertTrue(rulePolicyService.isProhibitedOperation("帮我查看用户手机号"));
    }

    @Test
    void shouldResolveNeedConfirmByToolMetadata() {
        assertTrue(rulePolicyService.resolveNeedConfirm("voucher_campaign_tool"));
        assertFalse(rulePolicyService.resolveNeedConfirm("order_analysis_tool"));
    }

    @Test
    void shouldResolveRiskLevel() {
        assertEquals("high", rulePolicyService.resolveRiskLevel("帮我直接退款", "order_analysis_tool"));
        assertEquals("medium", rulePolicyService.resolveRiskLevel("帮我设计秒杀活动", "voucher_campaign_tool"));
        assertEquals("low", rulePolicyService.resolveRiskLevel("帮我分析订单", "order_analysis_tool"));
    }
}
