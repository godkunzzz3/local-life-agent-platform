package com.hmdp.service;

import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentCampaignDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantCampaignDraftValidatorTest {

    private final MerchantCampaignDraftValidator validator = new MerchantCampaignDraftValidator();

    @Test
    void shouldValidateBasicDraftFields() {
        LocalDateTime beginTime = LocalDateTime.now().plusDays(1);

        Result result = validator.validateDraftBusinessFields(
                "seckill", "周末秒杀券", "限时福利", "{}", 800L, 1000L, 100,
                beginTime, beginTime.plusDays(2), false);

        assertTrue(result.getSuccess());
    }

    @Test
    void shouldRejectInvalidBasicDraftFields() {
        LocalDateTime beginTime = LocalDateTime.now().plusDays(1);

        Result result = validator.validateDraftBusinessFields(
                "seckill", "周末秒杀券", "限时福利", "{}", 1000L, 1000L, 100,
                beginTime, beginTime.plusDays(2), false);

        assertFalse(result.getSuccess());
    }

    @Test
    void shouldValidateUpdateByMergingExistingDraftFields() {
        AgentCampaignDraft draft = validDraft();
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setStock(0);

        Result result = validator.validateDraftUpdate(draft, request);

        assertFalse(result.getSuccess());
    }

    @Test
    void shouldValidateBeforeConfirmWithStrongerRules() {
        AgentCampaignDraft draft = validDraft()
                .setBeginTime(LocalDateTime.now().minusHours(1))
                .setEndTime(LocalDateTime.now().plusDays(1));

        Result result = validator.validateDraftBeforeConfirm(draft);

        assertFalse(result.getSuccess());
    }

    private AgentCampaignDraft validDraft() {
        LocalDateTime beginTime = LocalDateTime.now().plusDays(1);
        return new AgentCampaignDraft()
                .setDraftType("seckill")
                .setTitle("周末秒杀券")
                .setSubTitle("限时福利")
                .setRules("{}")
                .setPayValue(800L)
                .setActualValue(1000L)
                .setStock(100)
                .setBeginTime(beginTime)
                .setEndTime(beginTime.plusDays(2));
    }
}
