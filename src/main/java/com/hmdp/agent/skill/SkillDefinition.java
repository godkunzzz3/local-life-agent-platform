package com.hmdp.agent.skill;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 定义元数据。
 *
 * <p>Skill 是面向业务目标的编排层，allowedTools 用于声明它可编排的原子 Tool 边界。</p>
 */
@Data
@Accessors(chain = true)
public class SkillDefinition {

    private String skillName;

    private String displayName;

    private String description;

    private String version;

    private List<String> allowedTools = new ArrayList<>();

    private SkillRiskLevel riskLevel = SkillRiskLevel.LOW;

    private Boolean needHumanConfirm = false;

    /**
     * 第一版 Skill 不直接暴露给模型自由调用。
     */
    private Boolean modelCallable = false;
}
