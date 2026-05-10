package com.hmdp.agent;

import com.hmdp.dto.AgentModelRequestDTO;
import com.hmdp.dto.AgentModelResponseDTO;
import com.hmdp.dto.AgentRecommendationDTO;
import com.hmdp.dto.OrderStatsDTO;
import com.hmdp.dto.ReviewStatsDTO;
import com.hmdp.dto.VoucherStatsDTO;
import com.hmdp.entity.AgentCampaignDraft;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 规则版商家运营模型客户端。
 *
 * <p>当前还没有接 LangChain4j，所以先用规则版实现模型接口。
 * 它模拟“模型生成回复”的职责：读取工具结果和建议，生成面向商家的自然语言回复。
 * 后续接入真实大模型时，只需要新增另一个 MerchantAgentModelClient 实现。</p>
 */
@Component
@ConditionalOnProperty(prefix = "merchant-agent.model", name = "provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedMerchantAgentModelClient implements MerchantAgentModelClient {

    @Override
    public AgentModelResponseDTO generateReply(AgentModelRequestDTO request) {
        long start = System.currentTimeMillis();
        String intent = request.getPromptContext().getIntent();
        Map<String, Object> toolData = getToolData(request);
        AgentRecommendationDTO recommendation = request.getRecommendation();
        AgentCampaignDraft draft = request.getDraft();

        String reply = buildReply(intent, request, toolData, recommendation, draft);
        return new AgentModelResponseDTO()
                .setReply(reply)
                .setProvider("rule")
                .setModelName("rule-based-merchant-agent-v1")
                .setPromptVersion("merchant-agent-v1")
                .setCostMillis(System.currentTimeMillis() - start)
                .setFallback(true)
                .setReasoning(buildReasoning(intent, request))
                .setRecommendedAction(recommendation == null ? null : recommendation.getAction())
                .setConfidence(78);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getToolData(AgentModelRequestDTO request) {
        Object data = request.getToolExecution() == null ? null : request.getToolExecution().getData();
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return java.util.Collections.emptyMap();
    }

    private String buildReply(String intent, AgentModelRequestDTO request, Map<String, Object> toolData,
                              AgentRecommendationDTO recommendation, AgentCampaignDraft draft) {
        String shopName = request.getPromptContext().getShopName();
        OrderStatsDTO order = (OrderStatsDTO) toolData.get("orderAnalysis");
        VoucherStatsDTO voucher = (VoucherStatsDTO) toolData.get("voucherAnalysis");
        ReviewStatsDTO review = (ReviewStatsDTO) toolData.get("reviewAnalysis");

        if ("identity".equals(intent)) {
            return "我是黑马点评商家运营 Agent，当前可以通过 LangChain4j 接入 Qwen 模型，模型不可用时会自动降级为规则版回复。"
                    + "我能读取当前商家的订单、优惠券、评价和店铺基础数据，帮助你做运营分析、活动建议和待确认草稿。"
                    + "我不会直接创建真实优惠券或秒杀券，所有活动都需要先生成草稿，再由商家确认执行。";
        }
        if ("order_analysis".equals(intent) && order != null) {
            return shopName + "当前周期共有券订单" + order.getTotalOrders()
                    + "笔，已支付" + order.getPaidOrders()
                    + "笔，预计收入" + formatFen(order.getEstimatedRevenue())
                    + "，支付转化率" + order.getConversionRate()
                    + "。热门券是：" + order.getTopVoucher() + "。";
        }
        if ("voucher_plan".equals(intent) && voucher != null && recommendation != null) {
            String reply = "我建议采用【" + recommendation.getTitle() + "】。原因是："
                    + recommendation.getReason() + " " + recommendation.getAction()
                    + " 当前店铺在线券" + voucher.getOnlineVouchers()
                    + "张，秒杀库存" + voucher.getSeckillStock() + "。";
            if (draft != null) {
                reply += " 我已经生成待确认活动草稿，商家可以继续编辑后确认创建。";
            }
            return reply;
        }
        if ("review_analysis".equals(intent) && review != null && recommendation != null) {
            return "内容侧当前有探店笔记" + review.getBlogCount()
                    + "篇，评论互动" + review.getCommentCount()
                    + "次，互动等级为" + review.getEngagementLevel()
                    + "。建议：" + recommendation.getAction();
        }
        if (order != null && voucher != null && review != null && recommendation != null) {
            return shopName + "当前周期订单" + order.getTotalOrders()
                    + "笔，在线优惠券" + voucher.getOnlineVouchers()
                    + "张，内容互动等级" + review.getEngagementLevel()
                    + "。优先建议：" + recommendation.getTitle() + "，" + recommendation.getAction();
        }
        return shopName + "当前数据不足，我建议先生成完整运营报告，补齐订单、优惠券和评价数据后再制定动作。";
    }

    private String buildReasoning(String intent, AgentModelRequestDTO request) {
        int ragCount = request.getPromptContext().getRagKnowledge() == null
                ? 0
                : request.getPromptContext().getRagKnowledge().size();
        return "规则版模型根据意图 " + intent
                + "，读取工具 " + request.getPromptContext().getSelectedToolName()
                + " 的结构化结果，并参考 " + ragCount + " 条运营知识生成回复。";
    }

    private String formatFen(Long value) {
        long fen = value == null ? 0L : value;
        return "¥" + (fen / 100) + "." + String.format("%02d", Math.abs(fen % 100));
    }
}
