package com.hmdp.agent;

import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.tool.AgentToolRegistry;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 商家 Agent 规则策略组件。
 *
 * <p>这里集中承载轻量规则判断，供线上 Agent 和后续 Agent Eval 共用。</p>
 */
@Component
public class MerchantAgentRulePolicyService {

    private static final Set<String> CONFIRM_REQUIRED_POLICIES = new HashSet<>(
            Arrays.asList("draft_only", "human_confirm", "confirm_required")
    );

    private final AgentToolRegistry agentToolRegistry;

    public MerchantAgentRulePolicyService(AgentToolRegistry agentToolRegistry) {
        this.agentToolRegistry = agentToolRegistry;
    }

    public String resolveIntent(String userInput) {
        String text = userInput == null ? "" : userInput;
        if (containsAny(text, "你是什么", "什么模型", "哪个模型", "你是谁", "能做什么", "介绍一下", "你的能力", "怎么用")) {
            return "identity";
        }
        if (containsAny(text, "秒杀", "优惠券", "代金券", "活动", "拉新", "复购", "草稿")) {
            return "voucher_plan";
        }
        if (containsAny(text, "评价", "评论", "探店", "内容", "笔记", "口碑", "吐槽")) {
            return "review_analysis";
        }
        if (containsAny(text, "订单", "销售", "收入", "营收", "成交", "转化", "支付")) {
            return "order_analysis";
        }
        return "operation_chat";
    }

    public String resolveToolName(String intent) {
        if ("identity".equals(intent)) {
            return "agent_profile_tool";
        }
        if ("order_analysis".equals(intent)) {
            return "order_analysis_tool";
        }
        if ("voucher_plan".equals(intent)) {
            return "voucher_campaign_tool";
        }
        if ("review_analysis".equals(intent)) {
            return "review_content_tool";
        }
        return "operation_diagnosis_tool";
    }

    public boolean isProhibitedOperation(String userInput) {
        return containsAny(userInput,
                "退款", "退钱", "取消订单", "取消用户订单", "删除", "删掉", "下架全部", "群发", "批量推送",
                "修改支付", "改支付", "支付状态", "核销状态", "改核销", "直接核销", "自动核销",
                "修改库存", "改库存", "清空库存", "修改价格", "改价格",
                "直接创建", "超大规模", "大规模秒杀", "用户手机号", "手机号", "隐私信息", "用户隐私");
    }

    public boolean resolveNeedConfirm(String toolName) {
        AgentToolDefinitionDTO definition = findToolDefinition(toolName);
        if (definition == null) {
            return false;
        }
        return Boolean.TRUE.equals(definition.getRequireMerchantConfirm())
                || Boolean.TRUE.equals(definition.getWriteDatabase())
                || CONFIRM_REQUIRED_POLICIES.contains(normalize(definition.getExecutionPolicy()));
    }

    public String resolveRiskLevel(String userInput, String toolName) {
        if (isProhibitedOperation(userInput)) {
            return "high";
        }
        if (resolveNeedConfirm(toolName)) {
            return "medium";
        }
        AgentToolDefinitionDTO definition = findToolDefinition(toolName);
        if (definition != null && !isBlank(definition.getRiskLevel())) {
            return definition.getRiskLevel();
        }
        return "low";
    }

    private AgentToolDefinitionDTO findToolDefinition(String toolName) {
        if (isBlank(toolName)) {
            return null;
        }
        for (AgentToolDefinitionDTO definition : agentToolRegistry.listDefinitions()) {
            if (toolName.equals(definition.getName()) || toolName.equals(definition.getModelToolName())) {
                return definition;
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }
}
