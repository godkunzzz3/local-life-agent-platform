package com.hmdp.service;

import com.hmdp.agent.MerchantAgentRulePolicyService;
import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.MerchantCampaignDraftSkillResultDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentCampaignDraft;
import com.hmdp.entity.AgentSuggestion;
import com.hmdp.entity.Shop;
import com.hmdp.tool.ShopAgentTool;
import com.hmdp.tool.VoucherAgentTool;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后续 CouponDraftSkill 专用的安全草稿生成入口。
 *
 * <p>该服务只创建待确认活动草稿，不创建真实优惠券，不初始化秒杀 Redis 库存。</p>
 */
@Service
public class MerchantCampaignDraftSkillService {

    private static final int DRAFT_STATUS_PENDING = 1;
    private static final int SUGGESTION_STATUS_ADOPTED = 2;

    @Resource
    private IMerchantService merchantService;
    @Resource
    private ShopAgentTool shopAgentTool;
    @Resource
    private VoucherAgentTool voucherAgentTool;
    @Resource
    private IMerchantCampaignDraftService campaignDraftService;
    @Resource
    private IMerchantAgentSuggestionService agentSuggestionService;
    @Resource
    private MerchantCampaignDraftValidator campaignDraftValidator;
    @Resource
    private MerchantAgentRulePolicyService rulePolicyService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Transactional
    public Result createDraftFromSkill(Long shopId,
                                       String campaignGoal,
                                       String userRequirement,
                                       MerchantCampaignDraftRequest request,
                                       Long sessionId) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (isBlank(campaignGoal)) {
            return Result.fail("活动目标不能为空");
        }
        Shop shop = shopAgentTool.getShop(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺");
        }

        MerchantCampaignDraftRequest safeRequest = buildSafeDraftRequest(campaignGoal, userRequirement, request);
        String riskText = campaignGoal + " " + (userRequirement == null ? "" : userRequirement);
        boolean prohibited = rulePolicyService.isProhibitedOperation(riskText);
        String riskLevel = prohibited ? "HIGH" : "MEDIUM";
        List<String> riskWarnings = buildRiskWarnings(prohibited);

        AgentSuggestion suggestion = buildSkillSuggestion(shopId, campaignGoal, userRequirement, sessionId, riskLevel);
        agentSuggestionService.save(suggestion);
        AgentCampaignDraft draft = voucherAgentTool.buildCampaignDraft(suggestion, shop, safeRequest, nextAgentId());
        Result validateResult = campaignDraftValidator.validateDraftBusinessFields(
                draft.getDraftType(), draft.getTitle(), draft.getSubTitle(), draft.getRules(),
                draft.getPayValue(), draft.getActualValue(), draft.getStock(), draft.getBeginTime(), draft.getEndTime(), false);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        campaignDraftService.save(draft);
        agentSuggestionService.updateById(new AgentSuggestion()
                .setId(suggestion.getId())
                .setStatus(SUGGESTION_STATUS_ADOPTED));

        MerchantCampaignDraftSkillResultDTO result = new MerchantCampaignDraftSkillResultDTO()
                .setDraftId(draft.getId())
                .setSuggestionId(suggestion.getId())
                .setShopId(shopId)
                .setDraftStatus("PENDING")
                .setDraftContent(voucherAgentTool.draftToMap(draft))
                .setRiskWarnings(riskWarnings)
                .setConfirmFields(confirmFields())
                .setNeedHumanConfirm(true)
                .setRiskLevel(riskLevel);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "coupon_draft_skill");
        metadata.put("draftStatusCode", DRAFT_STATUS_PENDING);
        metadata.put("needHumanConfirm", true);
        metadata.put("prohibitedInputDetected", prohibited);
        result.setMetadata(metadata);
        return Result.ok(result);
    }

    private MerchantCampaignDraftRequest buildSafeDraftRequest(String campaignGoal,
                                                               String userRequirement,
                                                               MerchantCampaignDraftRequest request) {
        MerchantCampaignDraftRequest safeRequest = request == null ? new MerchantCampaignDraftRequest() : request;
        if (isBlank(safeRequest.getRecommendationType())) {
            safeRequest.setRecommendationType(resolveRecommendationType(campaignGoal + " " + userRequirement));
        }
        if (isBlank(safeRequest.getRecommendationTitle())) {
            safeRequest.setRecommendationTitle(campaignGoal.trim());
        }
        if (isBlank(safeRequest.getRecommendationReason())) {
            safeRequest.setRecommendationReason("来自 CouponDraftSkill 的活动目标：" + campaignGoal.trim());
        }
        if (isBlank(safeRequest.getRecommendationAction())) {
            safeRequest.setRecommendationAction(isBlank(userRequirement)
                    ? "生成待商家确认的活动草稿"
                    : userRequirement.trim());
        }
        return safeRequest;
    }

    private AgentSuggestion buildSkillSuggestion(Long shopId,
                                                 String campaignGoal,
                                                 String userRequirement,
                                                 Long sessionId,
                                                 String riskLevel) {
        return new AgentSuggestion()
                .setId(nextAgentId())
                .setSessionId(sessionId)
                .setShopId(shopId)
                .setSuggestionType(resolveRecommendationType(campaignGoal + " " + userRequirement))
                .setTitle(campaignGoal.trim())
                .setSummary(campaignGoal.trim())
                .setContent(isBlank(userRequirement) ? "生成待确认优惠券活动草稿" : userRequirement.trim())
                .setConfidenceScore(new BigDecimal("70.00"))
                .setRiskLevel("HIGH".equals(riskLevel) ? 3 : 2)
                .setStatus(SUGGESTION_STATUS_ADOPTED);
    }

    private List<String> buildRiskWarnings(boolean prohibited) {
        if (prohibited) {
            return Arrays.asList(
                    "输入命中高危操作规则，本入口仍只允许生成待确认草稿",
                    "草稿不会创建真实优惠券，必须由商家在确认接口中再次确认"
            );
        }
        return Arrays.asList("优惠券活动属于写操作建议，必须由商家确认后才会创建真实优惠券");
    }

    private List<String> confirmFields() {
        return Arrays.asList("title", "subTitle", "payValue", "actualValue", "stock", "beginTime", "endTime", "rules");
    }

    private String resolveRecommendationType(String text) {
        return text != null && text.contains("普通") ? "voucher" : "seckill";
    }

    private long nextAgentId() {
        return redisIdWorker.nextId("agent");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }
}
