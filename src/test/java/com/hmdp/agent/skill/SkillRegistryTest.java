package com.hmdp.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillRegistryTest {

    @Test
    void shouldRegisterSkillsAndListDefinitions() {
        SkillRegistry registry = new SkillRegistry(Arrays.asList(
                new FakeSkill("fake_diagnosis_skill"),
                new FakeSkill("fake_knowledge_skill")
        ));

        assertNotNull(registry.getSkill("fake_diagnosis_skill"));
        assertNotNull(registry.getSkill("fake_knowledge_skill"));

        List<SkillDefinition> definitions = registry.listDefinitions();
        assertEquals(2, definitions.size());
        assertEquals("fake_diagnosis_skill", definitions.get(0).getSkillName());
        assertEquals("fake_knowledge_skill", definitions.get(1).getSkillName());
    }

    @Test
    void shouldRejectDuplicateSkillName() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new SkillRegistry(Arrays.asList(
                        new FakeSkill("duplicate_skill"),
                        new FakeSkill("duplicate_skill")
                )));

        assertEquals("Duplicate Agent Skill name: duplicate_skill", exception.getMessage());
    }

    private static class FakeSkill implements AgentSkill<FakeSkillInput, String> {

        private final String name;

        private FakeSkill(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public SkillDefinition definition() {
            return new SkillDefinition()
                    .setSkillName(name)
                    .setDisplayName("Fake Skill")
                    .setVersion("v1")
                    .setAllowedTools(Collections.singletonList("fake_readonly_tool"));
        }

        @Override
        public Class<FakeSkillInput> inputType() {
            return FakeSkillInput.class;
        }

        @Override
        public SkillResult<String> execute(FakeSkillInput input, SkillContext context) {
            return SkillResult.success(input.getText());
        }
    }

    private static class FakeSkillInput {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
