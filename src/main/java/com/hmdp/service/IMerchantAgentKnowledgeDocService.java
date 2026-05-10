package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;

/**
 * 商家运营 Agent 知识库文档服务。
 *
 * <p>第一版只做 MySQL 文档管理和关键词检索。等这条链路跑通后，再把启用状态的文档
 * 切片、向量化，并接入真正的 RAG 检索。</p>
 */
public interface IMerchantAgentKnowledgeDocService extends IService<AgentKnowledgeDoc> {

    /**
     * 新增知识文档。
     */
    Result createKnowledgeDoc(AgentKnowledgeDocRequest request);

    /**
     * 修改知识文档。
     */
    Result updateKnowledgeDoc(Long docId, AgentKnowledgeDocRequest request);

    /**
     * 停用知识文档。
     */
    Result disableKnowledgeDoc(Long docId);

    /**
     * 查询知识文档列表。
     */
    Result queryKnowledgeDocs(String category, String keyword, Integer status);

    /**
     * RAG 第一版检索入口：用分类 + 关键词召回知识文档。
     */
    Result searchKnowledgeDocs(String category, String keyword, Integer limit);
}
