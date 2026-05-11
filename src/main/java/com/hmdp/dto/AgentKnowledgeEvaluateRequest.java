package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * RAG 召回评测请求。
 *
 * <p>评测接口用于批量验证“问题 -> 召回知识”的稳定性。它不调用大模型，
 * 只检查 TopK 召回结果是否命中预期分类，帮助我们发现 RAG 路由或向量召回偏差。</p>
 */
@Data
public class AgentKnowledgeEvaluateRequest {

    /**
     * 自定义评测用例。为空时使用后端内置的默认用例。
     */
    private List<CaseItem> cases;

    /**
     * 每个问题召回多少条知识。为空默认 3，最大 8。
     */
    private Integer limit;

    @Data
    public static class CaseItem {

        /**
         * 测试问题。
         */
        private String message;

        /**
         * 业务意图：voucher_plan / order_analysis / review_analysis / operation_chat。
         */
        private String intent;

        /**
         * 期望命中的知识分类。只要 TopK 中命中任意一个期望分类，就算通过。
         */
        private List<String> expectedCategories;
    }
}
