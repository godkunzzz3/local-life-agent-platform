package com.hmdp.service;

import com.hmdp.dto.AgentMemoryRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Agent Memory 安全校验器。
 *
 * <p>长期记忆会进入后续 Prompt，新增、编辑和候选确认都必须共用这套边界。</p>
 */
@Component
public class AgentMemoryValidator {

    public static final int MEMORY_KEY_MAX_LENGTH = 128;
    public static final int MEMORY_VALUE_MAX_LENGTH = 512;

    public static final String TYPE_PREFERENCE = "PREFERENCE";
    public static final String TYPE_CONSTRAINT = "CONSTRAINT";

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(验证码|token|accessToken|refreshToken|authorization|apiKey|api_key|password|secret|code|verifyCode|captcha|phone|mobile)");

    public String validate(AgentMemoryRequest request) {
        if (request == null) {
            return "Memory 请求不能为空";
        }
        String type = normalizeMemoryType(request.getMemoryType());
        if (!TYPE_PREFERENCE.equals(type) && !TYPE_CONSTRAINT.equals(type)) {
            return "第一版 Memory 只支持 PREFERENCE / CONSTRAINT";
        }
        if (isBlank(request.getMemoryKey())) {
            return "memoryKey不能为空";
        }
        if (request.getMemoryKey().trim().length() > MEMORY_KEY_MAX_LENGTH) {
            return "memoryKey长度不能超过128";
        }
        if (isBlank(request.getMemoryValue())) {
            return "memoryValue不能为空";
        }
        if (request.getMemoryValue().trim().length() > MEMORY_VALUE_MAX_LENGTH) {
            return "memoryValue长度不能超过512";
        }
        if (containsSensitive(request.getMemoryKey()) || containsSensitive(request.getMemoryValue())) {
            return "Memory 不能包含手机号、验证码、token、apiKey、password、secret 等敏感信息";
        }
        return null;
    }

    public boolean containsSensitive(String value) {
        if (value == null) {
            return false;
        }
        return PHONE_PATTERN.matcher(value).find() || SENSITIVE_PATTERN.matcher(value).find();
    }

    public String normalizeMemoryType(String value) {
        return isBlank(value) ? TYPE_PREFERENCE : value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
