package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.MerchantCampaignDraftRequest;

/**
 * 商家运营 Agent 门面服务。
 *
 * <p>Facade 不直接对应单张表，主要负责串联“会话、消息、工具调用、建议、草稿、审计”
 * 这些跨表流程。当前阶段先保留接口，下一步会在这里实现运营报告和 Agent 对话编排。</p>
 */
public interface IMerchantAgentFacadeService {

    /**
     * 生成店铺运营报告。
     *
     * @param shopId 店铺ID
     * @param dateRange 统计时间范围：TODAY / LAST_7_DAYS / LAST_30_DAYS
     * @return 结构化运营报告
     */
    Result generateOperationReport(Long shopId, String dateRange);

    /**
     * 查询某个店铺的 Agent 会话列表。
     *
     * @param shopId 店铺ID
     * @return 会话摘要列表
     */
    Result queryShopSessions(Long shopId);

    /**
     * 查询某个 Agent 会话下的消息记录。
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    Result querySessionMessages(Long sessionId);

    /**
     * 查询某个店铺的 Agent 建议列表。
     *
     * @param shopId 店铺ID
     * @return 建议卡片列表
     */
    Result queryShopSuggestions(Long shopId);

    /**
     * 根据 Agent 建议生成活动草稿。
     *
     * @param suggestionId 建议ID
     * @param request 草稿覆盖参数
     * @return 草稿详情
     */
    Result createCampaignDraft(Long suggestionId, MerchantCampaignDraftRequest request);

    /**
     * 商家确认活动草稿后创建真实优惠券。
     *
     * @param draftId 草稿ID
     * @return 创建结果
     */
    Result confirmCampaignDraft(Long draftId);
}
