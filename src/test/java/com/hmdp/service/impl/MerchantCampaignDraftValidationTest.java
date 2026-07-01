package com.hmdp.service.impl;

import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentCampaignDraft;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IMerchantAgentActionLogService;
import com.hmdp.service.IMerchantAgentSuggestionService;
import com.hmdp.service.IMerchantCampaignDraftService;
import com.hmdp.service.IMerchantService;
import com.hmdp.service.MerchantCampaignDraftValidator;
import com.hmdp.tool.VoucherAgentTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantCampaignDraftValidationTest {

    private static final Long DRAFT_ID = 1001L;
    private static final Long SHOP_ID = 10143L;
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_CREATED = 2;
    private static final int STATUS_REJECTED = 3;

    @InjectMocks
    private MerchantAgentFacadeServiceImpl facadeService;

    @Mock
    private IMerchantCampaignDraftService campaignDraftService;
    @Mock
    private IMerchantService merchantService;
    @Mock
    private VoucherAgentTool voucherAgentTool;
    @Mock
    private IMerchantAgentSuggestionService agentSuggestionService;
    @Mock
    private IMerchantAgentActionLogService agentActionLogService;
    @Spy
    private MerchantCampaignDraftValidator campaignDraftValidator = new MerchantCampaignDraftValidator();

    @Test
    void shouldRejectTooLongTitleOnUpdate() {
        givenPendingDraft("seckill");
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setTitle(repeat('a', 129));

        Result result = facadeService.updateCampaignDraft(DRAFT_ID, request);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectTooLongSubTitleOnUpdate() {
        givenPendingDraft("seckill");
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setSubTitle(repeat('a', 121));

        Result result = facadeService.updateCampaignDraft(DRAFT_ID, request);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectTooLongRulesOnUpdate() {
        givenPendingDraft("seckill");
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setRules(repeat('a', 1025));

        Result result = facadeService.updateCampaignDraft(DRAFT_ID, request);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectInvalidAmountsOnUpdate() {
        assertInvalidAmountRejected(0L, 1000L);
        assertInvalidAmountRejected(-1L, 1000L);
        assertInvalidAmountRejected(100L, 0L);
        assertInvalidAmountRejected(100L, -1L);
        assertInvalidAmountRejected(1000L, 1000L);
        assertInvalidAmountRejected(1200L, 1000L);
    }

    @Test
    void shouldRejectInvalidSeckillStockOnUpdate() {
        assertInvalidSeckillStockRejected(0);
        assertInvalidSeckillStockRejected(-1);
        assertInvalidSeckillStockRejected(10001);
    }

    @Test
    void shouldRejectInvalidActivityTimeOnUpdate() {
        LocalDateTime now = LocalDateTime.now();
        assertInvalidActivityTimeRejected("seckill", now.plusDays(1), now.plusDays(1));
        assertInvalidActivityTimeRejected("seckill", now.plusDays(1), now.plusDays(9));
        assertInvalidActivityTimeRejected("voucher", now.plusDays(1), now.plusDays(183));
    }

    @Test
    void shouldReuseValidationBeforeConfirm() {
        AgentCampaignDraft draft = validDraft("seckill")
                .setPayValue(1000L)
                .setActualValue(1000L);
        when(campaignDraftService.getById(DRAFT_ID)).thenReturn(draft);
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);

        Result result = facadeService.confirmCampaignDraft(DRAFT_ID);

        assertFalse(result.getSuccess());
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectConfirmNonPendingDraft() {
        assertNonPendingConfirmRejected(STATUS_CREATED);
        assertNonPendingConfirmRejected(STATUS_REJECTED);
    }

    private void assertInvalidAmountRejected(Long payValue, Long actualValue) {
        givenPendingDraft("seckill");
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setPayValue(payValue);
        request.setActualValue(actualValue);

        Result result = facadeService.updateCampaignDraft(DRAFT_ID, request);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
    }

    private void assertInvalidSeckillStockRejected(Integer stock) {
        givenPendingDraft("seckill");
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setStock(stock);

        Result result = facadeService.updateCampaignDraft(DRAFT_ID, request);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
    }

    private void assertInvalidActivityTimeRejected(String draftType, LocalDateTime beginTime, LocalDateTime endTime) {
        givenPendingDraft(draftType);
        MerchantCampaignDraftRequest request = new MerchantCampaignDraftRequest();
        request.setBeginTime(beginTime);
        request.setEndTime(endTime);

        Result result = facadeService.updateCampaignDraft(DRAFT_ID, request);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).updateById(any(AgentCampaignDraft.class));
    }

    private void assertNonPendingConfirmRejected(Integer status) {
        AgentCampaignDraft draft = validDraft("seckill").setStatus(status);
        when(campaignDraftService.getById(DRAFT_ID)).thenReturn(draft);

        Result result = facadeService.confirmCampaignDraft(DRAFT_ID);

        assertFalse(result.getSuccess());
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    private void givenPendingDraft(String draftType) {
        when(campaignDraftService.getById(DRAFT_ID)).thenReturn(validDraft(draftType));
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
    }

    private AgentCampaignDraft validDraft(String draftType) {
        LocalDateTime beginTime = LocalDateTime.now().plusDays(1);
        int durationDays = "seckill".equals(draftType) ? 3 : 30;
        return new AgentCampaignDraft()
                .setId(DRAFT_ID)
                .setSuggestionId(2001L)
                .setShopId(SHOP_ID)
                .setDraftType(draftType)
                .setTitle("周末拉新活动")
                .setSubTitle("限时福利")
                .setPayValue(800L)
                .setActualValue(1000L)
                .setStock(100)
                .setBeginTime(beginTime)
                .setEndTime(beginTime.plusDays(durationDays))
                .setRules("{}")
                .setStatus(STATUS_PENDING);
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
