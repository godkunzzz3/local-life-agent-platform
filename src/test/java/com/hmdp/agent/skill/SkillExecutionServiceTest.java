package com.hmdp.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.service.AgentWorkflowRecorderService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillExecutionServiceTest {

    @Test
    void shouldExecuteSkillSuccessfully() {
        SkillExecutionService executionService = new SkillExecutionService(
                new SkillRegistry(Collections.singletonList(new EchoSkill())),
                new ObjectMapper(),
                null
        );

        FakeSkillInput input = new FakeSkillInput();
        input.setText("hello");
        input.setCount(2);

        SkillResult<?> result = executionService.execute("echo_skill", input, SkillContext.create(1L, 2L, "hello"));

        assertTrue(result.isSuccess());
        assertEquals("hello:2", result.getOutput());
        assertNotNull(result.getUsedTools());
        assertNotNull(result.getMetadata());
        assertEquals(1, result.getUsedTools().size());
        assertEquals("fake_readonly_tool", result.getUsedTools().get(0));
    }

    @Test
    void shouldWrapSkillExceptionAsFailureResult() {
        SkillExecutionService executionService = new SkillExecutionService(
                new SkillRegistry(Collections.singletonList(new FailingSkill())),
                new ObjectMapper(),
                null
        );

        SkillResult<?> result = executionService.execute("failing_skill", new FakeSkillInput(), new SkillContext());

        assertFalse(result.isSuccess());
        assertEquals("SKILL_EXECUTION_FAILED", result.getErrorCode());
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getUsedTools());
        assertNotNull(result.getMetadata());
    }

    @Test
    void shouldConvertMapInputToSkillInputType() {
        SkillExecutionService executionService = new SkillExecutionService(
                new SkillRegistry(Collections.singletonList(new EchoSkill())),
                new ObjectMapper(),
                null
        );
        Map<String, Object> input = new HashMap<>();
        input.put("text", "converted");
        input.put("count", 3);

        SkillResult<?> result = executionService.execute("echo_skill", input, new SkillContext());

        assertTrue(result.isSuccess());
        assertEquals("converted:3", result.getOutput());
    }

    @Test
    void shouldReturnFailureWhenSkillNotFound() {
        SkillExecutionService executionService = new SkillExecutionService(
                new SkillRegistry(Collections.<AgentSkill<?, ?>>emptyList()),
                new ObjectMapper(),
                null
        );

        SkillResult<?> result = executionService.execute("missing_skill", new FakeSkillInput(), new SkillContext());

        assertFalse(result.isSuccess());
        assertEquals("SKILL_NOT_FOUND", result.getErrorCode());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void shouldRecordWorkflowStepsWhenRunIdExists() {
        CapturingWorkflowRecorder recorder = new CapturingWorkflowRecorder();
        SkillExecutionService executionService = new SkillExecutionService(
                new SkillRegistry(Collections.singletonList(new EchoSkill())),
                new ObjectMapper(),
                recorder
        );
        SkillContext context = new SkillContext()
                .setShopId(10143L)
                .setSessionId("123")
                .setWorkflowRunId(456L)
                .setTraceId("trace-1");

        SkillResult<?> result = executionService.execute("echo_skill", new FakeSkillInput("hi", 1), context);

        assertTrue(result.isSuccess());
        assertEquals(2, recorder.recordCount);
        assertEquals("SKILL_FINAL", recorder.lastStepCode);
        assertEquals("echo_skill", recorder.lastToolName);
    }

    private static class EchoSkill implements AgentSkill<FakeSkillInput, String> {

        @Override
        public String name() {
            return "echo_skill";
        }

        @Override
        public SkillDefinition definition() {
            return new SkillDefinition()
                    .setSkillName(name())
                    .setDisplayName("Echo Skill")
                    .setDescription("Fake skill for tests")
                    .setVersion("v1")
                    .setAllowedTools(Collections.singletonList("fake_readonly_tool"))
                    .setRiskLevel(SkillRiskLevel.LOW);
        }

        @Override
        public Class<FakeSkillInput> inputType() {
            return FakeSkillInput.class;
        }

        @Override
        public SkillResult<String> execute(FakeSkillInput input, SkillContext context) {
            String text = input == null ? "" : input.getText();
            Integer count = input == null ? null : input.getCount();
            return SkillResult.success(text + ":" + count)
                    .addUsedTool("fake_readonly_tool")
                    .putMetadata("source", "unit-test");
        }
    }

    private static class FailingSkill implements AgentSkill<FakeSkillInput, String> {

        @Override
        public String name() {
            return "failing_skill";
        }

        @Override
        public SkillDefinition definition() {
            return new SkillDefinition().setSkillName(name());
        }

        @Override
        public Class<FakeSkillInput> inputType() {
            return FakeSkillInput.class;
        }

        @Override
        public SkillResult<String> execute(FakeSkillInput input, SkillContext context) {
            throw new RuntimeException("boom");
        }
    }

    private static class FakeSkillInput {
        private String text;
        private Integer count;

        private FakeSkillInput() {
        }

        private FakeSkillInput(String text, Integer count) {
            this.text = text;
            this.count = count;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }

    private static class CapturingWorkflowRecorder extends AgentWorkflowRecorderService {
        private int recordCount;
        private String lastStepCode;
        private String lastToolName;

        @Override
        public void recordStep(Long runId, Long sessionId, Long shopId, Integer stepOrder,
                               String stepCode, String stepName, String nodeType, String toolName,
                               String status, Object input, Object output, String detail,
                               String errorMsg, Long costMillis) {
            recordCount++;
            lastStepCode = stepCode;
            lastToolName = toolName;
        }
    }
}
