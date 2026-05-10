package com.hmdp.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商家运营 Agent Prompt 模板服务。
 *
 * <p>Prompt 属于可持续迭代的运营策略，不适合长期硬编码在 Java 方法里。
 * 这里统一从 resources/prompt/merchant-agent 目录读取模板，后续要调整角色边界、
 * 输出格式或 Few-shot 示例时，只改 md 文件即可。</p>
 */
@Slf4j
@Component
public class MerchantAgentPromptTemplateService {

    private static final String PROMPT_DIR = "prompt/merchant-agent/";
    private static final String DEFAULT_OUTPUT_TEMPLATE = "operation-chat.md";

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Value("${merchant-agent.prompt.version:merchant-agent-v1}")
    private String promptVersion;

    /**
     * 当前 Prompt 版本。
     *
     * <p>模型效果排查时，版本号和 provider/modelName 一样重要：
     * 同一个模型在不同 Prompt 下可能生成完全不同的运营建议。</p>
     */
    public String promptVersion() {
        return promptVersion;
    }

    /**
     * 获取 Agent 主系统 Prompt。
     */
    public String systemPrompt() {
        return loadTemplate("system.md");
    }

    /**
     * 获取行为边界模板，例如不能泄露 API Key、不能直接创建真实活动。
     */
    public String behaviorBoundary() {
        return loadTemplate("behavior-boundary.md");
    }

    /**
     * 获取对话总框架模板。
     */
    public String chatFrame() {
        return loadTemplate("chat-frame.md");
    }

    /**
     * 根据业务意图选择输出要求模板。
     */
    public String outputRequirement(String intent) {
        if ("identity".equals(intent)) {
            return loadTemplate("identity.md");
        }
        if ("order_analysis".equals(intent)) {
            return loadTemplate("order-analysis.md");
        }
        if ("voucher_plan".equals(intent)) {
            return loadTemplate("voucher-plan.md");
        }
        if ("review_analysis".equals(intent)) {
            return loadTemplate("review-analysis.md");
        }
        return loadTemplate(DEFAULT_OUTPUT_TEMPLATE);
    }

    private String loadTemplate(String fileName) {
        return templateCache.computeIfAbsent(fileName, this::readTemplate);
    }

    private String readTemplate(String fileName) {
        ClassPathResource resource = new ClassPathResource(PROMPT_DIR + fileName);
        try (InputStream inputStream = resource.getInputStream()) {
            // Java 8 没有 InputStream#readAllBytes，这里使用 Spring 的 StreamUtils 读取 classpath 模板。
            byte[] bytes = StreamUtils.copyToByteArray(inputStream);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.warn("读取 Agent Prompt 模板失败：{}", fileName, e);
            return "";
        }
    }
}
