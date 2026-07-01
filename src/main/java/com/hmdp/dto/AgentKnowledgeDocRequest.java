package com.hmdp.dto;

import lombok.Data;

/**
 * Agent 知识库文档请求参数。
 *
 * <p>这个 DTO 同时用于新增和修改。修改时字段可以只传需要覆盖的部分；
 * 新增时 title、category、content 必填。</p>
 */
@Data
public class AgentKnowledgeDocRequest {

    /**
     * 店铺ID。为空表示公共知识；非空表示店铺私有知识。
     */
    private Long shopId;

    /**
     * 知识标题，例如“周末秒杀券设计规则”。
     */
    private String title;

    /**
     * 知识分类：voucher_rule / seckill_rule / industry_case / risk_rule / cost_rule。
     */
    private String category;

    /**
     * 知识正文。
     */
    private String content;

    /**
     * 向量库文档 ID。第一版先不用，预留给后续 RAG 向量化。
     */
    private String vectorId;

    /**
     * 状态：1启用，0停用。
     */
    private Integer status;
}
