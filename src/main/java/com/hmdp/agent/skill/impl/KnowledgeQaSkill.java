package com.hmdp.agent.skill.impl;

import com.hmdp.agent.skill.AgentSkill;
import com.hmdp.agent.skill.SkillContext;
import com.hmdp.agent.skill.SkillDefinition;
import com.hmdp.agent.skill.SkillResult;
import com.hmdp.agent.skill.SkillRiskLevel;
import com.hmdp.agent.skill.dto.KnowledgeQaSkillInput;
import com.hmdp.agent.skill.dto.KnowledgeQaSkillOutput;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家知识问答 Skill。
 *
 * <p>该 Skill 只调用商家隔离 RAG 检索入口，不调用旧的全局 retrieveForAgent 入口，
 * 也不调用大模型生成答案。</p>
 */
@Component
public class KnowledgeQaSkill implements AgentSkill<KnowledgeQaSkillInput, KnowledgeQaSkillOutput> {

    public static final String SKILL_NAME = "knowledge_qa_skill";

    private static final String DEFAULT_INTENT = "operation_chat";
    private static final int DEFAULT_TOP_K = 5;
    private static final String LOW_CONFIDENCE_ANSWER = "未找到足够可靠的商家知识库依据，建议补充相关知识后再回答。";
    private static final String RETRIEVED_ANSWER = "已召回相关商家知识片段，请基于 retrievedChunks 进行回答。";

    private final IMerchantAgentKnowledgeDocService knowledgeDocService;

    public KnowledgeQaSkill(IMerchantAgentKnowledgeDocService knowledgeDocService) {
        this.knowledgeDocService = knowledgeDocService;
    }

    @Override
    public String name() {
        return SKILL_NAME;
    }

    @Override
    public SkillDefinition definition() {
        return new SkillDefinition()
                .setSkillName(SKILL_NAME)
                .setDisplayName("商家知识问答 Skill")
                .setDescription("复用商家隔离 RAG 检索、重排、置信阈值和 noReliableHit 能力，返回基于知识库证据的问答结果。")
                .setVersion("v1")
                .setAllowedTools(Collections.<String>emptyList())
                .setRiskLevel(SkillRiskLevel.LOW)
                .setNeedHumanConfirm(false)
                .setModelCallable(false);
    }

    @Override
    public Class<KnowledgeQaSkillInput> inputType() {
        return KnowledgeQaSkillInput.class;
    }

    @Override
    public SkillResult<KnowledgeQaSkillOutput> execute(KnowledgeQaSkillInput input, SkillContext context) {
        if (input == null || input.getShopId() == null) {
            return SkillResult.failure("INVALID_SHOP_ID", "shopId不能为空");
        }
        if (isBlank(input.getQuestion())) {
            return SkillResult.failure("INVALID_QUESTION", "question不能为空");
        }

        Long shopId = input.getShopId();
        String intent = isBlank(input.getIntent()) ? DEFAULT_INTENT : input.getIntent().trim();
        String question = input.getQuestion().trim();
        Integer topK = resolveTopK(input.getTopK());

        List<Map<String, Object>> hits;
        try {
            hits = knowledgeDocService.retrieveForAgentForShop(shopId, intent, question, topK);
        } catch (Exception e) {
            return SkillResult.failure("RAG_RETRIEVE_FAILED",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        KnowledgeQaSkillOutput output = buildOutput(shopId, intent, question, topK, hits, context);
        return SkillResult.success(output)
                .setRiskLevel(SkillRiskLevel.LOW)
                .setNeedHumanConfirm(false)
                .setConfidence(output.getConfidence())
                .putMetadata("skillName", SKILL_NAME)
                .putMetadata("shopId", shopId)
                .putMetadata("intent", intent)
                .putMetadata("topK", topK)
                .putMetadata("noReliableHit", output.getNoReliableHit())
                .putMetadata("retrievedCount", output.getRetrievedChunks().size())
                .putMetadata("shopScoped", true)
                .putMetadata("traceId", context == null ? null : context.getTraceId());
    }

    private KnowledgeQaSkillOutput buildOutput(Long shopId,
                                               String intent,
                                               String question,
                                               Integer topK,
                                               List<Map<String, Object>> hits,
                                               SkillContext context) {
        List<Map<String, Object>> safeHits = hits == null ? new ArrayList<>() : hits;
        boolean noReliableHit = safeHits.isEmpty() || containsNoReliableHit(safeHits);
        Double confidence = resolveConfidence(safeHits);
        List<Object> chunks = new ArrayList<>(safeHits);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("skillName", SKILL_NAME);
        metadata.put("shopId", shopId);
        metadata.put("intent", intent);
        metadata.put("topK", topK);
        metadata.put("retrievedCount", chunks.size());
        metadata.put("shopScoped", true);
        metadata.put("traceId", context == null ? null : context.getTraceId());

        return new KnowledgeQaSkillOutput()
                .setShopId(shopId)
                .setIntent(intent)
                .setQuestion(question)
                .setAnswer(resolveAnswer(noReliableHit, safeHits))
                .setRetrievedChunks(chunks)
                .setConfidence(confidence)
                .setNoReliableHit(noReliableHit)
                .setTopK(topK)
                .setMetadata(metadata);
    }

    private String resolveAnswer(boolean noReliableHit, List<Map<String, Object>> hits) {
        if (noReliableHit || hits == null || hits.isEmpty()) {
            return LOW_CONFIDENCE_ANSWER;
        }
        for (Map<String, Object> hit : hits) {
            Object answer = hit == null ? null : hit.get("answer");
            if (answer != null && !isBlank(String.valueOf(answer))) {
                return String.valueOf(answer);
            }
        }
        return RETRIEVED_ANSWER;
    }

    private boolean containsNoReliableHit(List<Map<String, Object>> hits) {
        for (Map<String, Object> hit : hits) {
            if (hit != null && Boolean.TRUE.equals(hit.get("noReliableHit"))) {
                return true;
            }
        }
        return false;
    }

    private Double resolveConfidence(List<Map<String, Object>> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        Double best = null;
        for (Map<String, Object> hit : hits) {
            Double value = numberAsDouble(hit == null ? null : hit.get("confidence"));
            if (value == null) {
                value = numberAsDouble(hit == null ? null : hit.get("similarityScore"));
            }
            if (value == null) {
                value = numberAsDouble(hit == null ? null : hit.get("rerankScore"));
            }
            if (value != null && (best == null || value > best)) {
                best = value;
            }
        }
        return best;
    }

    private Double numberAsDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer resolveTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return topK;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
