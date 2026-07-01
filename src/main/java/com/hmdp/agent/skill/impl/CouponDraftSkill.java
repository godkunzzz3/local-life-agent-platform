package com.hmdp.agent.skill.impl;

import com.hmdp.agent.MerchantAgentRulePolicyService;
import com.hmdp.agent.skill.AgentSkill;
import com.hmdp.agent.skill.SkillContext;
import com.hmdp.agent.skill.SkillDefinition;
import com.hmdp.agent.skill.SkillResult;
import com.hmdp.agent.skill.SkillRiskLevel;
import com.hmdp.agent.skill.dto.CouponDraftSkillInput;
import com.hmdp.agent.skill.dto.CouponDraftSkillOutput;
import com.hmdp.dto.AgentToolExecutionRequestDTO;
import com.hmdp.dto.AgentToolExecutionResultDTO;
import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.MerchantCampaignDraftSkillResultDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.MerchantCampaignDraftSkillService;
import com.hmdp.tool.AgentToolExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券策略草稿 Skill。
 *
 * <p>该 Skill 只生成待确认活动草稿，真实优惠券创建仍必须走商家确认流程。</p>
 */
@Component
public class CouponDraftSkill implements AgentSkill<CouponDraftSkillInput, CouponDraftSkillOutput> {

    public static final String SKILL_NAME = "coupon_draft_skill";

    private static final String ORDER_ANALYSIS_TOOL = "order_analysis_tool";
    private static final String VOUCHER_ANALYSIS_TOOL = "voucher_analysis_tool";
    private static final List<String> ALLOWED_TOOLS = Collections.unmodifiableList(Arrays.asList(
            ORDER_ANALYSIS_TOOL,
            VOUCHER_ANALYSIS_TOOL
    ));
    private static final List<String> CONFIRM_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "活动标题", "券类型", "支付金额", "抵扣金额", "库存", "开始时间", "结束时间", "适用门店", "是否秒杀券"
    ));

    private final AgentToolExecutor agentToolExecutor;
    private final MerchantCampaignDraftSkillService draftSkillService;
    private final MerchantAgentRulePolicyService rulePolicyService;

    public CouponDraftSkill(AgentToolExecutor agentToolExecutor,
                            MerchantCampaignDraftSkillService draftSkillService,
                            MerchantAgentRulePolicyService rulePolicyService) {
        this.agentToolExecutor = agentToolExecutor;
        this.draftSkillService = draftSkillService;
        this.rulePolicyService = rulePolicyService;
    }

    @Override
    public String name() {
        return SKILL_NAME;
    }

    @Override
    public SkillDefinition definition() {
        return new SkillDefinition()
                .setSkillName(SKILL_NAME)
                .setDisplayName("优惠券策略草稿 Skill")
                .setDescription("编排订单统计、优惠券分析、风险策略和安全草稿生成入口，仅生成待商家确认的优惠券活动草稿，不直接创建真实优惠券。")
                .setVersion("v1")
                .setAllowedTools(new ArrayList<>(ALLOWED_TOOLS))
                .setRiskLevel(SkillRiskLevel.HIGH)
                .setNeedHumanConfirm(true)
                .setModelCallable(false);
    }

    @Override
    public Class<CouponDraftSkillInput> inputType() {
        return CouponDraftSkillInput.class;
    }

    @Override
    public SkillResult<CouponDraftSkillOutput> execute(CouponDraftSkillInput input, SkillContext context) {
        if (input == null || input.getShopId() == null) {
            return failure("INVALID_SHOP_ID", "shopId不能为空");
        }
        if (isBlank(input.getCampaignGoal())) {
            return failure("INVALID_CAMPAIGN_GOAL", "campaignGoal不能为空");
        }
        String draftType = resolveDraftType(input.getDraftType());
        if (draftType == null) {
            return failure("INVALID_DRAFT_TYPE", "draftType只能是voucher或seckill");
        }

        String riskText = buildRiskText(input);
        if (hasBypassConfirmIntent(riskText) || rulePolicyService.isProhibitedOperation(riskText)) {
            SkillResult<CouponDraftSkillOutput> result = failure("PROHIBITED_OPERATION", "输入命中高危或绕过人工确认语义");
            result.setRiskLevel(SkillRiskLevel.HIGH);
            result.setNeedHumanConfirm(true);
            result.putMetadata("riskLevel", "HIGH")
                    .putMetadata("needHumanConfirm", true)
                    .putMetadata("riskWarnings", buildRiskWarnings(true, true));
            return result;
        }

        List<String> usedTools = new ArrayList<>();
        Map<String, String> failedTools = new LinkedHashMap<>();
        AgentToolExecutionRequestDTO toolRequest = buildToolRequest(input);
        executeReadonlyAnalysis(ORDER_ANALYSIS_TOOL, toolRequest, usedTools, failedTools);
        executeReadonlyAnalysis(VOUCHER_ANALYSIS_TOOL, toolRequest, usedTools, failedTools);

        boolean partialFailure = !failedTools.isEmpty();
        boolean needConfirm = true;
        String policyRiskLevel = rulePolicyService.resolveRiskLevel(riskText, "voucher_campaign_tool");
        String riskLevel = "HIGH";
        MerchantCampaignDraftRequest draftRequest = buildDraftRequest(input, draftType, partialFailure);

        Result draftResult;
        try {
            draftResult = draftSkillService.createDraftFromSkill(input.getShopId(), input.getCampaignGoal(),
                    input.getUserRequirement(), draftRequest, parseSessionId(context));
        } catch (Exception e) {
            return failure("DRAFT_CREATE_FAILED", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (draftResult == null || !Boolean.TRUE.equals(draftResult.getSuccess())) {
            return failure("DRAFT_CREATE_FAILED", draftResult == null ? "草稿生成失败" : draftResult.getErrorMsg());
        }

        MerchantCampaignDraftSkillResultDTO safeResult = (MerchantCampaignDraftSkillResultDTO) draftResult.getData();
        CouponDraftSkillOutput output = buildOutput(input.getShopId(), safeResult, partialFailure, failedTools);
        SkillResult<CouponDraftSkillOutput> result = SkillResult.success(output)
                .setRiskLevel(SkillRiskLevel.HIGH)
                .setNeedHumanConfirm(needConfirm)
                .putMetadata("skillName", SKILL_NAME)
                .putMetadata("shopId", input.getShopId())
                .putMetadata("draftId", output.getDraftId())
                .putMetadata("draftStatus", output.getDraftStatus())
                .putMetadata("riskLevel", riskLevel)
                .putMetadata("policyRiskLevel", policyRiskLevel)
                .putMetadata("needHumanConfirm", true)
                .putMetadata("riskWarnings", output.getRiskWarnings())
                .putMetadata("confirmFields", output.getConfirmFields())
                .putMetadata("usedReadonlyTools", usedTools)
                .putMetadata("partialFailure", partialFailure)
                .putMetadata("traceId", context == null ? null : context.getTraceId());
        if (partialFailure) {
            result.putMetadata("failedTools", failedTools);
        }
        for (String usedTool : usedTools) {
            result.addUsedTool(usedTool);
        }
        return result;
    }

    private void executeReadonlyAnalysis(String toolName,
                                         AgentToolExecutionRequestDTO request,
                                         List<String> usedTools,
                                         Map<String, String> failedTools) {
        AgentToolExecutionResultDTO result = agentToolExecutor.executeReadonlyTool(toolName, request);
        if (result != null && Boolean.TRUE.equals(result.getSuccess())) {
            usedTools.add(toolName);
            return;
        }
        failedTools.put(toolName, result == null ? "工具执行失败" : result.getErrorMsg());
    }

    private MerchantCampaignDraftRequest buildDraftRequest(CouponDraftSkillInput input,
                                                           String draftType,
                                                           boolean partialFailure) {
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setDraftType(draftType);
        request.setRecommendationType(draftType);
        request.setRecommendationTitle(input.getCampaignGoal().trim());
        request.setRecommendationReason(partialFailure
                ? "部分只读分析工具失败，先生成保守草稿，等待商家确认"
                : "基于只读订单和优惠券分析生成待确认草稿");
        request.setRecommendationAction(firstNotBlank(input.getUserRequirement(), "生成待商家确认的优惠券活动草稿"));
        if (input.getBudgetLimit() != null && input.getBudgetLimit() > 0) {
            long actualValue = Math.min(Math.max(input.getBudgetLimit().longValue(), 1000L), 100000L);
            request.setActualValue(actualValue);
            request.setPayValue(Math.max(100L, Math.round(actualValue * ("seckill".equals(draftType) ? 0.6D : 0.8D))));
        }
        if ("seckill".equals(draftType)) {
            request.setStock(100);
        }
        return request;
    }

    private CouponDraftSkillOutput buildOutput(Long shopId,
                                               MerchantCampaignDraftSkillResultDTO safeResult,
                                               boolean partialFailure,
                                               Map<String, String> failedTools) {
        CouponDraftSkillOutput output = new CouponDraftSkillOutput()
                .setDraftId(safeResult.getDraftId())
                .setShopId(shopId)
                .setDraftStatus(firstNotBlank(safeResult.getDraftStatus(), "PENDING"))
                .setDraftContent(safeResult.getDraftContent() == null ? new LinkedHashMap<>() : safeResult.getDraftContent())
                .setRiskWarnings(safeResult.getRiskWarnings() == null ? new ArrayList<>() : new ArrayList<>(safeResult.getRiskWarnings()))
                .setConfirmFields(resolveConfirmFields(safeResult.getConfirmFields()))
                .setNeedHumanConfirm(true)
                .setRiskLevel("HIGH")
                .setMetadata(safeResult.getMetadata() == null ? new LinkedHashMap<>() : safeResult.getMetadata());
        if (partialFailure) {
            output.getRiskWarnings().add("部分只读分析工具失败，草稿采用保守默认值，请重点核对金额、库存和时间");
            output.getMetadata().put("partialFailure", true);
            output.getMetadata().put("failedTools", failedTools);
        }
        output.getMetadata().put("needHumanConfirm", true);
        output.getMetadata().put("riskLevel", "HIGH");
        return output;
    }

    private List<String> resolveConfirmFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return new ArrayList<>(CONFIRM_FIELDS);
        }
        return new ArrayList<>(fields);
    }

    private AgentToolExecutionRequestDTO buildToolRequest(CouponDraftSkillInput input) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(resolveDays(input.getTimeRange()));
        return new AgentToolExecutionRequestDTO()
                .setShopId(input.getShopId())
                .setIntent("voucher_plan")
                .setDateRange(firstNotBlank(input.getTimeRange(), "LAST_30_DAYS"))
                .setStartTime(start)
                .setEndTime(end);
    }

    private int resolveDays(String timeRange) {
        if ("TODAY".equalsIgnoreCase(timeRange)) {
            return 1;
        }
        if ("LAST_7_DAYS".equalsIgnoreCase(timeRange) || "last_7_days".equalsIgnoreCase(timeRange)) {
            return 7;
        }
        return 30;
    }

    private String resolveDraftType(String draftType) {
        if (isBlank(draftType)) {
            return "voucher";
        }
        if ("voucher".equalsIgnoreCase(draftType)) {
            return "voucher";
        }
        if ("seckill".equalsIgnoreCase(draftType)) {
            return "seckill";
        }
        return null;
    }

    private boolean hasBypassConfirmIntent(String text) {
        return containsAny(text, "直接创建", "立即生效", "绕过确认", "自动确认", "不用人工",
                "confirmNow", "autoConfirm", "bypassConfirm");
    }

    private List<String> buildRiskWarnings(boolean prohibited, boolean bypassConfirm) {
        List<String> warnings = new ArrayList<>();
        if (prohibited) {
            warnings.add("输入命中高危操作规则");
        }
        if (bypassConfirm) {
            warnings.add("输入命中绕过人工确认风险");
        }
        warnings.add("优惠券活动草稿必须由商家确认后才会创建真实优惠券");
        return warnings;
    }

    private SkillResult<CouponDraftSkillOutput> failure(String errorCode, String errorMessage) {
        return SkillResult.<CouponDraftSkillOutput>failure(errorCode, errorMessage)
                .setRiskLevel(SkillRiskLevel.HIGH)
                .setNeedHumanConfirm(true);
    }

    private Long parseSessionId(SkillContext context) {
        if (context == null || isBlank(context.getSessionId())) {
            return null;
        }
        try {
            return Long.valueOf(context.getSessionId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildRiskText(CouponDraftSkillInput input) {
        return input.getCampaignGoal() + " " + (input.getUserRequirement() == null ? "" : input.getUserRequirement());
    }

    private boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNotBlank(String first, String fallback) {
        return isBlank(first) ? fallback : first.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }
}
