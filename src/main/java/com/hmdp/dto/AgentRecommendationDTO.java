package com.hmdp.dto;

import lombok.Data;

/**
 * Agent 运营建议 DTO。
 *
 * <p>用于承载 Agent 给商家的单条可执行建议。相比普通字符串，
 * 结构化建议可以被前端展示、排序、筛选，也可以作为后续生成活动草稿的输入。</p>
 */
@Data
public class AgentRecommendationDTO {

    /**
     * 建议类型，例如 voucher、seckill、content、review、shop。
     */
    private String type;

    /**
     * 建议标题，用于前端卡片展示。
     */
    private String title;

    /**
     * 建议原因，说明 Agent 为什么给出这条建议。
     */
    private String reason;

    /**
     * 推荐动作，说明商家下一步可以做什么。
     */
    private String action;

    /**
     * 优先级，数值越小优先级越高，例如 1 表示最高优先级。
     */
    private Integer priority;

    /**
     * 风险等级：1 低风险，2 中风险，3 高风险。
     */
    private Integer riskLevel;

    /**
     * 预期效果，用于告诉商家这条动作可能带来的收益。
     */
    private String expectedEffect;
}
