package com.hmdp.dto;

import com.hmdp.entity.AgentCampaignDraft;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Agent 模型请求。
 *
 * <p>Facade 层已经完成权限校验、意图识别和工具调用后，再把这些结构化上下文交给模型层。
 * 这样模型层只负责“基于上下文生成回复”，不直接查询数据库，也不直接写业务表。</p>
 */
@Data
@Accessors(chain = true)
public class AgentModelRequestDTO {

    /**
     * Prompt 上下文：角色、用户问题、意图、选中工具和输出约束。
     */
    private AgentPromptContextDTO promptContext;

    /**
     * 工具执行结果：模型生成回复时必须基于这里的数据。
     */
    private AgentToolExecutionResultDTO toolExecution;

    /**
     * 当前规则或模型选中的运营建议。
     */
    private AgentRecommendationDTO recommendation;

    /**
     * 可选活动草稿。只有活动类问题且触发自动草稿时才会存在。
     */
    private AgentCampaignDraft draft;
}
