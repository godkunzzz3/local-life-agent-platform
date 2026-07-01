package com.hmdp.agent.skill;

/**
 * Agent Skill 统一接口。
 *
 * <p>Tool 是原子业务能力，Skill 是面向业务目标的 Tool / RAG / 规则校验 / 输出模板 /
 * HITL 策略编排。Skill 不应绕过 Tool Registry 或 Human-in-the-loop 安全边界。</p>
 */
public interface AgentSkill<I, O> {

    String name();

    SkillDefinition definition();

    Class<I> inputType();

    SkillResult<O> execute(I input, SkillContext context);
}
