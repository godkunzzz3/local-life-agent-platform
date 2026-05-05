package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Agent 模型响应。
 *
 * <p>无论底层是规则实现、LangChain4j，还是直接 HTTP 调大模型，都统一返回这个结构。
 * Facade 层只关心标准字段，不关心具体模型厂商。</p>
 */
@Data
@Accessors(chain = true)
public class AgentModelResponseDTO {

    /**
     * 模型或规则生成的最终回复。
     */
    private String reply;

    /**
     * 模型供应方：rule / langchain4j / openai / qwen / deepseek。
     */
    private String provider;

    /**
     * 模型名称或规则引擎名称。
     */
    private String modelName;

    /**
     * Prompt 模板版本，例如 merchant-agent-v1。
     *
     * <p>同一个模型在不同 Prompt 下表现会不一样，记录版本后才能复盘
     * “这次回答为什么这样生成”。</p>
     */
    private String promptVersion;

    /**
     * 模型调用耗时，单位毫秒。
     */
    private Long costMillis;

    /**
     * 是否发生降级。true 表示没有走真实模型，而是使用规则兜底。
     */
    private Boolean fallback;

    /**
     * 简短推理说明，用于开发调试和商家端解释。
     */
    private String reasoning;

    /**
     * 推荐动作摘要。
     */
    private String recommendedAction;

    /**
     * 置信度，0-100。
     */
    private Integer confidence;
}
