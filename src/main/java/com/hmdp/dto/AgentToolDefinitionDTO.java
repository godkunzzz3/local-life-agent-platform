package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Agent 工具定义。
 *
 * <p>这类 DTO 不承载具体业务数据，而是描述工具“能做什么、怎么调用、风险多高”。
 * 后续接入大模型时，可以把这些定义拼进 Prompt 或转换成 Function Calling 的工具 Schema。</p>
 */
@Data
@Accessors(chain = true)
public class AgentToolDefinitionDTO {

    /**
     * 工具唯一名称，后续用于大模型选择工具或审计工具调用。
     */
    private String name;

    /**
     * 给商家或开发者看的工具名称。
     */
    private String displayName;

    /**
     * 工具职责说明。
     */
    private String description;

    /**
     * 工具分类，例如 shop/order/voucher/review。
     */
    private String category;

    /**
     * 工具类型：readonly 只读分析、draft 生成草稿、execute 执行真实业务动作。
     */
    private String toolType;

    /**
     * 访问级别：read 表示只读分析，write 表示可能写入业务数据。
     */
    private String accessLevel;

    /**
     * 是否必须经过商家确认。
     */
    private Boolean requireMerchantConfirm;

    /**
     * 是否会写入数据库。
     */
    private Boolean writeDatabase;

    /**
     * 是否允许暴露给模型直接选择。
     *
     * <p>只读工具通常可以暴露；写工具和高风险执行工具不应该直接暴露给模型。</p>
     */
    private Boolean modelCallable;

    /**
     * 执行策略：direct 直接执行、draft_only 只生成草稿、human_confirm 必须人工确认后执行。
     */
    private String executionPolicy;

    /**
     * 人工确认说明，用于前端和面试讲解当前工具为什么不能直接执行。
     */
    private String confirmReason;

    /**
     * 输入参数说明，当前先用文本描述，后续可以替换成 JSON Schema。
     */
    private String inputSchema;

    /**
     * 输出结果说明，当前先用文本描述，后续可以替换成 JSON Schema。
     */
    private String outputSchema;

    /**
     * 风险等级：low/medium/high。
     */
    private String riskLevel;

    /**
     * 典型调用场景示例。
     */
    private List<String> examples;
}
