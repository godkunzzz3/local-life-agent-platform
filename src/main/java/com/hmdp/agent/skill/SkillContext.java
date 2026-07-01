package com.hmdp.agent.skill;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 执行上下文。
 */
@Data
@Accessors(chain = true)
public class SkillContext {

    private Long shopId;

    private Long userId;

    private String sessionId;

    private Long workflowRunId;

    private String traceId;

    private String userInput;

    private Map<String, Object> attributes = new LinkedHashMap<>();

    public static SkillContext create(Long shopId, Long userId, String userInput) {
        return new SkillContext()
                .setShopId(shopId)
                .setUserId(userId)
                .setUserInput(userInput);
    }

    public SkillContext putAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(key, value);
        return this;
    }
}
