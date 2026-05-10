package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * Agent Prompt 上下文。
 *
 * <p>当前项目还没有真正接入大模型，所以这里先把 Prompt 需要的核心上下文结构化。
 * 后续接入 Spring AI / LangChain4j 时，可以直接把这个 DTO 转成系统提示词、用户消息和工具约束。</p>
 */
@Data
@Accessors(chain = true)
public class AgentPromptContextDTO {

    /**
     * Agent 运行场景，例如 merchant_operation_chat。
     */
    private String scene;

    /**
     * 系统提示词，定义 Agent 的角色、边界和行为规则。
     */
    private String systemPrompt;

    /**
     * 商家原始输入。
     */
    private String userMessage;

    /**
     * 规则或模型识别出的意图。
     */
    private String intent;

    /**
     * 意图中文名，便于前端展示和调试。
     */
    private String intentName;

    /**
     * 本轮选择的工具名称。
     */
    private String selectedToolName;

    /**
     * 统计时间范围。
     */
    private String dateRange;

    /**
     * 店铺ID。
     */
    private Long shopId;

    /**
     * 店铺名称。
     */
    private String shopName;

    /**
     * Prompt 约束，例如不能直接创建真实券、必须说明数据口径。
     */
    private List<String> constraints;

    /**
     * 期望输出格式，方便后续要求大模型稳定返回结构化内容。
     */
    private String outputFormat;

    /**
     * RAG 检索召回的运营知识。
     *
     * <p>这里保存的是已经从知识库检索出来的少量相关规则或案例。模型回复时必须优先基于
     * 这些知识和工具数据，不允许凭空编造平台规则。</p>
     */
    private List<Map<String, Object>> ragKnowledge;

    /**
     * RAG 检索方式。当前第一版是 mysql_keyword，后续可以升级为 vector_search。
     */
    private String ragRetrievalMode;
}
