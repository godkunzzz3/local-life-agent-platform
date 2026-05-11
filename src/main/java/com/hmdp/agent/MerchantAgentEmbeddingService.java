package com.hmdp.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
        EmbeddingModel embeddingModel = buildEmbeddingModel();
        Embedding embedding = embeddingModel.embed(doc.getContent()).content();
        String vectorKey = VECTOR_KEY_PREFIX + doc.getId();
        stringRedisTemplate.opsForValue().set(vectorKey, toJson(embedding.vectorAsList()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", String.valueOf(doc.getId()));
        result.put("title", doc.getTitle());
        result.put("vectorId", vectorKey);
        result.put("provider", "langchain4j-dashscope");
        result.put("modelName", modelName);
        result.put("dimension", embedding.dimension());
        result.put("costMillis", System.currentTimeMillis() - start);
        return Result.ok(result);
    }

    private EmbeddingModel buildEmbeddingModel() {
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
}
