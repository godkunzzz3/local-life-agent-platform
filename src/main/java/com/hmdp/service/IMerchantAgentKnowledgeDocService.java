package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.AgentKnowledgeEvaluateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 商家运营 Agent 知识库文档服务。
 *
 * <p>负责知识文档的维护、文件导入、向量化和 Agent 内部召回。
 * 当前实现会优先走语义向量检索，异常时降级为 MySQL 关键词检索。</p>
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
     * 知识库查询入口：用于管理端按分类和关键词查看文档。
     */
    Result searchKnowledgeDocs(String category, String keyword, Integer limit);

    /**
     * 从 txt/md 文件导入知识文档。
     *
     * <p>第一版只接收纯文本文件，避免 PDF/DOCX 解析复杂度干扰 RAG 主线。
     * 后续接文档解析和切片时，可以继续扩展这个入口。</p>
     */
    Result uploadKnowledgeDoc(String category, String title, MultipartFile file);

    /**
     * 向量化单条知识 chunk。
     */
    Result vectorizeKnowledgeDoc(Long docId);

    /**
     * 批量向量化启用状态的知识 chunk。
     */
    Result vectorizeKnowledgeDocs(String category, Integer limit);

    /**
     * RAG 召回调试接口。
     *
     * <p>用于学习和排查：直接输入商家问题，返回召回知识、检索模式和相似度分数。
     * 它不调用大模型，也不生成最终回复。</p>
     */
    Result debugRetrieveForAgent(String intent, String userMessage, Integer limit);

    /**
     * RAG 召回批量评测。
     *
     * <p>用于验证一批测试问题的 TopK 召回是否命中预期分类，帮助持续改进知识库和召回策略。</p>
     */
    Result evaluateRetrieval(AgentKnowledgeEvaluateRequest request);

    /**
     * Agent 内部 RAG 检索入口。
     *
     * <p>返回 Map 是为了方便直接放进 PromptContext 和前端调试面板。后续接向量库时，
     * 这个方法已经融合了向量相似度和关键词兜底，后续可以继续扩展重排序。</p>
     */
    List<Map<String, Object>> retrieveForAgent(String intent, String userMessage, Integer limit);

    /**
     * Agent 内部商家隔离 RAG 检索入口。
     *
     * <p>只允许召回当前店铺私有知识和公共知识，禁止召回其他店铺私有知识。</p>
     */
    List<Map<String, Object>> retrieveForAgentForShop(Long shopId, String intent, String userMessage, Integer limit);
}
