package com.hmdp.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家运营 Agent Embedding 服务。
 *
 * <p>职责边界：
 * 1. 调用 DashScope/Qwen Embedding 模型，把知识 chunk 文本转换为向量；
 * 2. 把向量保存到 Redis，MySQL 只保存 vectorId；
 * 3. 不负责检索排序，下一步语义检索会基于这里保存的向量计算相似度。</p>
 */
@Slf4j
@Component
public class MerchantAgentEmbeddingService {

    private static final String VECTOR_KEY_PREFIX = "agent:knowledge:embedding:";

    @Value("${merchant-agent.embedding.api-key:${merchant-agent.model.api-key:}}")
    private String apiKey;

    @Value("${merchant-agent.embedding.base-url:${merchant-agent.model.base-url:}}")
    private String baseUrl;

    @Value("${merchant-agent.embedding.model-name:text-embedding-v4}")
    private String modelName;

    @Value("${merchant-agent.embedding.dimension:}")
    private String dimension;

    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 为单条知识文档生成向量并保存。
     *
     * <p>这里的“文档”实际已经是 chunk。向量本体放 Redis，是为了避免 MySQL TEXT/JSON 字段承载
     * 大数组；MySQL 的 vectorId 只保存 Redis key，后续检索时按 key 取向量即可。</p>
     */
    public Result embedKnowledgeDoc(AgentKnowledgeDoc doc) {
        if (doc == null) {
            return Result.fail("知识文档不存在");
        }
        if (isBlank(doc.getContent())) {
            return Result.fail("知识文档内容不能为空");
        }
        if (isBlank(apiKey)) {
            return Result.fail("未配置 Embedding API Key，请先配置 DASHSCOPE_API_KEY");
        }

        long start = System.currentTimeMillis();
        try {
            // 1. 调用 DashScope Embedding 模型，把知识 chunk 的自然语言文本转换成向量。
            // 例如“周末秒杀券库存建议 50-100 张”会变成一个 1024 维浮点数组。
            Embedding embedding = createEmbedding(doc.getContent());

            // 2. Redis key 作为 vectorId。MySQL 保存这个 key，真正的大数组存在 Redis。
            String vectorKey = VECTOR_KEY_PREFIX + doc.getId();

            // 3. LangChain4j 返回的是 List<Float>，RedisTemplate 保存字符串，所以这里转成 JSON。
            stringRedisTemplate.opsForValue().set(vectorKey, toJson(embedding.vectorAsList()));

            // 4. 返回给前端/调用方，方便确认本次用了哪个模型、生成了多少维向量。
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("docId", String.valueOf(doc.getId()));
            result.put("title", doc.getTitle());
            result.put("vectorId", vectorKey);
            result.put("provider", "langchain4j-dashscope");
            result.put("modelName", modelName);
            result.put("dimension", embedding.dimension());
            result.put("costMillis", System.currentTimeMillis() - start);
            return Result.ok(result);
        } catch (Throwable e) {
            // 模型调用属于外部依赖，失败时返回脱敏后的原因，便于学习和联调定位。
            log.warn("知识文档向量化失败，docId={}, modelName={}, errorType={}, message={}",
                    doc.getId(), modelName, e.getClass().getSimpleName(), e.getMessage(), e);
            return Result.fail("Embedding 向量化失败："
                    + e.getClass().getSimpleName() + " - " + safeErrorMessage(e.getMessage()));
        }
    }

    /**
     * 把用户问题转换成查询向量。
     *
     * <p>这是语义检索的第一步：用户问题和知识文档必须进入同一个向量空间，后面才能用
     * 余弦相似度比较“问题”和“知识片段”的语义距离。</p>
     */
    public List<Float> embedQuery(String queryText) {
        if (isBlank(queryText) || isBlank(apiKey)) {
            return null;
        }
        try {
            // 用户问题也必须使用同一个 Embedding 模型向量化。
            // 只有 query 向量和 doc 向量处在同一个向量空间，余弦相似度才有意义。
            return createEmbedding(queryText).vectorAsList();
        } catch (Throwable e) {
            // RAG 检索是增强能力，查询向量化失败时允许上层降级为关键词检索。
            log.warn("用户问题向量化失败，modelName={}, errorType={}, message={}",
                    modelName, e.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从 Redis 读取知识文档向量。
     *
     * <p>MySQL 只保存 vectorId，真正的 1024 维数组存在 Redis。这样可以减少关系库字段膨胀，
     * 也方便后续替换为 Redis Vector 或专业向量库。</p>
     */
    public List<Float> loadVector(String vectorId) {
        if (isBlank(vectorId)) {
            return null;
        }
        // vectorId 类似 agent:knowledge:embedding:300001，本质就是 Redis 里的字符串 key。
        String vectorJson = stringRedisTemplate.opsForValue().get(vectorId);
        if (isBlank(vectorJson)) {
            return null;
        }
        try {
            // Redis 里保存的是 JSON 数组，这里反序列化回 List<Float> 参与相似度计算。
            return objectMapper.readValue(vectorJson, new TypeReference<List<Float>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("读取知识向量失败，vectorId={}, message={}", vectorId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * <p>公式：cos(A,B)=A·B/(|A|*|B|)。值越接近 1，说明用户问题和知识文档语义越接近。</p>
     */
    public double cosineSimilarity(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.size(); i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);

            // dot 是点积 A·B，表示两个向量在方向上的重合程度。
            dot += leftValue * rightValue;

            // norm 是向量长度的平方和，最后会开根号得到 |A| 和 |B|。
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private Embedding createEmbedding(String text) {
        // 每次调用都构建模型客户端，代码简单直观；后续要优化性能时可以把模型客户端做成 Bean 缓存。
        EmbeddingModel embeddingModel = buildEmbeddingModel();
        return embeddingModel.embed(text).content();
    }

    private EmbeddingModel buildEmbeddingModel() {
        // QwenEmbeddingModel 是 LangChain4j 对 DashScope 文本向量模型的封装。
        QwenEmbeddingModel.QwenEmbeddingModelBuilder builder = QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (!isBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        Integer parsedDimension = parseDimension();
        if (parsedDimension != null) {
            builder.dimension(parsedDimension);
        }
        return builder.build();
    }

    private Integer parseDimension() {
        if (isBlank(dimension)) {
            return null;
        }
        try {
            return Integer.valueOf(dimension);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(List<Float> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化知识向量失败", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeErrorMessage(String message) {
        if (isBlank(message)) {
            return "请查看后端日志";
        }
        String safeMessage = message;
        if (!isBlank(apiKey)) {
            safeMessage = safeMessage.replace(apiKey, "***");
        }
        int maxLength = 220;
        if (safeMessage.length() > maxLength) {
            return safeMessage.substring(0, maxLength) + "...";
        }
        return safeMessage;
    }
}
