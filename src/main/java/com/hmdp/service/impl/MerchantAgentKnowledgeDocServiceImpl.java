package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.agent.MerchantAgentEmbeddingService;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.AgentKnowledgeEvaluateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import com.hmdp.mapper.AgentKnowledgeDocMapper;
import com.hmdp.service.IMerchantAgentKnowledgeEvalCaseService;
import com.hmdp.service.IMerchantAgentKnowledgeEvalRunService;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
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
    /**
     * 单次 RAG 评测最多允许多少条用例。
     *
     * <p>评测接口会对每条用例执行一次召回，如果不限制数量，前端误传大数组时会造成 Redis
     * 向量计算和数据库查询压力。学习阶段 20 条足够做小型回归测试。</p>
     */
    private static final int MAX_EVALUATION_CASES = 20;
    /**
     * 语义召回最低可信相似度。
     *
     * <p>RAG 不是“有结果就塞进 Prompt”。相似度过低的知识片段很可能只是噪音，
     * 会干扰模型判断。学习阶段先用 0.55 做保守阈值，后续可结合评测集继续调参。</p>
     */
    private static final double VECTOR_MIN_SIMILARITY = 0.55D;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private MerchantAgentEmbeddingService embeddingService;
    @Resource
    private IMerchantAgentKnowledgeEvalCaseService evalCaseService;
    @Resource
    private IMerchantAgentKnowledgeEvalRunService evalRunService;

    @Override
    public Result createKnowledgeDoc(AgentKnowledgeDocRequest request) {
        Result validateResult = validateCreateRequest(request);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        AgentKnowledgeDoc doc = new AgentKnowledgeDoc()
                .setId(redisIdWorker.nextId("agent"))
                .setShopId(request.getShopId())
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
        if (request.getShopId() != null) {
            updateDoc.setShopId(request.getShopId());
        }
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
    public Result debugRetrieveForAgent(String intent, String userMessage, Integer limit) {
        if (isBlank(userMessage)) {
            return Result.fail("请输入要调试的商家问题");
        }
        int safeLimit = limit == null || limit <= 0 ? 3 : Math.min(limit, 8);
        String safeIntent = isBlank(intent) ? "operation_chat" : intent.trim();

        // 调用正式 Agent 使用的同一个召回入口，保证调试结果和线上对话链路一致。
        List<Map<String, Object>> hits = retrieveForAgent(safeIntent, userMessage.trim(), safeLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", userMessage.trim());
        result.put("intent", safeIntent);
        result.put("limit", safeLimit);
        result.put("retrievalMode", resolveRetrievalMode(hits));
        result.put("hitCount", hits.size());
        result.put("vectorMinSimilarity", VECTOR_MIN_SIMILARITY);
        result.put("qualityGate", hits.isEmpty() ? "no_reliable_hit" : "passed");
        result.put("documents", hits);
        result.put("explain", hits.isEmpty()
                ? "没有可靠知识进入 Prompt。可能原因：知识未向量化、问题与业务无关，或语义相似度低于阈值。"
                : "已使用与 Agent 对话相同的 RAG 召回链路返回 TopK 可靠知识。");
        return Result.ok(result);
    }

    @Override
    public Result evaluateRetrieval(AgentKnowledgeEvaluateRequest request) {
        int safeLimit = request == null || request.getLimit() == null || request.getLimit() <= 0
                ? 3
                : Math.min(request.getLimit(), 8);
        boolean customCaseRequest = request != null && request.getCases() != null && !request.getCases().isEmpty();
        List<AgentKnowledgeEvaluateRequest.CaseItem> cases = customCaseRequest
                ? sanitizeEvaluationCases(request.getCases())
                : evalCaseService.listEnabledCaseItems();
        String caseSource = customCaseRequest ? "custom" : "default";
        if (!customCaseRequest && cases.isEmpty()) {
            cases = defaultEvaluationCases();
        } else if (!customCaseRequest) {
            caseSource = "persisted";
        }
        // 如果前端传了自定义用例，但全部为空或不合法，回退到默认用例，保证评测接口仍然可用。
        if (cases.isEmpty()) {
            cases = defaultEvaluationCases();
            caseSource = "default_fallback";
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int topKPassCount = 0;
        int top1PassCount = 0;
        int noHitCount = 0;
        for (AgentKnowledgeEvaluateRequest.CaseItem item : cases) {
            Map<String, Object> row = evaluateOneCase(item, safeLimit);
            rows.add(row);
            if (Boolean.TRUE.equals(row.get("topKPassed"))) {
                topKPassCount++;
            }
            if (Boolean.TRUE.equals(row.get("top1Passed"))) {
                top1PassCount++;
            }
            if (Boolean.TRUE.equals(row.get("noReliableHit"))) {
                noHitCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("passCount", topKPassCount);
        result.put("failCount", rows.size() - topKPassCount);
        result.put("passRate", formatRate(topKPassCount, rows.size()));
        result.put("top1PassCount", top1PassCount);
        result.put("topKPassCount", topKPassCount);
        result.put("top1PassRate", formatRate(top1PassCount, rows.size()));
        result.put("topKPassRate", formatRate(topKPassCount, rows.size()));
        result.put("noReliableHitCount", noHitCount);
        result.put("limit", safeLimit);
        result.put("caseSource", caseSource);
        result.put("maxCaseCount", MAX_EVALUATION_CASES);
        result.put("items", rows);
        result.put("diagnosis", buildEvaluationDiagnosis(rows));
        result.put("vectorMinSimilarity", VECTOR_MIN_SIMILARITY);
        result.put("explain", "评测口径：Top1 命中衡量第一条是否准确；TopK 命中衡量通过质量闸门后的候选结果是否覆盖预期分类。");

        // 评测历史是旁路观测能力：保存成功就把 runId 返回给前端，保存失败也不影响本次评测结果。
        Long runId = evalRunService.recordRun(result);
        if (runId != null) {
            result.put("runId", String.valueOf(runId));
        }
        return Result.ok(result);
    }

    @Override
    public List<Map<String, Object>> retrieveForAgent(String intent, String userMessage, Integer limit) {
        return retrieveForAgentScoped(null, intent, userMessage, limit);
    }

    @Override
    public List<Map<String, Object>> retrieveForAgentForShop(Long shopId, String intent, String userMessage, Integer limit) {
        if (shopId == null || isBlank(userMessage)) {
            return new ArrayList<>();
        }
        return retrieveForAgentScoped(shopId, intent, userMessage, limit);
    }

    private List<Map<String, Object>> retrieveForAgentScoped(Long shopId, String intent, String userMessage, Integer limit) {
        // Agent 内部默认只取 Top3，避免把太多知识片段塞进 Prompt 导致成本和噪音上升。
        int safeLimit = limit == null || limit <= 0 ? 3 : Math.min(limit, 8);

        // 先根据问题文本和意图缩小候选分类范围。
        // 注意这里返回的是“候选分类列表”，不是唯一分类，避免普通优惠券问题被硬路由到秒杀规则。
        List<String> categories = resolveCategoriesForRetrieval(intent, userMessage);

        // 第一优先级：语义向量检索。向量先粗召回，再用规则版 Rerank 精排 TopK。
        List<Map<String, Object>> vectorHits = retrieveByVector(shopId, categories, userMessage, safeLimit);
        if (!vectorHits.isEmpty()) {
            return filterRowsForShopScope(vectorHits, shopId);
        }

        // 兜底策略：如果向量检索不可用，就回到关键词检索，保证 Agent 主流程仍然可用。
        return filterRowsForShopScope(retrieveByKeyword(shopId, categories, userMessage, intent, safeLimit), shopId);
    }

    /**
     * 语义向量召回。
     *
     * <p>业务逻辑：
     * 1. 先把商家问题转换成 query 向量；
     * 2. 再读取同分类知识 chunk 的 doc 向量；
     * 3. 用余弦相似度做粗排，保留 TopN 候选；
     * 4. 用规则版 Rerank 结合分类、标题、正文关键词重新排序；
     * 5. 把相似度和 rerank 分数返回给前端，方便观察排序依据。</p>
     */
    private List<Map<String, Object>> retrieveByVector(Long shopId, List<String> categories, String userMessage, int safeLimit) {
        // 1. 把商家输入的问题向量化，例如“帮我设计周末秒杀券” -> queryVector。
        List<Float> queryVector = embeddingService.embedQuery(userMessage);
        if (queryVector == null || queryVector.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 从 MySQL 读取候选知识文档。这里不取全部，只取启用、已向量化、候选分类内的近 200 条。
        // 学习项目数据量小，可以直接 Java 内存算；生产大规模场景应交给向量数据库做 ANN 检索。
        List<AgentKnowledgeDoc> candidates = query()
                .eq("status", 1)
                .and(shopId != null, wrapper -> wrapper.isNull("shop_id").or().eq("shop_id", shopId))
                .in(hasCategories(categories), "category", categories)
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
            if (score < VECTOR_MIN_SIMILARITY) {
                continue;
            }
            scoredDocs.add(new ScoredKnowledgeDoc(doc, score));
        }

        // 5. 按相似度从高到低排序，先得到语义粗召回候选。
        scoredDocs.sort(Comparator.comparingDouble(ScoredKnowledgeDoc::getScore).reversed());

        List<Map<String, Object>> rows = new ArrayList<>();
        int candidateLimit = Math.min(Math.max(safeLimit * 4, safeLimit), scoredDocs.size());
        for (int i = 0; i < candidateLimit; i++) {
            ScoredKnowledgeDoc scoredDoc = scoredDocs.get(i);
            Map<String, Object> row = toDocRow(scoredDoc.getDoc());

            // retrievalMode 和 similarityScore 会返回给前端调试面板，便于观察 RAG 是否真的命中。
            row.put("retrievalMode", "semantic_vector");
            row.put("similarityScore", roundScore(scoredDoc.getScore()));
            row.put("originalScore", roundScore(scoredDoc.getScore()));
            row.put("qualityStatus", "passed");
            row.put("minSimilarity", VECTOR_MIN_SIMILARITY);
            row.put("candidateCategories", categories);
            rows.add(row);
        }
        return rerankKnowledgeRows(rows, categories, userMessage, safeLimit);
    }

    /**
     * 关键词兜底召回。
     *
     * <p>当 API Key 未配置、Embedding 服务异常、Redis 中还没有向量时，RAG 不能阻断 Agent 主流程。
     * 所以这里保留原来的 MySQL like 检索，保证学习项目在没有向量库时也能运行。</p>
     */
    private List<Map<String, Object>> retrieveByKeyword(Long shopId, List<String> categories, String userMessage, String intent, int safeLimit) {
        // 关键词由简单规则提取，例如“秒杀/周末” -> “秒杀”，“评价/口碑” -> “评价”。
        String keyword = resolveKeyword(userMessage, intent);
        if (isBlank(keyword) && !isStrongBusinessIntent(intent)) {
            // 兜底检索也要有边界：业务相关性不足时不强行按分类取最近文档，
            // 否则“我帅吗”这类问题也可能被塞入优惠券规则，造成 Prompt 噪音。
            return new ArrayList<>();
        }
        int candidateLimit = Math.max(safeLimit * 4, safeLimit);
        List<AgentKnowledgeDoc> docs = query()
                .eq("status", 1)
                .and(shopId != null, wrapper -> wrapper.isNull("shop_id").or().eq("shop_id", shopId))
                .in(hasCategories(categories), "category", categories)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("update_time")
                .last("LIMIT " + candidateLimit)
                .list();

        if (docs.isEmpty() && hasCategories(categories)) {
            // 如果关键词没命中，但当前意图能映射到分类，就退一步取该分类最近更新的知识。
            // 这样至少能给 Agent 一些行业规则背景，而不是完全没有 RAG 内容。
            docs = query()
                    .eq("status", 1)
                    .and(shopId != null, wrapper -> wrapper.isNull("shop_id").or().eq("shop_id", shopId))
                    .in("category", categories)
                    .orderByDesc("update_time")
                    .last("LIMIT " + candidateLimit)
                    .list();
        }

        List<Map<String, Object>> rows = toDocRows(docs);
        for (Map<String, Object> row : rows) {
            row.put("retrievalMode", "mysql_keyword_fallback");
            row.put("qualityStatus", isBlank(keyword) ? "category_fallback" : "keyword_fallback");
            row.put("candidateCategories", categories);
            row.put("originalScore", 0);
        }
        return rerankKnowledgeRows(rows, categories, userMessage, safeLimit);
    }

    /**
     * 规则版 Rerank。
     *
     * <p>第一版先不接额外模型，而是用可解释规则做精排：
     * 原始向量相似度负责“语义相关”，候选分类负责“业务方向”，标题/正文命中负责“文本证据”。
     * 这样做的好处是成本低、可解释，适合学习阶段先把二阶段检索链路跑通。</p>
     */
    private List<Map<String, Object>> rerankKnowledgeRows(List<Map<String, Object>> rows,
                                                          List<String> categories,
                                                          String userMessage,
                                                          int safeLimit) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> terms = extractRerankTerms(userMessage);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            // originalRank 表示粗召回阶段的名次，后面和 rerankRank 对比即可观察精排是否生效。
            row.put("originalRank", i + 1);
            RerankResult result = scoreKnowledgeRow(row, categories, terms);
            row.put("rerankMode", "rule_v1");
            row.put("rerankScore", roundScore(result.score));
            row.put("rerankReason", result.reason);
        }

        rows.sort((left, right) -> {
            int rerankCompare = Double.compare(numberValue(right.get("rerankScore")), numberValue(left.get("rerankScore")));
            if (rerankCompare != 0) {
                return rerankCompare;
            }
            return Double.compare(numberValue(right.get("originalScore")), numberValue(left.get("originalScore")));
        });

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(safeLimit, rows.size()); i++) {
            Map<String, Object> row = rows.get(i);
            int rerankRank = i + 1;
            int originalRank = (int) numberValue(row.get("originalRank"));
            row.put("rerankRank", rerankRank);
            row.put("rerankDelta", originalRank - rerankRank);
            result.add(row);
        }
        return result;
    }

    private RerankResult scoreKnowledgeRow(Map<String, Object> row, List<String> categories, List<String> terms) {
        String category = String.valueOf(row.getOrDefault("category", ""));
        String title = String.valueOf(row.getOrDefault("title", ""));
        String content = String.valueOf(row.getOrDefault("content", ""));

        double originalScore = numberValue(row.get("originalScore"));
        double score = originalScore * 60D;
        List<String> reasons = new ArrayList<>();
        if (originalScore > 0) {
            reasons.add("语义相似度 " + roundScore(originalScore));
        }

        if (categories != null && categories.contains(category)) {
            score += 20D;
            reasons.add("命中候选分类");
        }

        int titleHits = countTermHits(title, terms);
        if (titleHits > 0) {
            score += Math.min(18D, titleHits * 6D);
            reasons.add("标题命中关键词 " + titleHits + " 个");
        }

        int contentHits = countTermHits(content, terms);
        if (contentHits > 0) {
            score += Math.min(12D, contentHits * 3D);
            reasons.add("正文命中关键词 " + contentHits + " 个");
        }

        if (reasons.isEmpty()) {
            reasons.add("按默认候选顺序保留");
        }
        return new RerankResult(score, String.join("；", reasons));
    }

    private List<String> extractRerankTerms(String userMessage) {
        List<String> terms = new ArrayList<>();
        if (isBlank(userMessage)) {
            return terms;
        }

        String[] businessTerms = {
                "秒杀", "周末", "限时", "抢购", "优惠券", "代金券", "折扣", "满减", "拉新", "复购",
                "成本", "利润", "毛利", "订单", "成交", "支付", "核销", "转化",
                "评价", "评论", "口碑", "探店", "内容", "风险", "人工确认", "库存"
        };
        for (String term : businessTerms) {
            if (userMessage.contains(term)) {
                addCategory(terms, term);
            }
        }

        String keyword = resolveKeyword(userMessage, "operation_chat");
        if (!isBlank(keyword)) {
            addCategory(terms, keyword);
        }
        return terms;
    }

    private int countTermHits(String text, List<String> terms) {
        if (isBlank(text) || terms == null || terms.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String term : terms) {
            if (!isBlank(term) && text.contains(term)) {
                count++;
            }
        }
        return count;
    }

    private double numberValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0D;
        }
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
        row.put("shopId", doc.getShopId());
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

    /**
     * 应用层最终兜底过滤。
     *
     * <p>当前向量召回先从 MySQL 读候选文档再到 Redis 取向量，因此查询阶段已经能按 shop_id
     * 过滤。这里再做一次最终结果过滤，防止未来替换为向量索引时缺少 metadata filter 导致其他
     * 商家的私有 chunk 泄漏。安全优先于召回率：过滤后为空就返回 noReliableHit。</p>
     */
    List<Map<String, Object>> filterRowsForShopScope(List<Map<String, Object>> rows, Long shopId) {
        if (shopId == null || rows == null || rows.isEmpty()) {
            return rows == null ? new ArrayList<>() : rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (isRowVisibleToShop(row, shopId)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    boolean isRowVisibleToShop(Map<String, Object> row, Long shopId) {
        if (row == null || shopId == null) {
            return false;
        }
        Object rowShopId = row.get("shopId");
        if (rowShopId == null) {
            return true;
        }
        if (rowShopId instanceof Number) {
            return shopId.equals(((Number) rowShopId).longValue());
        }
        try {
            return shopId.equals(Long.valueOf(String.valueOf(rowShopId)));
        } catch (NumberFormatException e) {
            return false;
        }
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

    private List<String> resolveCategoriesForRetrieval(String intent, String userMessage) {
        List<String> categories = new ArrayList<>();

        // 明确提到秒杀、限时抢、周末爆发时，优先召回秒杀规则，同时补充成本规则做利润约束。
        if (containsAny(userMessage, "秒杀", "限时抢", "抢购", "周末爆发")) {
            addCategory(categories, "seckill_rule");
            addCategory(categories, "cost_rule");
            return categories;
        }

        // 普通优惠券、代金券、折扣、拉新、复购问题，优先召回普通券规则，而不是秒杀规则。
        if (containsAny(userMessage, "优惠券", "代金券", "折扣", "打折", "满减", "拉新", "复购", "老客")) {
            addCategory(categories, "voucher_rule");
            addCategory(categories, "cost_rule");
            return categories;
        }

        if (containsAny(userMessage, "成本", "利润", "毛利", "收入", "营收", "亏")) {
            addCategory(categories, "cost_rule");
            addCategory(categories, "voucher_rule");
            return categories;
        }

        if (containsAny(userMessage, "评价", "评论", "口碑", "探店", "内容")) {
            addCategory(categories, "industry_case");
            return categories;
        }

        if (containsAny(userMessage, "风险", "人工确认", "群发", "退款", "删除", "核销", "库存")) {
            addCategory(categories, "risk_rule");
            addCategory(categories, "cost_rule");
            return categories;
        }

        if ("voucher_plan".equals(intent)) {
            addCategory(categories, "voucher_rule");
            addCategory(categories, "seckill_rule");
            addCategory(categories, "cost_rule");
            return categories;
        }
        if ("review_analysis".equals(intent)) {
            addCategory(categories, "industry_case");
            return categories;
        }
        if ("order_analysis".equals(intent) || "operation_chat".equals(intent)) {
            addCategory(categories, "cost_rule");
            addCategory(categories, "voucher_rule");
            addCategory(categories, "industry_case");
            return categories;
        }
        return categories;
    }

    private String resolveKeyword(String userMessage, String intent) {
        if (containsAny(userMessage, "秒杀", "限时抢", "抢购", "周末")) {
            return "秒杀";
        }
        if (containsAny(userMessage, "优惠券", "代金券", "折扣", "打折", "满减", "拉新", "复购")) {
            return "优惠券";
        }
        if (containsAny(userMessage, "评价", "评论", "口碑")) {
            return "评价";
        }
        if (containsAny(userMessage, "成本", "利润", "收入", "营收")) {
            return "成本";
        }
        if (containsAny(userMessage, "订单", "经营", "成交", "支付", "核销", "转化")) {
            return "订单";
        }
        if (containsAny(userMessage, "人工确认", "风险", "退款", "删除", "群发", "库存")) {
            return "风险";
        }
        if ("voucher_plan".equals(intent)) {
            return "活动";
        }
        if ("review_analysis".equals(intent)) {
            return "评价";
        }
        return "";
    }

    private boolean isStrongBusinessIntent(String intent) {
        // 这些意图是由 Agent 前置意图识别得到的业务场景，即使用户没写出非常明确的关键词，
        // 也允许使用分类最近知识做兜底，保证订单分析、活动方案这类核心场景有知识背景。
        return "voucher_plan".equals(intent)
                || "review_analysis".equals(intent)
                || "order_analysis".equals(intent);
    }

    private void addCategory(List<String> categories, String category) {
        if (!categories.contains(category)) {
            categories.add(category);
        }
    }

    private boolean hasCategories(List<String> categories) {
        return categories != null && !categories.isEmpty();
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

    private Map<String, Object> evaluateOneCase(AgentKnowledgeEvaluateRequest.CaseItem item, int safeLimit) {
        String message = item == null || isBlank(item.getMessage()) ? "" : item.getMessage().trim();
        String intent = item == null || isBlank(item.getIntent()) ? "operation_chat" : item.getIntent().trim();
        List<String> expectedCategories = item == null || item.getExpectedCategories() == null
                ? new ArrayList<>()
                : item.getExpectedCategories();
        List<Map<String, Object>> hits = isBlank(message)
                ? new ArrayList<>()
                : retrieveForAgent(intent, message, safeLimit);

        List<String> hitCategories = new ArrayList<>();
        List<Map<String, Object>> topDocs = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            String category = String.valueOf(hit.getOrDefault("category", ""));
            addCategory(hitCategories, category);

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("docId", hit.get("docId"));
            doc.put("title", hit.get("title"));
            doc.put("category", hit.get("category"));
            doc.put("categoryName", hit.get("categoryName"));
            doc.put("similarityScore", hit.get("similarityScore"));
            doc.put("originalScore", hit.get("originalScore"));
            doc.put("originalRank", hit.get("originalRank"));
            doc.put("rerankRank", hit.get("rerankRank"));
            doc.put("rerankDelta", hit.get("rerankDelta"));
            doc.put("rerankScore", hit.get("rerankScore"));
            doc.put("rerankReason", hit.get("rerankReason"));
            doc.put("rerankMode", hit.get("rerankMode"));
            doc.put("retrievalMode", hit.get("retrievalMode"));
            topDocs.add(doc);
        }

        String top1Category = topDocs.isEmpty() ? "" : String.valueOf(topDocs.get(0).getOrDefault("category", ""));
        boolean top1Passed = hasIntersection(expectedCategories, Arrays.asList(top1Category));
        boolean topKPassed = hasIntersection(expectedCategories, hitCategories);
        boolean noReliableHit = hits.isEmpty();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("message", message);
        row.put("intent", intent);
        row.put("expectedCategories", expectedCategories);
        row.put("hitCategories", hitCategories);
        row.put("retrievalMode", resolveRetrievalMode(hits));
        row.put("top1", topDocs.isEmpty() ? null : topDocs.get(0));
        row.put("topK", topDocs);
        row.put("passed", topKPassed);
        row.put("top1Passed", top1Passed);
        row.put("topKPassed", topKPassed);
        row.put("noReliableHit", noReliableHit);
        row.put("reason", resolveEvaluationReason(top1Passed, topKPassed, noReliableHit));
        row.put("diagnosis", diagnoseEvaluationCase(expectedCategories, hitCategories, top1Passed, topKPassed, noReliableHit));
        return row;
    }

    private String formatRate(int count, int total) {
        return total <= 0 ? "0.00%" : String.format("%.2f%%", count * 100.0 / total);
    }

    private String resolveEvaluationReason(boolean top1Passed, boolean topKPassed, boolean noReliableHit) {
        if (top1Passed) {
            return "Top1 命中期望分类，召回排序质量较好";
        }
        if (topKPassed) {
            return "TopK 命中但 Top1 未命中，需要优化排序或补充更高质量知识";
        }
        if (noReliableHit) {
            return "没有可靠知识通过质量闸门，可能需要向量化知识或降低阈值";
        }
        return "TopK 未命中期望分类，需要补充知识或调整召回策略";
    }

    /**
     * 生成单条用例诊断。
     *
     * <p>这里把评测结果翻译成“可以行动”的问题类型：
     * 1. 没有可靠召回：优先检查向量化、阈值、知识覆盖；
     * 2. TopK 命中但 Top1 未命中：说明知识在候选里，但排序不够好；
     * 3. TopK 未命中但有召回：说明召回到了错误分类，通常要看分类路由或补知识。</p>
     */
    private Map<String, Object> diagnoseEvaluationCase(List<String> expectedCategories,
                                                       List<String> hitCategories,
                                                       boolean top1Passed,
                                                       boolean topKPassed,
                                                       boolean noReliableHit) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        if (top1Passed) {
            diagnosis.put("type", "OK");
            diagnosis.put("title", "召回正常");
            diagnosis.put("advice", "Top1 已命中期望分类，当前用例暂不需要调整。");
            return diagnosis;
        }
        if (topKPassed) {
            diagnosis.put("type", "RANKING_WEAK");
            diagnosis.put("title", "排序不够稳定");
            diagnosis.put("advice", "相关知识已经进入 TopK，但第一条不是最佳分类。建议结合 Rerank 排名变化，补充更聚焦的标题、正文关键词或调整精排权重。");
            return diagnosis;
        }
        if (noReliableHit) {
            diagnosis.put("type", "NO_RELIABLE_HIT");
            diagnosis.put("title", "没有可靠召回");
            diagnosis.put("advice", "没有知识通过质量闸门。建议检查对应分类知识是否已向量化，必要时补充知识或重新评估相似度阈值。");
            return diagnosis;
        }

        diagnosis.put("type", "CATEGORY_MISMATCH");
        diagnosis.put("title", "召回分类偏移");
        diagnosis.put("advice", "已召回知识，但命中分类与预期不一致。建议检查意图到知识分类的路由规则，或补充 " + joinCategories(expectedCategories) + " 相关知识。");
        diagnosis.put("expectedCategories", expectedCategories);
        diagnosis.put("hitCategories", hitCategories);
        return diagnosis;
    }

    /**
     * 生成整批评测的自动诊断摘要。
     *
     * <p>这相当于给 RAG 评测结果做一次“小体检”：不只给通过率，
     * 还告诉开发者主要问题集中在哪里，下一步优先修什么。</p>
     */
    private Map<String, Object> buildEvaluationDiagnosis(List<Map<String, Object>> rows) {
        int noReliableHitCount = 0;
        int rankingWeakCount = 0;
        int categoryMismatchCount = 0;
        int passedCount = 0;
        List<Map<String, Object>> failedCases = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> diagnosis = (Map<String, Object>) row.get("diagnosis");
            String type = diagnosis == null ? "" : String.valueOf(diagnosis.getOrDefault("type", ""));
            if ("OK".equals(type)) {
                passedCount++;
                continue;
            }
            if ("NO_RELIABLE_HIT".equals(type)) {
                noReliableHitCount++;
            } else if ("RANKING_WEAK".equals(type)) {
                rankingWeakCount++;
            } else if ("CATEGORY_MISMATCH".equals(type)) {
                categoryMismatchCount++;
            }

            Map<String, Object> failedCase = new LinkedHashMap<>();
            failedCase.put("message", row.get("message"));
            failedCase.put("type", type);
            failedCase.put("title", diagnosis == null ? "未知问题" : diagnosis.get("title"));
            failedCase.put("advice", diagnosis == null ? "请查看评测详情进一步定位。" : diagnosis.get("advice"));
            failedCases.add(failedCase);
        }

        List<String> recommendations = new ArrayList<>();
        if (noReliableHitCount > 0) {
            recommendations.add("优先检查未召回用例对应分类的知识是否已向量化，并确认相似度阈值是否过高。");
        }
        if (categoryMismatchCount > 0) {
            recommendations.add("检查商家问题到知识分类的路由规则，避免普通优惠券、秒杀券、评价问题互相串类。");
        }
        if (rankingWeakCount > 0) {
            recommendations.add("对 TopK 命中但 Top1 不准的用例，观察 Rerank 是否把目标知识前移；如果没有前移，优先补充标题关键词或调整精排权重。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前评测集表现稳定，可以继续增加更难的边界用例验证 RAG 鲁棒性。");
        }

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("level", rows.isEmpty() || failedCases.isEmpty() ? "GOOD" : (failedCases.size() >= Math.max(2, rows.size() / 2) ? "RISK" : "WARN"));
        diagnosis.put("title", failedCases.isEmpty() ? "RAG 召回稳定" : resolveBatchDiagnosisTitle(noReliableHitCount, rankingWeakCount, categoryMismatchCount));
        diagnosis.put("summary", failedCases.isEmpty()
                ? "本轮评测没有发现失败样本，TopK 召回覆盖了预期分类。"
                : "本轮发现 " + failedCases.size() + " 条需要关注的样本，其中无可靠召回 " + noReliableHitCount
                + " 条、排序不稳定 " + rankingWeakCount + " 条、分类偏移 " + categoryMismatchCount + " 条。");
        diagnosis.put("passedCount", passedCount);
        diagnosis.put("failedCount", failedCases.size());
        diagnosis.put("noReliableHitCount", noReliableHitCount);
        diagnosis.put("rankingWeakCount", rankingWeakCount);
        diagnosis.put("categoryMismatchCount", categoryMismatchCount);
        diagnosis.put("recommendations", recommendations);
        diagnosis.put("failedCases", failedCases);
        return diagnosis;
    }

    private String resolveBatchDiagnosisTitle(int noReliableHitCount, int rankingWeakCount, int categoryMismatchCount) {
        if (noReliableHitCount >= rankingWeakCount && noReliableHitCount >= categoryMismatchCount) {
            return "主要问题：可靠召回不足";
        }
        if (categoryMismatchCount >= rankingWeakCount) {
            return "主要问题：召回分类偏移";
        }
        return "主要问题：Top1 排序不稳定";
    }

    private String joinCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return "目标分类";
        }
        return String.join("、", categories);
    }

    private List<AgentKnowledgeEvaluateRequest.CaseItem> sanitizeEvaluationCases(List<AgentKnowledgeEvaluateRequest.CaseItem> rawCases) {
        List<AgentKnowledgeEvaluateRequest.CaseItem> cases = new ArrayList<>();
        if (rawCases == null) {
            return cases;
        }
        for (AgentKnowledgeEvaluateRequest.CaseItem rawCase : rawCases) {
            if (cases.size() >= MAX_EVALUATION_CASES) {
                break;
            }
            if (rawCase == null || isBlank(rawCase.getMessage())) {
                continue;
            }

            List<String> expectedCategories = new ArrayList<>();
            if (rawCase.getExpectedCategories() != null) {
                for (String category : rawCase.getExpectedCategories()) {
                    if (!isBlank(category)) {
                        expectedCategories.add(category.trim());
                    }
                }
            }
            // 没有期望分类的用例无法判断是否命中，直接跳过，避免评测指标失真。
            if (expectedCategories.isEmpty()) {
                continue;
            }

            AgentKnowledgeEvaluateRequest.CaseItem item = new AgentKnowledgeEvaluateRequest.CaseItem();
            item.setMessage(rawCase.getMessage().trim());
            item.setIntent(isBlank(rawCase.getIntent()) ? "operation_chat" : rawCase.getIntent().trim());
            item.setExpectedCategories(expectedCategories);
            cases.add(item);
        }
        return cases;
    }

    private List<AgentKnowledgeEvaluateRequest.CaseItem> defaultEvaluationCases() {
        List<AgentKnowledgeEvaluateRequest.CaseItem> cases = new ArrayList<>();
        cases.add(caseItem("帮我设计一张周末秒杀券", "voucher_plan", "seckill_rule", "cost_rule"));
        cases.add(caseItem("优惠券折扣不要太低但要吸引人", "voucher_plan", "voucher_rule", "cost_rule"));
        cases.add(caseItem("最近订单少应该先检查什么", "order_analysis", "cost_rule", "voucher_rule"));
        cases.add(caseItem("KTV评价说排队久怎么办", "review_analysis", "industry_case"));
        cases.add(caseItem("哪些操作必须人工确认", "operation_chat", "risk_rule"));
        return cases;
    }

    private AgentKnowledgeEvaluateRequest.CaseItem caseItem(String message, String intent, String... categories) {
        AgentKnowledgeEvaluateRequest.CaseItem item = new AgentKnowledgeEvaluateRequest.CaseItem();
        item.setMessage(message);
        item.setIntent(intent);
        item.setExpectedCategories(Arrays.asList(categories));
        return item;
    }

    private boolean hasIntersection(List<String> left, List<String> right) {
        if (left == null || right == null) {
            return false;
        }
        for (String item : left) {
            if (right.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private String resolveRetrievalMode(List<Map<String, Object>> hits) {
        if (hits == null || hits.isEmpty()) {
            return "skipped_or_no_hit";
        }
        Object mode = hits.get(0).get("retrievalMode");
        return mode == null ? "unknown" : String.valueOf(mode);
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

    /**
     * Rerank 内部打分结果。
     */
    private static class RerankResult {
        private final double score;
        private final String reason;

        private RerankResult(double score, String reason) {
            this.score = score;
            this.reason = reason;
        }
    }
}
