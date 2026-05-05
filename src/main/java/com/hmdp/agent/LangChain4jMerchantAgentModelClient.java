package com.hmdp.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.AgentModelRequestDTO;
import com.hmdp.dto.AgentModelResponseDTO;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * LangChain4j 版商家运营模型客户端。
 *
 * <p>这个类只负责把标准 Agent 上下文转换成 Prompt，并调用 DashScope 的 QwenChatModel。
 * 它不直接查库、不直接写优惠券，所有真实业务动作仍然由 Facade 和工具层控制。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "merchant-agent.model", name = "provider", havingValue = "langchain4j")
public class LangChain4jMerchantAgentModelClient implements MerchantAgentModelClient {

    @Value("${merchant-agent.model.api-key:}")
    private String apiKey;

    @Value("${merchant-agent.model.base-url:}")
    private String baseUrl;

    @Value("${merchant-agent.model.model-name:qwen-turbo}")
    private String modelName;

    @Value("${merchant-agent.model.temperature:0.3}")
    private Float temperature;

    @Value("${merchant-agent.model.max-tokens:800}")
    private Integer maxTokens;

    @Resource
    private ObjectMapper objectMapper;

    private final RuleBasedMerchantAgentModelClient fallbackClient = new RuleBasedMerchantAgentModelClient();

    @Override
    public AgentModelResponseDTO generateReply(AgentModelRequestDTO request) {
        if (isBlank(apiKey)) {
            AgentModelResponseDTO fallback = fallbackClient.generateReply(request);
            fallback.setProvider("rule_fallback")
                    .setModelName("rule-based-merchant-agent-v1")
                    .setReasoning("未配置 DASHSCOPE_API_KEY，已自动使用规则版回复。");
            return fallback;
        }

        try {
            ChatModel chatModel = buildChatModel();
            String prompt = buildPrompt(request);
            String reply = chatModel.chat(prompt);
            return new AgentModelResponseDTO()
                    .setReply(reply)
                    .setProvider("langchain4j")
                    .setModelName(modelName)
                    .setReasoning("LangChain4j 调用 DashScope QwenChatModel，根据 PromptContext 和 ToolExecution 生成回复。")
                    .setRecommendedAction(request.getRecommendation() == null ? null : request.getRecommendation().getAction())
                    .setConfidence(82);
        } catch (Throwable e) {
            // 模型 SDK 可能抛出 NoClassDefFoundError 等 Error，兜底层要保证业务接口不被外部模型拖垮。
            log.warn("调用 LangChain4j 千问模型失败，降级为规则版回复：{} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
            AgentModelResponseDTO fallback = fallbackClient.generateReply(request);
            fallback.setProvider("rule_fallback")
                    .setReasoning("LangChain4j 调用失败，已降级规则版回复：" + e.getMessage());
            return fallback;
        }
    }

    private ChatModel buildChatModel() {
        QwenChatModel.QwenChatModelBuilder builder = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens);
        if (!isBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private String buildPrompt(AgentModelRequestDTO request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(request.getPromptContext().getSystemPrompt()).append("\n\n");
        prompt.append("【商家问题】\n")
                .append(request.getPromptContext().getUserMessage()).append("\n\n");
        prompt.append("【业务意图】\n")
                .append(request.getPromptContext().getIntentName())
                .append("，工具：")
                .append(request.getPromptContext().getSelectedToolName()).append("\n\n");
        prompt.append("【工具结果】\n")
                .append(toJson(request.getToolExecution().getData())).append("\n\n");
        prompt.append("【推荐动作】\n")
                .append(request.getRecommendation() == null ? "暂无" : toJson(request.getRecommendation())).append("\n\n");
        prompt.append("【约束】\n");
        for (String constraint : request.getPromptContext().getConstraints()) {
            prompt.append("- ").append(constraint).append("\n");
        }
        prompt.append("\n【输出要求】\n");
        prompt.append("请用中文回复商家，语气专业、简洁，必须包含关键数据、判断结论和下一步动作。");
        prompt.append("不要编造工具结果中不存在的数据；涉及创建真实优惠券时，只能说明需要商家确认草稿。");
        return prompt.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
