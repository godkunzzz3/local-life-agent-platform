package com.hmdp.agent;

import com.hmdp.dto.AgentMemoryCandidateRequest;
import com.hmdp.service.AgentMemoryValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 第一版 Memory Candidate 规则提取器。
 *
 * <p>只做确定性规则，不调用大模型；命中后也只生成候选，不写长期 Memory。</p>
 */
@Component
public class MerchantAgentMemoryCandidateExtractor {

    private final AgentMemoryValidator memoryValidator;

    public MerchantAgentMemoryCandidateExtractor() {
        this(new AgentMemoryValidator());
    }

    public MerchantAgentMemoryCandidateExtractor(AgentMemoryValidator memoryValidator) {
        this.memoryValidator = memoryValidator == null ? new AgentMemoryValidator() : memoryValidator;
    }

    public List<AgentMemoryCandidateRequest> extract(String text) {
        List<AgentMemoryCandidateRequest> result = new ArrayList<>();
        if (isBlank(text) || memoryValidator.containsSensitive(text)) {
            return result;
        }
        String normalized = text.trim();
        if (containsAny(normalized, "以后活动文案都轻松一点", "文案轻松一点", "语气亲切一点", "不要太官方")) {
            result.add(candidate(AgentMemoryValidator.TYPE_PREFERENCE, "activity_style",
                    "商家偏好活动文案轻松、亲切，避免过于官方",
                    "从商家表达的活动文案偏好中提取"));
        }
        if (containsAny(normalized, "我不想折扣太大", "不要让利太多", "优惠力度别太大", "不要太亏")) {
            result.add(candidate(AgentMemoryValidator.TYPE_CONSTRAINT, "discount_limit",
                    "商家不希望折扣力度过大，需要控制让利幅度",
                    "从商家表达的折扣约束中提取"));
        }
        if (containsAny(normalized, "库存不要超过100", "秒杀库存不要太多", "库存控制在100以内", "库存别超过100张")) {
            result.add(candidate(AgentMemoryValidator.TYPE_CONSTRAINT, "stock_preference",
                    "商家倾向控制秒杀或活动库存，库存不宜过高，优先控制在100以内",
                    "从商家表达的库存约束中提取"));
        }
        if (containsAny(normalized, "周末活动优先", "以后优先做周末活动", "周末效果更好", "工作日少做活动")) {
            result.add(candidate(AgentMemoryValidator.TYPE_PREFERENCE, "campaign_time_preference",
                    "商家偏好优先设计周末活动",
                    "从商家表达的活动时间偏好中提取"));
        }
        if (containsAny(normalized, "更关注复购", "重点做老客")) {
            result.add(candidate(AgentMemoryValidator.TYPE_PREFERENCE, "campaign_goal_preference",
                    "商家更关注复购和老客运营",
                    "从商家表达的活动目标偏好中提取"));
        } else if (containsAny(normalized, "想拉新", "多吸引新客")) {
            result.add(candidate(AgentMemoryValidator.TYPE_PREFERENCE, "campaign_goal_preference",
                    "商家更关注拉新和新客转化",
                    "从商家表达的活动目标偏好中提取"));
        }
        return result;
    }

    private AgentMemoryCandidateRequest candidate(String type, String key, String value, String reason) {
        AgentMemoryCandidateRequest request = new AgentMemoryCandidateRequest();
        request.setCandidateType(type);
        request.setMemoryKey(key);
        request.setMemoryValue(value);
        request.setReason(reason);
        return request;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
