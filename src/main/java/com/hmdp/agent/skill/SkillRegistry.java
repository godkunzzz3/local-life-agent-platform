package com.hmdp.agent.skill;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Skill 注册表。
 *
 * <p>只注册 Spring Bean 中声明的 Skill，不扫描非 Bean，也不影响现有 AgentToolRegistry。</p>
 */
@Component
public class SkillRegistry {

    private final Map<String, AgentSkill<?, ?>> skillMap;

    public SkillRegistry(List<AgentSkill<?, ?>> skills) {
        Map<String, AgentSkill<?, ?>> registry = new LinkedHashMap<>();
        if (skills != null) {
            for (AgentSkill<?, ?> skill : skills) {
                if (skill == null) {
                    continue;
                }
                String skillName = skill.name();
                if (skillName == null || skillName.trim().isEmpty()) {
                    throw new IllegalStateException("Agent Skill name must not be blank");
                }
                if (registry.containsKey(skillName)) {
                    throw new IllegalStateException("Duplicate Agent Skill name: " + skillName);
                }
                registry.put(skillName, skill);
            }
        }
        this.skillMap = Collections.unmodifiableMap(registry);
    }

    public AgentSkill<?, ?> getSkill(String skillName) {
        if (skillName == null) {
            return null;
        }
        return skillMap.get(skillName);
    }

    public List<SkillDefinition> listDefinitions() {
        List<SkillDefinition> definitions = new ArrayList<>();
        for (AgentSkill<?, ?> skill : skillMap.values()) {
            definitions.add(skill.definition());
        }
        return definitions;
    }
}
