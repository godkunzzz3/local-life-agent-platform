package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentKnowledgeEvaluateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeEvalCase;

import java.util.List;

/**
 * RAG 召回评测用例服务。
 *
 * <p>它负责保存和读取评测集。真正的召回评测仍然放在
 * {@link IMerchantAgentKnowledgeDocService}，这样“测试集管理”和“召回算法”职责分开。</p>
 */
public interface IMerchantAgentKnowledgeEvalCaseService extends IService<AgentKnowledgeEvalCase> {

    /**
     * 查询启用中的评测用例。
     */
    Result queryEnabledCases();

    /**
     * 批量替换启用中的评测用例。
     */
    Result replaceEnabledCases(AgentKnowledgeEvaluateRequest request);

    /**
     * 返回启用用例的 DTO 形式，供评测接口复用。
     */
    List<AgentKnowledgeEvaluateRequest.CaseItem> listEnabledCaseItems();
}
