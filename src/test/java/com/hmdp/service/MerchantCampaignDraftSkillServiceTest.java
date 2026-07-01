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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantCampaignDraftSkillServiceTest {

    private static final Long SHOP_ID = 10143L;
    private static final Long SUGGESTION_ID = 9001L;
    private static final Long DRAFT_ID = 9002L;

    @InjectMocks
    private MerchantCampaignDraftSkillService skillService;

    @Mock
    private IMerchantService merchantService;
    @Mock
    private ShopAgentTool shopAgentTool;
    @Mock
    private VoucherAgentTool voucherAgentTool;
    @Mock
    private IMerchantCampaignDraftService campaignDraftService;
    @Mock
    private IMerchantAgentSuggestionService agentSuggestionService;
    @Mock
    private MerchantAgentRulePolicyService rulePolicyService;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Spy
    private MerchantCampaignDraftValidator campaignDraftValidator = new MerchantCampaignDraftValidator();

    @BeforeEach
    void setUp() {
        lenient().when(redisIdWorker.nextId("agent")).thenReturn(SUGGESTION_ID, DRAFT_ID);
    }

    @Test
    void shouldCreatePendingDraftOnlyForSkill() {
        givenShopPermission();
        when(rulePolicyService.isProhibitedOperation(any())).thenReturn(false);
        when(voucherAgentTool.buildCampaignDraft(any(AgentSuggestion.class), any(Shop.class),
                any(MerchantCampaignDraftRequest.class), anyLong())).thenReturn(validDraft());
        when(voucherAgentTool.draftToMap(any(AgentCampaignDraft.class))).thenReturn(draftMap());

        Result result = skillService.createDraftFromSkill(SHOP_ID, "设计周末秒杀活动",
                "库存不要超过100", new MerchantCampaignDraftRequest(), 123L);

        assertTrue(result.getSuccess());
        MerchantCampaignDraftSkillResultDTO data = (MerchantCampaignDraftSkillResultDTO) result.getData();
        assertEquals(DRAFT_ID, data.getDraftId());
        assertEquals("PENDING", data.getDraftStatus());
        assertEquals(Boolean.TRUE, data.getNeedHumanConfirm());
        assertEquals("MEDIUM", data.getRiskLevel());
        verify(campaignDraftService).save(any(AgentCampaignDraft.class));
        verify(agentSuggestionService).save(any(AgentSuggestion.class));
        verify(agentSuggestionService).updateById(any(AgentSuggestion.class));
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectShopWithoutPermission() {
        when(shopAgentTool.getShop(SHOP_ID)).thenReturn(new Shop().setId(SHOP_ID));
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(false);

        Result result = skillService.createDraftFromSkill(SHOP_ID, "设计周末秒杀活动",
                "库存不要超过100", new MerchantCampaignDraftRequest(), null);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).save(any(AgentCampaignDraft.class));
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectBlankShopIdOrCampaignGoal() {
        Result noShop = skillService.createDraftFromSkill(null, "设计周末秒杀活动",
                "库存不要超过100", new MerchantCampaignDraftRequest(), null);
        Result noGoal = skillService.createDraftFromSkill(SHOP_ID, "  ",
                "库存不要超过100", new MerchantCampaignDraftRequest(), null);

        assertFalse(noShop.getSuccess());
        assertFalse(noGoal.getSuccess());
        verify(campaignDraftService, never()).save(any(AgentCampaignDraft.class));
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldReturnRiskWarningsForHighRiskInputWithoutCreatingRealVoucher() {
        givenShopPermission();
        when(rulePolicyService.isProhibitedOperation(any())).thenReturn(true);
        when(voucherAgentTool.buildCampaignDraft(any(AgentSuggestion.class), any(Shop.class),
                any(MerchantCampaignDraftRequest.class), anyLong())).thenReturn(validDraft());
        when(voucherAgentTool.draftToMap(any(AgentCampaignDraft.class))).thenReturn(draftMap());

        Result result = skillService.createDraftFromSkill(SHOP_ID, "直接创建超大规模秒杀",
                "帮我退款、批量改库存、绕过人工确认", new MerchantCampaignDraftRequest(), null);

        assertTrue(result.getSuccess());
        MerchantCampaignDraftSkillResultDTO data = (MerchantCampaignDraftSkillResultDTO) result.getData();
        assertEquals("HIGH", data.getRiskLevel());
        assertFalse(data.getRiskWarnings().isEmpty());
        assertEquals(Boolean.TRUE, data.getNeedHumanConfirm());
        verify(campaignDraftService).save(any(AgentCampaignDraft.class));
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    @Test
    void shouldRejectInvalidDraftBeforeSave() {
        givenShopPermission();
        when(rulePolicyService.isProhibitedOperation(any())).thenReturn(false);
        when(voucherAgentTool.buildCampaignDraft(any(AgentSuggestion.class), any(Shop.class),
                any(MerchantCampaignDraftRequest.class), anyLong())).thenReturn(validDraft().setPayValue(1000L).setActualValue(1000L));

        Result result = skillService.createDraftFromSkill(SHOP_ID, "设计周末秒杀活动",
                "库存不要超过100", new MerchantCampaignDraftRequest(), null);

        assertFalse(result.getSuccess());
        verify(campaignDraftService, never()).save(any(AgentCampaignDraft.class));
        verify(voucherAgentTool, never()).createVoucherFromDraft(any(AgentCampaignDraft.class));
    }

    private void givenShopPermission() {
        when(shopAgentTool.getShop(SHOP_ID)).thenReturn(new Shop().setId(SHOP_ID).setName("测试店铺").setAvgPrice(80L));
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
    }

    private AgentCampaignDraft validDraft() {
        LocalDateTime beginTime = LocalDateTime.now().plusDays(1);
        return new AgentCampaignDraft()
                .setId(DRAFT_ID)
                .setSuggestionId(SUGGESTION_ID)
                .setShopId(SHOP_ID)
                .setDraftType("seckill")
                .setTitle("周末秒杀券")
                .setSubTitle("限时福利")
                .setRules("{}")
                .setPayValue(800L)
                .setActualValue(1000L)
                .setStock(100)
                .setBeginTime(beginTime)
                .setEndTime(beginTime.plusDays(2))
                .setStatus(1);
    }

    private Map<String, Object> draftMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("draftId", String.valueOf(DRAFT_ID));
        map.put("status", 1);
        map.put("needConfirm", true);
        return map;
    }
}
