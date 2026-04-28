package com.hmdp.service;

import com.hmdp.dto.Result;

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
}
