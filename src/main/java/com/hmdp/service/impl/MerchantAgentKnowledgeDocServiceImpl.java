package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.agent.MerchantAgentEmbeddingService;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import com.hmdp.mapper.AgentKnowledgeDocMapper;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * 商家运营 Agent 知识库文档服务实现。
 *
 * <p>当前是 RAG 的学习版实现：文档元数据保存在 MySQL，向量保存在 Redis。
 * Agent 检索时优先使用向量相似度召回，失败时降级为标题/正文关键词匹配。</p>
 */
@Service
public class MerchantAgentKnowledgeDocServiceImpl
        extends ServiceImpl<AgentKnowledgeDocMapper, AgentKnowledgeDoc>
        implements IMerchantAgentKnowledgeDocService {

    private static final long MAX_UPLOAD_SIZE = 256 * 1024;
    private static final int MAX_CHUNK_LENGTH = 500;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private MerchantAgentEmbeddingService embeddingService;

    @Override
    public Result createKnowledgeDoc(AgentKnowledgeDocRequest request) {
        Result validateResult = validateCreateRequest(request);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        AgentKnowledgeDoc doc = new AgentKnowledgeDoc()
                .setId(redisIdWorker.nextId("agent"))
                .setTitle(request.getTitle().trim())
                .setCategory(request.getCategory().trim())
                .setContent(request.getContent().trim())
                .setVectorId(trimToNull(request.getVectorId()))
                .setStatus(request.getStatus() == null ? 1 : request.getStatus());
        save(doc);
        return Result.ok(toDocRow(doc));
    }

    @Override
    public Result updateKnowledgeDoc(Long docId, AgentKnowledgeDocRequest request) {
        if (docId == null) {
            return Result.fail("知识文档id不能为空");
        }
        if (request == null) {
            return Result.fail("知识文档修改内容不能为空");
        }
        AgentKnowledgeDoc oldDoc = getById(docId);
        if (oldDoc == null) {
            return Result.fail("知识文档不存在");
        }

        AgentKnowledgeDoc updateDoc = new AgentKnowledgeDoc().setId(docId);
        if (!isBlank(request.getTitle())) {
            updateDoc.setTitle(request.getTitle().trim());
        }
        if (!isBlank(request.getCategory())) {
            updateDoc.setCategory(request.getCategory().trim());
        }
        if (!isBlank(request.getContent())) {
            updateDoc.setContent(request.getContent().trim());
        }
        if (request.getVectorId() != null) {
            updateDoc.setVectorId(trimToNull(request.getVectorId()));
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                return Result.fail("知识文档状态只能是启用或停用");
            }
            updateDoc.setStatus(request.getStatus());
        }
        updateById(updateDoc);
        return Result.ok(toDocRow(getById(docId)));
    }

    @Override
    public Result disableKnowledgeDoc(Long docId) {
        if (docId == null) {
            return Result.fail("知识文档id不能为空");
        }
        AgentKnowledgeDoc doc = getById(docId);
        if (doc == null) {
            return Result.fail("知识文档不存在");
        }
        updateById(new AgentKnowledgeDoc().setId(docId).setStatus(0));
        doc.setStatus(0);
        return Result.ok(toDocRow(doc));
    }

    @Override
    public Result queryKnowledgeDocs(String category, String keyword, Integer status) {
        List<AgentKnowledgeDoc> docs = query()
                .eq(!isBlank(category), "category", category)
                .eq(status != null, "status", status)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("create_time")
                .last("LIMIT 100")
                .list();
        return Result.ok(toDocRows(docs));
    }

    @Override
    public Result searchKnowledgeDocs(String category, String keyword, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 5 : Math.min(limit, 20);
        List<AgentKnowledgeDoc> docs = query()
                .eq("status", 1)
                .eq(!isBlank(category), "category", category)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("update_time")
                .last("LIMIT " + safeLimit)
                .list();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retrievalMode", "mysql_keyword");
        result.put("category", category);
        result.put("keyword", keyword);
        result.put("limit", safeLimit);
        result.put("documents", toDocRows(docs));
        result.put("message", docs.isEmpty()
                ? "暂未检索到相关运营知识，可以先补充知识文档"
                : "已基于关键词召回运营知识文档");
        return Result.ok(result);
    }

    @Override
    public Result uploadKnowledgeDoc(String category, String title, MultipartFile file) {
        Result validateResult = validateUploadRequest(category, file);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        try {
            String content = new String(StreamUtils.copyToByteArray(file.getInputStream()), StandardCharsets.UTF_8).trim();
            if (isBlank(content)) {
                return Result.fail("上传文件内容不能为空");
            }
            String baseTitle = resolveUploadTitle(title, file.getOriginalFilename());
            List<String> chunks = splitKnowledgeContent(content);
            List<AgentKnowledgeDoc> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                AgentKnowledgeDoc doc = new AgentKnowledgeDoc()
                        .setId(redisIdWorker.nextId("agent"))
                        .setTitle(buildChunkTitle(baseTitle, i + 1, chunks.size()))
                        .setCategory(category.trim())
                        .setContent(chunks.get(i))
                        .setStatus(1);
                docs.add(doc);
            }
            saveBatch(docs);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourceTitle", baseTitle);
            result.put("chunkCount", docs.size());
            result.put("documents", toDocRows(docs));
            result.put("message", "知识文件已切分为 " + docs.size() + " 个片段并导入");
            return Result.ok(result);
        } catch (IOException e) {
            return Result.fail("读取知识文件失败，请确认文件编码为UTF-8");
        }
    }

    @Override
    public Result vectorizeKnowledgeDoc(Long docId) {
        if (docId == null) {
            return Result.fail("知识文档id不能为空");
        }
        AgentKnowledgeDoc doc = getById(docId);
        if (doc == null) {
            return Result.fail("知识文档不存在");
        }
        Result result = embeddingService.embedKnowledgeDoc(doc);
        if (!result.getSuccess()) {
            return result;
        }
        Map<String, Object> vectorInfo = (Map<String, Object>) result.getData();
        String vectorId = String.valueOf(vectorInfo.get("vectorId"));
        updateById(new AgentKnowledgeDoc().setId(docId).setVectorId(vectorId));
        doc.setVectorId(vectorId);
        vectorInfo.put("document", toDocRow(doc));
        return Result.ok(vectorInfo);
    }

    @Override
    public Result vectorizeKnowledgeDocs(String category, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        List<AgentKnowledgeDoc> docs = query()
                .eq("status", 1)
                .eq(!isBlank(category), "category", category)
                .last("LIMIT " + safeLimit)
                .list();

        List<Map<String, Object>> rows = new ArrayList<>();
        int successCount = 0;
        for (AgentKnowledgeDoc doc : docs) {
            Result result = vectorizeKnowledgeDoc(doc.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("docId", String.valueOf(doc.getId()));
            row.put("title", doc.getTitle());
            row.put("success", result.getSuccess());
            row.put("data", result.getData());
            row.put("errorMsg", result.getErrorMsg());
            rows.add(row);
            if (result.getSuccess()) {
                successCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("limit", safeLimit);
        result.put("total", docs.size());
        result.put("successCount", successCount);
        result.put("failedCount", docs.size() - successCount);
        result.put("items", rows);
        return Result.ok(result);
    }

    @Override
    public List<Map<String, Object>> retrieveForAgent(String intent, String userMessage, Integer limit) {
        // Agent 内部默认只取 Top3，避免把太多知识片段塞进 Prompt 导致成本和噪音上升。
        int safeLimit = limit == null || limit <= 0 ? 3 : Math.min(limit, 8);

        // 先根据意图缩小知识分类范围，例如优惠券方案优先查秒杀/优惠券规则。
        // 这样做可以减少候选文档数量，也能降低无关知识被召回的概率。
        String category = resolveCategoryByIntent(intent);

        // 第一优先级：语义向量检索。只要命中，就直接返回 TopK。
        List<Map<String, Object>> vectorHits = retrieveByVector(category, userMessage, safeLimit);
        if (!vectorHits.isEmpty()) {
            return vectorHits;
        }

        // 兜底策略：如果向量检索不可用，就回到关键词检索，保证 Agent 主流程仍然可用。
        return retrieveByKeyword(category, userMessage, intent, safeLimit);
    }

    /**
     * 语义向量召回。
     *
     * <p>业务逻辑：
     * 1. 先把商家问题转换成 query 向量；
     * 2. 再读取同分类知识 chunk 的 doc 向量；
     * 3. 用余弦相似度排序，取 TopK；
     * 4. 把相似度分数返回给前端和 PromptContext，方便观察 RAG 命中依据。</p>
     */
    private List<Map<String, Object>> retrieveByVector(String category, String userMessage, int safeLimit) {
        // 1. 把商家输入的问题向量化，例如“帮我设计周末秒杀券” -> queryVector。
        List<Float> queryVector = embeddingService.embedQuery(userMessage);
        if (queryVector == null || queryVector.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 从 MySQL 读取候选知识文档。这里不取全部，只取启用、已向量化、同分类的近 200 条。
        // 学习项目数据量小，可以直接 Java 内存算；生产大规模场景应交给向量数据库做 ANN 检索。
        List<AgentKnowledgeDoc> candidates = query()
                .eq("status", 1)
                .eq(!isBlank(category), "category", category)
                .isNotNull("vector_id")
                .orderByDesc("update_time")
                .last("LIMIT 200")
                .list();

        List<ScoredKnowledgeDoc> scoredDocs = new ArrayList<>();
        for (AgentKnowledgeDoc doc : candidates) {
            if (isBlank(doc.getVectorId())) {
                continue;
            }

            // 3. 根据 MySQL 里的 vectorId 到 Redis 读取 1024 维文档向量。
            List<Float> docVector = embeddingService.loadVector(doc.getVectorId());

            // 4. 用余弦相似度计算“用户问题”和“知识片段”的语义相关度。
            double score = embeddingService.cosineSimilarity(queryVector, docVector);
            if (score <= 0D) {
                continue;
            }
            scoredDocs.add(new ScoredKnowledgeDoc(doc, score));
        }

        // 5. 按相似度从高到低排序，分数最高的知识片段最先进入 Prompt。
        scoredDocs.sort(Comparator.comparingDouble(ScoredKnowledgeDoc::getScore).reversed());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(safeLimit, scoredDocs.size()); i++) {
            ScoredKnowledgeDoc scoredDoc = scoredDocs.get(i);
            Map<String, Object> row = toDocRow(scoredDoc.getDoc());

            // retrievalMode 和 similarityScore 会返回给前端调试面板，便于观察 RAG 是否真的命中。
            row.put("retrievalMode", "semantic_vector");
            row.put("similarityScore", roundScore(scoredDoc.getScore()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 关键词兜底召回。
     *
     * <p>当 API Key 未配置、Embedding 服务异常、Redis 中还没有向量时，RAG 不能阻断 Agent 主流程。
     * 所以这里保留原来的 MySQL like 检索，保证学习项目在没有向量库时也能运行。</p>
     */
    private List<Map<String, Object>> retrieveByKeyword(String category, String userMessage, String intent, int safeLimit) {
        // 关键词由简单规则提取，例如“秒杀/周末” -> “秒杀”，“评价/口碑” -> “评价”。
        String keyword = resolveKeyword(userMessage, intent);
        List<AgentKnowledgeDoc> docs = query()
                .eq("status", 1)
                .eq(!isBlank(category), "category", category)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("update_time")
                .last("LIMIT " + safeLimit)
                .list();

        if (docs.isEmpty() && !isBlank(category)) {
            // 如果关键词没命中，但当前意图能映射到分类，就退一步取该分类最近更新的知识。
            // 这样至少能给 Agent 一些行业规则背景，而不是完全没有 RAG 内容。
            docs = query()
                    .eq("status", 1)
                    .eq("category", category)
                    .orderByDesc("update_time")
                    .last("LIMIT " + safeLimit)
                    .list();
        }

        List<Map<String, Object>> rows = toDocRows(docs);
        for (Map<String, Object> row : rows) {
            row.put("retrievalMode", "mysql_keyword_fallback");
        }
        return rows;
    }

    private Result validateCreateRequest(AgentKnowledgeDocRequest request) {
        if (request == null) {
            return Result.fail("知识文档内容不能为空");
        }
        if (isBlank(request.getTitle())) {
            return Result.fail("知识标题不能为空");
        }
        if (isBlank(request.getCategory())) {
            return Result.fail("知识分类不能为空");
        }
        if (isBlank(request.getContent())) {
            return Result.fail("知识正文不能为空");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            return Result.fail("知识文档状态只能是启用或停用");
        }
        return Result.ok();
    }

    private Result validateUploadRequest(String category, MultipartFile file) {
        if (isBlank(category)) {
            return Result.fail("知识分类不能为空");
        }
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的知识文件");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            return Result.fail("知识文件不能超过256KB");
        }
        String fileName = file.getOriginalFilename();
        if (isBlank(fileName) || !isAllowedTextFile(fileName)) {
            return Result.fail("仅支持上传 .txt 或 .md 文件");
        }
        return Result.ok();
    }

    private boolean isAllowedTextFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".txt") || lowerName.endsWith(".md");
    }

    private String resolveUploadTitle(String title, String fileName) {
        if (!isBlank(title)) {
            return title.trim();
        }
        if (isBlank(fileName)) {
            return "未命名知识文档";
        }
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return isBlank(baseName) ? "未命名知识文档" : baseName.trim();
    }

    private List<String> splitKnowledgeContent(String content) {
        List<String> chunks = new ArrayList<>();
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (isBlank(text)) {
                continue;
            }
            if (text.length() > MAX_CHUNK_LENGTH) {
                flushChunk(chunks, current);
                splitLongParagraph(chunks, text);
                continue;
            }
            if (current.length() > 0 && current.length() + text.length() + 2 > MAX_CHUNK_LENGTH) {
                flushChunk(chunks, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(text);
        }
        flushChunk(chunks, current);
        return chunks.isEmpty() ? java.util.Collections.singletonList(content.trim()) : chunks;
    }

    private void splitLongParagraph(List<String> chunks, String text) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, text.length());
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(current.toString().trim());
        current.setLength(0);
    }

    private String buildChunkTitle(String baseTitle, int index, int total) {
        if (total <= 1) {
            return baseTitle;
        }
        return baseTitle + " #" + index;
    }

    private List<Map<String, Object>> toDocRows(List<AgentKnowledgeDoc> docs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentKnowledgeDoc doc : docs) {
            rows.add(toDocRow(doc));
        }
        return rows;
    }

    private Map<String, Object> toDocRow(AgentKnowledgeDoc doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId());
        row.put("docId", String.valueOf(doc.getId()));
        row.put("title", doc.getTitle());
        row.put("category", doc.getCategory());
        row.put("categoryName", resolveCategoryName(doc.getCategory()));
        row.put("content", doc.getContent());
        row.put("vectorId", doc.getVectorId());
        row.put("status", doc.getStatus());
        row.put("statusName", doc.getStatus() != null && doc.getStatus() == 1 ? "启用" : "停用");
        row.put("createTime", doc.getCreateTime());
        row.put("updateTime", doc.getUpdateTime());
        return row;
    }

    private String resolveCategoryName(String category) {
        if ("voucher_rule".equals(category)) {
            return "优惠券规则";
        }
        if ("seckill_rule".equals(category)) {
            return "秒杀活动规则";
        }
        if ("industry_case".equals(category)) {
            return "行业运营案例";
        }
        if ("risk_rule".equals(category)) {
            return "平台风控规则";
        }
        if ("cost_rule".equals(category)) {
            return "成本计算规则";
        }
        return "其他知识";
    }

    private String resolveCategoryByIntent(String intent) {
        if ("voucher_plan".equals(intent)) {
            return "seckill_rule";
        }
        if ("review_analysis".equals(intent)) {
            return "industry_case";
        }
        if ("order_analysis".equals(intent) || "operation_chat".equals(intent)) {
            return "cost_rule";
        }
        return null;
    }

    private String resolveKeyword(String userMessage, String intent) {
        if (containsAny(userMessage, "秒杀", "周末")) {
            return "秒杀";
        }
        if (containsAny(userMessage, "优惠券", "代金券")) {
            return "优惠券";
        }
        if (containsAny(userMessage, "评价", "评论", "口碑")) {
            return "评价";
        }
        if (containsAny(userMessage, "成本", "利润", "收入", "营收")) {
            return "成本";
        }
        if ("voucher_plan".equals(intent)) {
            return "活动";
        }
        if ("review_analysis".equals(intent)) {
            return "评价";
        }
        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private double roundScore(double score) {
        // 前端只需要观察相似度大致水平，保留 4 位小数即可。
        return Math.round(score * 10000D) / 10000D;
    }

    /**
     * 内部排序对象：把文档和相似度分数绑在一起。
     *
     * <p>这里不用 DTO 暴露到外部，是因为它只服务于 retrieveByVector 的排序过程。</p>
     */
    private static class ScoredKnowledgeDoc {
        private final AgentKnowledgeDoc doc;
        private final double score;

        private ScoredKnowledgeDoc(AgentKnowledgeDoc doc, double score) {
            this.doc = doc;
            this.score = score;
        }

        private AgentKnowledgeDoc getDoc() {
            return doc;
        }

        private double getScore() {
            return score;
        }
    }
}
