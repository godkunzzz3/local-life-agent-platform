package com.hmdp.agent;

import com.hmdp.dto.AgentMemoryCandidateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantAgentMemoryCandidateExtractorTest {

    private MerchantAgentMemoryCandidateExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MerchantAgentMemoryCandidateExtractor();
    }

    @Test
    void shouldExtractActivityStylePreference() {
        List<AgentMemoryCandidateRequest> result = extractor.extract("以后活动文案都轻松一点，不要太官方");

        assertEquals(1, result.size());
        assertEquals("PREFERENCE", result.get(0).getCandidateType());
        assertEquals("activity_style", result.get(0).getMemoryKey());
    }

    @Test
    void shouldExtractDiscountLimitConstraint() {
        List<AgentMemoryCandidateRequest> result = extractor.extract("我不想折扣太大，不要让利太多");

        assertEquals(1, result.size());
        assertEquals("CONSTRAINT", result.get(0).getCandidateType());
        assertEquals("discount_limit", result.get(0).getMemoryKey());
    }

    @Test
    void shouldExtractStockPreference() {
        List<AgentMemoryCandidateRequest> result = extractor.extract("秒杀库存不要太多，库存别超过100张");

        assertEquals(1, result.size());
        assertEquals("stock_preference", result.get(0).getMemoryKey());
        assertTrue(result.get(0).getMemoryValue().contains("100"));
    }

    @Test
    void shouldExtractCampaignTimePreference() {
        List<AgentMemoryCandidateRequest> result = extractor.extract("以后优先做周末活动，周末效果更好");

        assertEquals(1, result.size());
        assertEquals("campaign_time_preference", result.get(0).getMemoryKey());
    }

    @Test
    void shouldExtractCampaignGoalPreference() {
        List<AgentMemoryCandidateRequest> oldCustomer = extractor.extract("我们更关注复购，重点做老客");
        List<AgentMemoryCandidateRequest> newCustomer = extractor.extract("这次想拉新，多吸引新客");

        assertEquals("campaign_goal_preference", oldCustomer.get(0).getMemoryKey());
        assertTrue(oldCustomer.get(0).getMemoryValue().contains("老客"));
        assertEquals("campaign_goal_preference", newCustomer.get(0).getMemoryKey());
        assertTrue(newCustomer.get(0).getMemoryValue().contains("新客"));
    }

    @Test
    void shouldReturnEmptyWhenNoRuleMatched() {
        assertTrue(extractor.extract("帮我看一下最近订单").isEmpty());
    }

    @Test
    void shouldRejectSensitiveInput() {
        assertTrue(extractor.extract("以后联系手机号13800138000").isEmpty());
        assertTrue(extractor.extract("token 是 abc").isEmpty());
    }
}
