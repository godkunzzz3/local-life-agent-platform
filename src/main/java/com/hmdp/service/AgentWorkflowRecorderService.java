package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentWorkflowRun;
import com.hmdp.entity.AgentWorkflowStep;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Agent Workflow 记录服务。
 *
 * <p>Workflow 属于旁路可观测性能力，任何记录失败都不能影响 Agent 主流程。</p>
 */
@Slf4j
@Service
public class AgentWorkflowRecorderService {

    public static final int RUN_STATUS_RUNNING = 1;
    public static final int RUN_STATUS_SUCCESS = 2;
    public static final int RUN_STATUS_FAILED = 3;

    public static final int STEP_STATUS_SUCCESS = 1;
    public static final int STEP_STATUS_FAILED = 2;
    public static final int STEP_STATUS_SKIPPED = 3;

    private static final int USER_MESSAGE_MAX_LENGTH = 512;
    private static final int SUMMARY_MAX_LENGTH = 512;
    private static final int ERROR_MAX_LENGTH = 512;
    private static final int DETAIL_MAX_LENGTH = 1024;
    private static final int JSON_MAX_LENGTH = 8192;

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern SENSITIVE_JSON_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:token|accessToken|refreshToken|authorization|apiKey|api_key|secret|password|code|verifyCode|captcha|phone|mobile)\"\\s*:\\s*\")([^\"]*)(\")");

    @Resource
    private IMerchantAgentWorkflowRunService workflowRunService;
    @Resource
    private IMerchantAgentWorkflowStepService workflowStepService;
    @Resource
    private IMerchantService merchantService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startRun(Long sessionId, Long shopId, Long merchantId, String scene,
                         String triggerType, String userMessage, String intent) {
        try {
            Long runId = redisIdWorker.nextId("agent");
            LocalDateTime now = LocalDateTime.now();
            AgentWorkflowRun run = new AgentWorkflowRun()
                    .setId(runId)
                    .setSessionId(sessionId)
                    .setShopId(shopId)
                    .setMerchantId(merchantId)
                    .setScene(truncate(scene, 64))
                    .setTriggerType(truncate(triggerType, 32))
                    .setUserMessage(sanitizeAndTruncate(userMessage, USER_MESSAGE_MAX_LENGTH))
                    .setIntent(truncate(intent, 64))
                    .setStatus(RUN_STATUS_RUNNING)
                    .setStartTime(now);
            workflowRunService.save(run);
            return runId;
        } catch (Exception e) {
            log.warn("记录 Agent Workflow Run 开始失败", e);
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStep(Long runId, Long sessionId, Long shopId, Integer stepOrder,
                           String stepCode, String stepName, String nodeType, String toolName,
                           String status, Object input, Object output, String detail,
                           String errorMsg, Long costMillis) {
        if (runId == null) {
            return;
        }
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = costMillis == null ? endTime : endTime.minus(Duration.ofMillis(Math.max(0L, costMillis)));
            AgentWorkflowStep step = new AgentWorkflowStep()
                    .setId(redisIdWorker.nextId("agent"))
                    .setRunId(runId)
                    .setSessionId(sessionId)
                    .setShopId(shopId)
                    .setStepOrder(stepOrder == null ? 1 : stepOrder)
                    .setStepCode(truncate(stepCode, 64))
                    .setStepName(truncate(stepName, 128))
                    .setNodeType(truncate(nodeType, 64))
                    .setToolName(truncate(toolName, 64))
                    .setStatus(resolveStepStatus(status))
                    .setInputJson(toSafeJson(input))
                    .setOutputJson(toSafeJson(output))
                    .setDetail(sanitizeAndTruncate(detail, DETAIL_MAX_LENGTH))
                    .setErrorMsg(sanitizeAndTruncate(errorMsg, ERROR_MAX_LENGTH))
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setCostMillis(costMillis);
            workflowStepService.save(step);
        } catch (Exception e) {
            log.warn("记录 Agent Workflow Step 失败，runId={}, stepCode={}", runId, stepCode, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRun(Long runId, String summary) {
        if (runId == null) {
            return;
        }
        try {
            AgentWorkflowRun oldRun = workflowRunService.getById(runId);
            LocalDateTime endTime = LocalDateTime.now();
            Long costMillis = null;
            if (oldRun != null && oldRun.getStartTime() != null) {
                costMillis = Duration.between(oldRun.getStartTime(), endTime).toMillis();
            }
            workflowRunService.updateById(new AgentWorkflowRun()
                    .setId(runId)
                    .setStatus(RUN_STATUS_SUCCESS)
                    .setEndTime(endTime)
                    .setCostMillis(costMillis)
                    .setSummary(sanitizeAndTruncate(summary, SUMMARY_MAX_LENGTH)));
        } catch (Exception e) {
            log.warn("标记 Agent Workflow Run 成功失败，runId={}", runId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(Long runId, String errorMsg) {
        if (runId == null) {
            return;
        }
        try {
            AgentWorkflowRun oldRun = workflowRunService.getById(runId);
            LocalDateTime endTime = LocalDateTime.now();
            Long costMillis = null;
            if (oldRun != null && oldRun.getStartTime() != null) {
                costMillis = Duration.between(oldRun.getStartTime(), endTime).toMillis();
            }
            workflowRunService.updateById(new AgentWorkflowRun()
                    .setId(runId)
                    .setStatus(RUN_STATUS_FAILED)
                    .setEndTime(endTime)
                    .setCostMillis(costMillis)
                    .setErrorMsg(sanitizeAndTruncate(errorMsg, ERROR_MAX_LENGTH)));
        } catch (Exception e) {
            log.warn("标记 Agent Workflow Run 失败失败，runId={}", runId, e);
        }
    }

    public Result queryRuns(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权查看该店铺 Workflow");
        }
        List<AgentWorkflowRun> runs = workflowRunService.list(new QueryWrapper<AgentWorkflowRun>()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .last("LIMIT 50"));
        return Result.ok(toRunRows(runs));
    }

    public Result querySteps(Long runId) {
        if (runId == null) {
            return Result.fail("Workflow Run id不能为空");
        }
        AgentWorkflowRun run = workflowRunService.getById(runId);
        if (run == null) {
            return Result.fail("Workflow Run 不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(run.getShopId())) {
            return Result.fail("无权查看该 Workflow");
        }
        List<AgentWorkflowStep> steps = workflowStepService.list(new QueryWrapper<AgentWorkflowStep>()
                .eq("run_id", runId)
                .orderByAsc("step_order"));
        return Result.ok(toStepRows(steps));
    }

    private List<Map<String, Object>> toRunRows(List<AgentWorkflowRun> runs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (runs == null) {
            return rows;
        }
        for (AgentWorkflowRun run : runs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", String.valueOf(run.getId()));
            row.put("sessionId", run.getSessionId() == null ? null : String.valueOf(run.getSessionId()));
            row.put("shopId", run.getShopId());
            row.put("merchantId", run.getMerchantId() == null ? null : String.valueOf(run.getMerchantId()));
            row.put("scene", run.getScene());
            row.put("triggerType", run.getTriggerType());
            row.put("userMessage", run.getUserMessage());
            row.put("intent", run.getIntent());
            row.put("status", run.getStatus());
            row.put("statusName", resolveRunStatusName(run.getStatus()));
            row.put("startTime", run.getStartTime());
            row.put("endTime", run.getEndTime());
            row.put("costMillis", run.getCostMillis());
            row.put("errorMsg", run.getErrorMsg());
            row.put("summary", run.getSummary());
            row.put("createTime", run.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> toStepRows(List<AgentWorkflowStep> steps) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (steps == null) {
            return rows;
        }
        for (AgentWorkflowStep step : steps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stepId", String.valueOf(step.getId()));
            row.put("runId", String.valueOf(step.getRunId()));
            row.put("sessionId", step.getSessionId() == null ? null : String.valueOf(step.getSessionId()));
            row.put("shopId", step.getShopId());
            row.put("stepOrder", step.getStepOrder());
            row.put("stepCode", step.getStepCode());
            row.put("stepName", step.getStepName());
            row.put("nodeType", step.getNodeType());
            row.put("toolName", step.getToolName());
            row.put("status", step.getStatus());
            row.put("statusName", resolveStepStatusName(step.getStatus()));
            row.put("inputJson", step.getInputJson());
            row.put("outputJson", step.getOutputJson());
            row.put("detail", step.getDetail());
            row.put("errorMsg", step.getErrorMsg());
            row.put("startTime", step.getStartTime());
            row.put("endTime", step.getEndTime());
            row.put("costMillis", step.getCostMillis());
            row.put("createTime", step.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    private Integer resolveStepStatus(String status) {
        if ("failed".equalsIgnoreCase(status) || "fail".equalsIgnoreCase(status)) {
            return STEP_STATUS_FAILED;
        }
        if ("skipped".equalsIgnoreCase(status) || "skip".equalsIgnoreCase(status)) {
            return STEP_STATUS_SKIPPED;
        }
        return STEP_STATUS_SUCCESS;
    }

    private String resolveRunStatusName(Integer status) {
        if (status != null && status == RUN_STATUS_SUCCESS) {
            return "成功";
        }
        if (status != null && status == RUN_STATUS_FAILED) {
            return "失败";
        }
        return "运行中";
    }

    private String resolveStepStatusName(Integer status) {
        if (status != null && status == STEP_STATUS_FAILED) {
            return "失败";
        }
        if (status != null && status == STEP_STATUS_SKIPPED) {
            return "跳过";
        }
        return "成功";
    }

    private String toSafeJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return sanitizeAndTruncate(objectMapper.writeValueAsString(data), JSON_MAX_LENGTH);
        } catch (JsonProcessingException e) {
            return sanitizeAndTruncate(String.valueOf(data), JSON_MAX_LENGTH);
        }
    }

    private String sanitizeAndTruncate(String value, int maxLength) {
        return truncate(sanitize(value), maxLength);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String result = SENSITIVE_JSON_FIELD_PATTERN.matcher(value).replaceAll("$1***$3");
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
