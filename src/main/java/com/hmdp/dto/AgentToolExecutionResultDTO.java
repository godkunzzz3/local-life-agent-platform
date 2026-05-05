package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Agent 工具执行结果。
 *
 * <p>工具定义负责说明“工具是什么”，执行结果负责记录“这次调用发生了什么”。
 * 后续接入大模型后，工具调用结果会作为模型的上下文输入，也会作为审计日志的来源。</p>
 */
@Data
@Accessors(chain = true)
public class AgentToolExecutionResultDTO {

    /**
     * 本次调用的工具名称。
     */
    private String toolName;

    /**
     * 工具调用是否成功。
     */
    private Boolean success;

    /**
     * 调用失败时的错误信息。
     */
    private String errorMsg;

    /**
     * 工具入参，统一保存成 JSON 字符串，方便落库和排查问题。
     */
    private String toolArgs;

    /**
     * 工具执行后的业务结果。
     */
    private Object data;

    /**
     * 工具执行耗时，后续可以用于观察 Agent 工具性能。
     */
    private Long costMillis;
}
