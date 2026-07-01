package com.hmdp.agent.skill.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.agent.skill.AgentSkill;
import com.hmdp.agent.skill.SkillContext;
import com.hmdp.agent.skill.SkillDefinition;
import com.hmdp.agent.skill.SkillResult;
import com.hmdp.agent.skill.SkillRiskLevel;
import com.hmdp.agent.skill.dto.MerchantDiagnosisSkillInput;
import com.hmdp.agent.skill.dto.MerchantDiagnosisSkillOutput;
import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.dto.AgentToolExecutionRequestDTO;
import com.hmdp.dto.AgentToolExecutionResultDTO;
import com.hmdp.tool.AgentToolExecutor;
import com.hmdp.tool.AgentToolRegistry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家运营诊断 Skill。
 *
 * <p>该 Skill 编排多个只读原子 Tool，不调用 operation_diagnosis_tool，也不触发写库或草稿确认流程。</p>
 */
@Component
public class MerchantDiagnosisSkill implements AgentSkill<MerchantDiagnosisSkillInput, MerchantDiagnosisSkillOutput> {

    public static final String SKILL_NAME = "merchant_diagnosis_skill";

    private static final String SHOP_PROFILE_TOOL = "shop_profile_tool";
    private static final String ORDER_ANALYSIS_TOOL = "order_analysis_tool";
    private static final String VOUCHER_ANALYSIS_TOOL = "voucher_analysis_tool";
    private static final String REVIEW_CONTENT_TOOL = "review_content_tool";

    private static final List<String> ALLOWED_TOOLS = Collections.unmodifiableList(Arrays.asList(
            SHOP_PROFILE_TOOL,
            ORDER_ANALYSIS_TOOL,
            VOUCHER_ANALYSIS_TOOL,
            REVIEW_CONTENT_TOOL
    ));

    private final AgentToolExecutor agentToolExecutor;
    private final AgentToolRegistry agentToolRegistry;
    private final ObjectMapper objectMapper;

    public MerchantDiagnosisSkill(AgentToolExecutor agentToolExecutor,
                                  AgentToolRegistry agentToolRegistry,
                                  ObjectMapper objectMapper) {
        this.agentToolExecutor = agentToolExecutor;
        this.agentToolRegistry = agentToolRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return SKILL_NAME;
    }

    @Override
    public SkillDefinition definition() {
        return new SkillDefinition()
                .setSkillName(SKILL_NAME)
                .setDisplayName("商家运营诊断 Skill")
                .setDescription("编排商家画像、订单统计、优惠券分析和评价摘要等只读工具，生成结构化商家运营诊断结果。")
                .setVersion("v1")
                .setAllowedTools(new ArrayList<>(ALLOWED_TOOLS))
                .setRiskLevel(SkillRiskLevel.LOW)
                .setNeedHumanConfirm(false)
                .setModelCallable(false);
    }

    @Override
    public Class<MerchantDiagnosisSkillInput> inputType() {
        return MerchantDiagnosisSkillInput.class;
    }

    @Override
    public SkillResult<MerchantDiagnosisSkillOutput> execute(MerchantDiagnosisSkillInput input, SkillContext context) {
        if (input == null || input.getShopId() == null) {
            return SkillResult.failure("INVALID_SHOP_ID", "shopId不能为空");
        }
        SkillResult<Void> boundaryCheck = validateReadonlyToolBoundary();
        if (!boundaryCheck.isSuccess()) {
            return SkillResult.failure(boundaryCheck.getErrorCode(), boundaryCheck.getErrorMessage());
        }

        String timeRange = normalizeTimeRange(input.getTimeRange());
        AgentToolExecutionRequestDTO request = buildToolRequest(input.getShopId(), timeRange);
        MerchantDiagnosisSkillOutput output = new MerchantDiagnosisSkillOutput()
                .setShopId(input.getShopId())
                .setTimeRange(timeRange);
        List<String> usedTools = new ArrayList<>();
        Map<String, String> partialFailures = new LinkedHashMap<>();

        AgentToolExecutionResultDTO shopProfile = executeTool(SHOP_PROFILE_TOOL, request);
        if (!Boolean.TRUE.equals(shopProfile.getSuccess())) {
            return buildFailure("SHOP_PROFILE_FAILED", shopProfile, usedTools);
        }
        output.setShopProfile(toMap(shopProfile.getData()));
        usedTools.add(SHOP_PROFILE_TOOL);

        AgentToolExecutionResultDTO orderStats = executeTool(ORDER_ANALYSIS_TOOL, request);
        if (!Boolean.TRUE.equals(orderStats.getSuccess())) {
            return buildFailure("ORDER_STATS_FAILED", orderStats, usedTools);
        }
        output.setOrderStats(toMap(orderStats.getData()));
        usedTools.add(ORDER_ANALYSIS_TOOL);

        AgentToolExecutionResultDTO voucherStats = executeTool(VOUCHER_ANALYSIS_TOOL, request);
        if (Boolean.TRUE.equals(voucherStats.getSuccess())) {
            output.setVoucherStats(toMap(voucherStats.getData()));
            usedTools.add(VOUCHER_ANALYSIS_TOOL);
        } else {
            output.setVoucherStats(Collections.<String, Object>emptyMap());
            partialFailures.put(VOUCHER_ANALYSIS_TOOL, safeError(voucherStats));
        }

        AgentToolExecutionResultDTO reviewSummary = executeTool(REVIEW_CONTENT_TOOL, request);
        if (Boolean.TRUE.equals(reviewSummary.getSuccess())) {
            output.setReviewSummary(toMap(reviewSummary.getData()));
            usedTools.add(REVIEW_CONTENT_TOOL);
        } else {
            output.setReviewSummary(Collections.<String, Object>emptyMap());
            partialFailures.put(REVIEW_CONTENT_TOOL, safeError(reviewSummary));
        }

        output.setUsedTools(new ArrayList<>(usedTools));
        fillConservativeDiagnosis(output, partialFailures);

        SkillResult<MerchantDiagnosisSkillOutput> result = SkillResult.success(output)
                .setRiskLevel(SkillRiskLevel.LOW)
                .setNeedHumanConfirm(false)
                .setConfidence(partialFailures.isEmpty() ? 1.0D : 0.8D)
                .putMetadata("skillName", SKILL_NAME)
                .putMetadata("timeRange", timeRange)
                .putMetadata("traceId", context == null ? null : context.getTraceId())
                .putMetadata("partialFailure", !partialFailures.isEmpty());
        if (!partialFailures.isEmpty()) {
            result.putMetadata("partialFailures", partialFailures);
        }
        for (String usedTool : usedTools) {
            result.addUsedTool(usedTool);
        }
        return result;
    }

    private SkillResult<Void> validateReadonlyToolBoundary() {
        List<AgentToolDefinitionDTO> definitions = agentToolRegistry.listDefinitions();
        for (String allowedTool : ALLOWED_TOOLS) {
            AgentToolDefinitionDTO definition = findDefinition(definitions, allowedTool);
            if (definition == null) {
                return SkillResult.failure("TOOL_DEFINITION_MISSING", "工具定义不存在：" + allowedTool);
            }
            if (!"readonly".equals(definition.getToolType())
                    || !"read".equals(definition.getAccessLevel())
                    || Boolean.TRUE.equals(definition.getWriteDatabase())
                    || Boolean.TRUE.equals(definition.getRequireMerchantConfirm())) {
                return SkillResult.failure("TOOL_BOUNDARY_INVALID", "Skill 只能编排只读工具：" + allowedTool);
            }
        }
        return SkillResult.success(null);
    }

    private AgentToolDefinitionDTO findDefinition(List<AgentToolDefinitionDTO> definitions, String toolName) {
        if (definitions == null) {
            return null;
        }
        for (AgentToolDefinitionDTO definition : definitions) {
            if (definition != null && toolName.equals(definition.getName())) {
                return definition;
            }
        }
        return null;
    }

    private AgentToolExecutionRequestDTO buildToolRequest(Long shopId, String timeRange) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(resolveDays(timeRange));
        return new AgentToolExecutionRequestDTO()
                .setShopId(shopId)
                .setIntent("operation_chat")
                .setDateRange(timeRange)
                .setStartTime(startTime)
                .setEndTime(endTime);
    }

    private AgentToolExecutionResultDTO executeTool(String toolName, AgentToolExecutionRequestDTO request) {
        try {
            return agentToolExecutor.executeReadonlyTool(toolName, request);
        } catch (Exception e) {
            return new AgentToolExecutionResultDTO()
                    .setToolName(toolName)
                    .setSuccess(false)
                    .setErrorMsg(e.getMessage());
        }
    }

    private SkillResult<MerchantDiagnosisSkillOutput> buildFailure(String errorCode,
                                                                   AgentToolExecutionResultDTO failedTool,
                                                                   List<String> usedTools) {
        SkillResult<MerchantDiagnosisSkillOutput> result = SkillResult.<MerchantDiagnosisSkillOutput>failure(errorCode, safeError(failedTool))
                .setRiskLevel(SkillRiskLevel.LOW)
                .setNeedHumanConfirm(false)
                .putMetadata("skillName", SKILL_NAME)
                .putMetadata("failedTool", failedTool == null ? null : failedTool.getToolName());
        for (String usedTool : usedTools) {
            result.addUsedTool(usedTool);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object data) {
        if (data == null) {
            return new LinkedHashMap<>();
        }
        if (data instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) data);
        }
        return objectMapper.convertValue(data, Map.class);
    }

    private void fillConservativeDiagnosis(MerchantDiagnosisSkillOutput output, Map<String, String> partialFailures) {
        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("已完成店铺画像和订单统计读取，可用于后续运营诊断。");
        if (!output.getVoucherStats().isEmpty()) {
            keyFindings.add("已读取优惠券结构，可结合订单表现判断活动供给是否充足。");
        }
        if (!output.getReviewSummary().isEmpty()) {
            keyFindings.add("已读取评价与探店内容摘要，可结合互动数据判断内容侧表现。");
        }
        if (!partialFailures.isEmpty()) {
            keyFindings.add("部分非核心数据读取失败，本次诊断结果应按已成功返回的数据保守解读。");
        }

        List<String> possibleReasons = new ArrayList<>();
        possibleReasons.add("第一版 Skill 不调用大模型，不编造原因；需结合返回的订单、优惠券和评价字段进一步判断。");
        if (!partialFailures.isEmpty()) {
            possibleReasons.add("非核心工具失败可能导致优惠券或评价维度信息不完整。");
        }

        List<String> suggestions = new ArrayList<>();
        suggestions.add("优先核对订单转化、优惠券结构和评价互动三个维度，再决定是否进入活动草稿环节。");
        suggestions.add("如需生成活动方案，应进入独立草稿 Skill，并继续走商家确认流程。");

        output.setKeyFindings(keyFindings)
                .setPossibleReasons(possibleReasons)
                .setSuggestions(suggestions);
    }

    private String normalizeTimeRange(String timeRange) {
        if (timeRange == null || timeRange.trim().isEmpty()) {
            return "last_7_days";
        }
        return timeRange.trim();
    }

    private long resolveDays(String timeRange) {
        if ("last_30_days".equalsIgnoreCase(timeRange) || "LAST_30_DAYS".equalsIgnoreCase(timeRange)) {
            return 30L;
        }
        if ("today".equalsIgnoreCase(timeRange) || "TODAY".equalsIgnoreCase(timeRange)) {
            return 1L;
        }
        return 7L;
    }

    private String safeError(AgentToolExecutionResultDTO result) {
        if (result == null || result.getErrorMsg() == null || result.getErrorMsg().trim().isEmpty()) {
            return "工具执行失败";
        }
        return result.getErrorMsg();
    }
}
