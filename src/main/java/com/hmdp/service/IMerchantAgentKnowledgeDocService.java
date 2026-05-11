package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
     * Agent 内部 RAG 检索入口。
     *
     * <p>返回 Map 是为了方便直接放进 PromptContext 和前端调试面板。后续接向量库时，
     * 这个方法可以改成融合关键词、向量相似度和重排序分数。</p>
     */
    List<Map<String, Object>> retrieveForAgent(String intent, String userMessage, Integer limit);
}
