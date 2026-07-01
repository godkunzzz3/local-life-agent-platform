package com.hmdp.agent.skill;

/**
 * Skill 元数据层面的风险等级。
 *
 * <p>该枚举只描述 Skill 的默认风险，不替代 MerchantAgentRulePolicyService 中的线上风险判断。</p>
 */
public enum SkillRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
