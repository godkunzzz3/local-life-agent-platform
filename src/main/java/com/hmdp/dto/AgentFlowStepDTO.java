package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Agent 单步流程记录。
 *
 * <p>用于记录一次 Agent 对话中发生了哪些步骤：理解问题、选择工具、执行工具、
 * 生成建议、生成回复。它不是业务表实体，主要用于接口返回和后续审计排查。</p>
 */
@Data
@Accessors(chain = true)
public class AgentFlowStepDTO {

    /**
     * 步骤编码，例如 understand_intent / select_tool / execute_tool。
     */
    private String stepCode;

    /**
     * 步骤名称。
     */
    private String stepName;

    /**
     * 步骤状态：success / skipped / failed。
     */
    private String status;

    /**
     * 步骤说明。
     */
    private String detail;

    /**
     * 当前步骤关联的工具名，没有则为空。
     */
    private String toolName;

    /**
     * 当前步骤耗时，毫秒。
     */
    private Long costMillis;
}
