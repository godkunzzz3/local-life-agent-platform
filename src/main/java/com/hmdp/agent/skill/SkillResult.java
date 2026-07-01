package com.hmdp.agent.skill;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 执行结果。
 */
@Data
@Accessors(chain = true)
public class SkillResult<O> {

    private boolean success;

    private O output;

    private List<String> usedTools = new ArrayList<>();

    private SkillRiskLevel riskLevel = SkillRiskLevel.LOW;

    private boolean needHumanConfirm;

    private Double confidence;

    private String errorCode;

    private String errorMessage;

    private Map<String, Object> metadata = new LinkedHashMap<>();

    public static <O> SkillResult<O> success(O output) {
        return new SkillResult<O>()
                .setSuccess(true)
                .setOutput(output);
    }

    public static <O> SkillResult<O> failure(String errorCode, String errorMessage) {
        return new SkillResult<O>()
                .setSuccess(false)
                .setErrorCode(errorCode)
                .setErrorMessage(errorMessage);
    }

    public SkillResult<O> addUsedTool(String toolName) {
        if (usedTools == null) {
            usedTools = new ArrayList<>();
        }
        if (toolName != null && !toolName.trim().isEmpty()) {
            usedTools.add(toolName);
        }
        return this;
    }

    public SkillResult<O> putMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        metadata.put(key, value);
        return this;
    }
}
