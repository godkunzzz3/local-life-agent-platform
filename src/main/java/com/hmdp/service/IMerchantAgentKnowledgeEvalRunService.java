package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeEvalRun;

import java.util.Map;

/**
 * RAG 评测运行记录服务。
 *
 * <p>它属于 Agent 的可观测性模块：不影响召回算法，只负责保存和查询每次评测的质量指标。</p>
 */
public interface IMerchantAgentKnowledgeEvalRunService extends IService<AgentKnowledgeEvalRun> {

    /**
     * 记录一次 RAG 批量评测结果。
     *
     * @param evalResult evaluateRetrieval 生成的完整结果 Map
     * @return 本次评测运行 ID；保存失败时返回 null，由主流程降级忽略
     */
    Long recordRun(Map<String, Object> evalResult);

    /**
     * 查询最近的评测运行记录。
     */
    Result queryRecentRuns(Integer limit);
}
