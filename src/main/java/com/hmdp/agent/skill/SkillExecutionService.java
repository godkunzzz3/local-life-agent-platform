package com.hmdp.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.service.AgentWorkflowRecorderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 执行服务。
 *
 * <p>负责 Skill 查找、输入转换、异常包装和可选 Workflow 旁路记录。</p>
 */
@Slf4j
@Service
public class SkillExecutionService {

    private static final String ERROR_SKILL_NOT_FOUND = "SKILL_NOT_FOUND";
    private static final String ERROR_SKILL_EXECUTION_FAILED = "SKILL_EXECUTION_FAILED";

    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;
    private final AgentWorkflowRecorderService workflowRecorderService;

    public SkillExecutionService(SkillRegistry skillRegistry,
                                 ObjectMapper objectMapper,
                                 @Autowired(required = false) AgentWorkflowRecorderService workflowRecorderService) {
        this.skillRegistry = skillRegistry;
        this.objectMapper = objectMapper;
        this.workflowRecorderService = workflowRecorderService;
    }

    public SkillResult<?> execute(String skillName, Object input, SkillContext context) {
        SkillContext safeContext = context == null ? new SkillContext() : context;
        AgentSkill<?, ?> skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            SkillResult<?> failure = SkillResult.failure(ERROR_SKILL_NOT_FOUND, "Skill not found: " + skillName);
            recordFinalStep(skillName, safeContext, failure, true);
            return failure;
        }

        recordStartStep(skill.name(), input, safeContext);
        try {
            SkillResult<?> result = invokeSkill(skill, input, safeContext);
            SkillResult<?> safeResult = result == null
                    ? SkillResult.failure(ERROR_SKILL_EXECUTION_FAILED, "Skill returned null result")
                    : result;
            normalizeResult(safeResult);
            recordFinalStep(skill.name(), safeContext, safeResult, !safeResult.isSuccess());
            return safeResult;
        } catch (Exception e) {
            log.warn("Agent Skill 执行失败，skillName={}", skill.name(), e);
            SkillResult<?> failure = SkillResult.failure(ERROR_SKILL_EXECUTION_FAILED, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            recordFinalStep(skill.name(), safeContext, failure, true);
            return failure;
        }
    }

    private <I, O> SkillResult<O> invokeSkill(AgentSkill<I, O> skill, Object input, SkillContext context) {
        I convertedInput = convertInput(input, skill.inputType());
        return skill.execute(convertedInput, context);
    }

    private <I> I convertInput(Object input, Class<I> inputType) {
        if (input == null) {
            return null;
        }
        if (inputType.isInstance(input)) {
            return inputType.cast(input);
        }
        return objectMapper.convertValue(input, inputType);
    }

    private void normalizeResult(SkillResult<?> result) {
        if (result.getUsedTools() == null) {
            result.setUsedTools(new java.util.ArrayList<String>());
        }
        if (result.getMetadata() == null) {
            result.setMetadata(new LinkedHashMap<String, Object>());
        }
        if (!result.isSuccess()) {
            if (result.getErrorCode() == null || result.getErrorCode().trim().isEmpty()) {
                result.setErrorCode(ERROR_SKILL_EXECUTION_FAILED);
            }
            if (result.getErrorMessage() == null || result.getErrorMessage().trim().isEmpty()) {
                result.setErrorMessage("Skill execution failed");
            }
        }
    }

    private void recordStartStep(String skillName, Object input, SkillContext context) {
        if (!shouldRecordWorkflow(context)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skillName", skillName);
        payload.put("traceId", context.getTraceId());
        payload.put("input", input);
        try {
            workflowRecorderService.recordStep(context.getWorkflowRunId(), parseSessionId(context.getSessionId()), context.getShopId(),
                    resolveStepOrder(context, 1), "SKILL_START", "Skill 开始执行", "SKILL",
                    skillName, "success", payload, null, "Skill execution started", null, null);
        } catch (Exception e) {
            log.warn("记录 Skill Workflow 开始步骤失败，skillName={}", skillName, e);
        }
    }

    private void recordFinalStep(String skillName, SkillContext context, SkillResult<?> result, boolean failed) {
        if (!shouldRecordWorkflow(context)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skillName", skillName);
        payload.put("success", result.isSuccess());
        if (result.isSuccess()) {
            payload.put("usedTools", result.getUsedTools());
            payload.put("riskLevel", result.getRiskLevel() == null ? null : result.getRiskLevel().name());
            payload.put("needHumanConfirm", result.isNeedHumanConfirm());
            payload.put("confidence", result.getConfidence());
            payload.put("traceId", context.getTraceId());
            payload.put("metadata", result.getMetadata());
        } else {
            payload.put("errorCode", result.getErrorCode());
            payload.put("errorMessage", result.getErrorMessage());
            payload.put("traceId", context.getTraceId());
        }
        try {
            workflowRecorderService.recordStep(context.getWorkflowRunId(), parseSessionId(context.getSessionId()), context.getShopId(),
                    resolveStepOrder(context, failed ? 3 : 2), failed ? "SKILL_FAILED" : "SKILL_FINAL",
                    failed ? "Skill 执行失败" : "Skill 执行完成", "SKILL", skillName,
                    failed ? "failed" : "success", null, payload, failed ? result.getErrorMessage() : "Skill execution finished",
                    failed ? result.getErrorMessage() : null, null);
        } catch (Exception e) {
            log.warn("记录 Skill Workflow 结束步骤失败，skillName={}", skillName, e);
        }
    }

    private boolean shouldRecordWorkflow(SkillContext context) {
        return workflowRecorderService != null && context != null && context.getWorkflowRunId() != null;
    }

    private Long parseSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(sessionId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer resolveStepOrder(SkillContext context, int defaultOrder) {
        if (context == null || context.getAttributes() == null) {
            return defaultOrder;
        }
        Object order = context.getAttributes().get("skillStepOrder");
        if (order instanceof Number) {
            return ((Number) order).intValue();
        }
        return defaultOrder;
    }
}
