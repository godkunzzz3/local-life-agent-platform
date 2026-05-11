package com.hmdp.dto;

import lombok.Data;

/**
 * RAG 知识召回调试请求。
 *
 * <p>这个 DTO 不用于正式 Agent 对话，只用于学习和排查：
 * 输入一句商家问题，查看后端会召回哪些知识、相似度多少、是否走了向量检索。</p>
 */
@Data
public class AgentKnowledgeRetrieveRequest {

    /**
     * 商家问题，例如“帮我设计一张周末秒杀券”。
     */
    private String message;

    /**
     * 业务意图，可选。
     *
     * <p>常见值：voucher_plan / order_analysis / review_analysis / operation_chat。
     * 为空时按 operation_chat 处理。</p>
     */
    private String intent;

    /**
     * 召回条数。为空默认 3，最大 8。
     */
    private Integer limit;
}
