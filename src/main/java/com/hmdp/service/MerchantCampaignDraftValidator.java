package com.hmdp.service;

import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentCampaignDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 活动草稿字段校验组件。
 *
 * <p>Agent、前端和后续 Skill 都可能生成草稿字段，因此校验逻辑集中在这里，
 * 避免生成、编辑和确认三个阶段出现不一致。</p>
 */
@Component
public class MerchantCampaignDraftValidator {

    private static final int DRAFT_TITLE_MAX_LENGTH = 128;
    private static final int DRAFT_SUB_TITLE_MAX_LENGTH = 120;
    private static final int VOUCHER_RULES_MAX_LENGTH = 1024;
    private static final long MAX_VOUCHER_AMOUNT = 1_000_000L;
    private static final int MAX_SECKILL_STOCK = 10_000;
    private static final int MAX_VOUCHER_DURATION_DAYS = 180;
    private static final int MAX_SECKILL_DURATION_DAYS = 7;
    private static final int MAX_BEGIN_TIME_AFTER_DAYS = 365;

    public Result validateDraftUpdate(AgentCampaignDraft draft, MerchantCampaignDraftRequest request) {
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        if (request == null) {
            return Result.fail("草稿修改内容不能为空");
        }
        String title = request.getTitle() == null ? draft.getTitle() : request.getTitle();
        String subTitle = request.getSubTitle() == null ? draft.getSubTitle() : request.getSubTitle();
        String rules = request.getRules() == null ? draft.getRules() : request.getRules();
        Long payValue = request.getPayValue() == null ? draft.getPayValue() : request.getPayValue();
        Long actualValue = request.getActualValue() == null ? draft.getActualValue() : request.getActualValue();
        Integer stock = request.getStock() == null ? draft.getStock() : request.getStock();
        LocalDateTime beginTime = request.getBeginTime() == null ? draft.getBeginTime() : request.getBeginTime();
        LocalDateTime endTime = request.getEndTime() == null ? draft.getEndTime() : request.getEndTime();

        return validateDraftBusinessFields(draft.getDraftType(), title, subTitle, rules, payValue, actualValue, stock,
                beginTime, endTime, false);
    }

    public Result validateDraftBeforeConfirm(AgentCampaignDraft draft) {
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        return validateDraftBusinessFields(
                draft.getDraftType(),
                draft.getTitle(),
                draft.getSubTitle(),
                draft.getRules(),
                draft.getPayValue(),
                draft.getActualValue(),
                draft.getStock(),
                draft.getBeginTime(),
                draft.getEndTime(),
                true
        );
    }

    public Result validateDraftBusinessFields(String draftType,
                                              String title,
                                              String subTitle,
                                              String rules,
                                              Long payValue,
                                              Long actualValue,
                                              Integer stock,
                                              LocalDateTime beginTime,
                                              LocalDateTime endTime,
                                              boolean confirmStage) {
        if (!"voucher".equals(draftType) && !"seckill".equals(draftType)) {
            return Result.fail("草稿类型只能是普通代金券或秒杀券");
        }
        if (isBlank(title)) {
            return Result.fail("活动标题不能为空");
        }
        if (textLength(title) > DRAFT_TITLE_MAX_LENGTH) {
            return Result.fail("活动标题不能超过" + DRAFT_TITLE_MAX_LENGTH + "个字符");
        }
        if (textLength(subTitle) > DRAFT_SUB_TITLE_MAX_LENGTH) {
            return Result.fail("活动副标题不能超过" + DRAFT_SUB_TITLE_MAX_LENGTH + "个字符");
        }
        if (textLength(rules) > VOUCHER_RULES_MAX_LENGTH) {
            return Result.fail("活动规则不能超过" + VOUCHER_RULES_MAX_LENGTH + "个字符");
        }
        if (confirmStage && payValue == null) {
            return Result.fail("支付金额不能为空");
        }
        if (confirmStage && actualValue == null) {
            return Result.fail("抵扣金额不能为空");
        }
        if (payValue != null && payValue <= 0) {
            return Result.fail("支付金额必须大于0");
        }
        if (actualValue != null && actualValue <= 0) {
            return Result.fail("抵扣金额必须大于0");
        }
        if (payValue != null && payValue > MAX_VOUCHER_AMOUNT) {
            return Result.fail("支付金额不能超过10000元");
        }
        if (actualValue != null && actualValue > MAX_VOUCHER_AMOUNT) {
            return Result.fail("抵扣金额不能超过10000元");
        }
        if (payValue != null && actualValue != null && payValue >= actualValue) {
            return Result.fail("支付金额必须小于抵扣金额");
        }

        if ("seckill".equals(draftType) && (stock == null || stock <= 0)) {
            return Result.fail("秒杀券库存必须大于0");
        }
        if ("seckill".equals(draftType) && stock != null && stock > MAX_SECKILL_STOCK) {
            return Result.fail("秒杀券库存不能超过" + MAX_SECKILL_STOCK + "张");
        }

        if (beginTime != null && endTime != null && !beginTime.isBefore(endTime)) {
            return Result.fail("活动开始时间必须早于结束时间");
        }
        if (beginTime != null && beginTime.isAfter(LocalDateTime.now().plusDays(MAX_BEGIN_TIME_AFTER_DAYS))) {
            return Result.fail("活动开始时间不能超过一年后");
        }
        if (beginTime != null && endTime != null) {
            int maxDays = "seckill".equals(draftType) ? MAX_SECKILL_DURATION_DAYS : MAX_VOUCHER_DURATION_DAYS;
            if (beginTime.plusDays(maxDays).isBefore(endTime)) {
                return Result.fail(("seckill".equals(draftType) ? "秒杀活动" : "普通代金券") + "有效期不能超过" + maxDays + "天");
            }
        }
        if (confirmStage) {
            LocalDateTime now = LocalDateTime.now();
            if (beginTime == null || endTime == null) {
                return Result.fail("活动开始和结束时间不能为空");
            }
            if (!endTime.isAfter(now)) {
                return Result.fail("活动结束时间必须晚于当前时间");
            }
            if ("seckill".equals(draftType) && !beginTime.isAfter(now)) {
                return Result.fail("秒杀券开始时间必须晚于当前时间");
            }
        }
        return Result.ok();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }
}
