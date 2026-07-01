package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hmdp.agent.MerchantAgentEmbeddingService;
import com.hmdp.entity.AgentKnowledgeDoc;
import com.hmdp.mapper.AgentKnowledgeDocMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAgentKnowledgeDocShopScopeTest {

    @Mock
    private AgentKnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private MerchantAgentEmbeddingService embeddingService;

    private MerchantAgentKnowledgeDocServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantAgentKnowledgeDocServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", knowledgeDocMapper);
        ReflectionTestUtils.setField(service, "embeddingService", embeddingService);
        lenient().when(embeddingService.embedQuery(any())).thenReturn(null);
    }

    @Test
    void shouldKeepAgentKnowledgeDocShopIdField() {
        AgentKnowledgeDoc doc = new AgentKnowledgeDoc().setShopId(10143L);

        assertEquals(10143L, doc.getShopId());
    }

    @Test
    void shouldKeepGlobalRetrieveForAgentBehavior() {
        when(knowledgeDocMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                doc(1L, null, "公共秒杀规则"),
                doc(2L, 10143L, "当前店铺秒杀规则"),
                doc(3L, 20202L, "其他店铺秒杀规则")
        ));

        List<Map<String, Object>> hits = service.retrieveForAgent("voucher_plan", "秒杀库存怎么设置", 5);

        assertEquals(3, hits.size());
    }

    @Test
    void shouldReturnOnlyCurrentShopAndPublicKnowledgeForShopScopedRetrieval() {
        when(knowledgeDocMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(
                doc(1L, null, "公共秒杀规则"),
                doc(2L, 10143L, "当前店铺秒杀规则"),
                doc(3L, 20202L, "其他店铺秒杀规则")
        ));

        List<Map<String, Object>> hits = service.retrieveForAgentForShop(10143L, "voucher_plan", "秒杀库存怎么设置", 5);

        assertEquals(2, hits.size());
        assertTrue(hits.stream().anyMatch(row -> row.get("shopId") == null));
        assertTrue(hits.stream().anyMatch(row -> Long.valueOf(10143L).equals(row.get("shopId"))));
        assertFalse(hits.stream().anyMatch(row -> Long.valueOf(20202L).equals(row.get("shopId"))));
    }

    @Test
    void shouldReturnNoReliableHitInsteadOfOtherShopKnowledge() {
        when(knowledgeDocMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(
                doc(3L, 20202L, "其他店铺秒杀规则")
        ));

        List<Map<String, Object>> hits = service.retrieveForAgentForShop(10143L, "voucher_plan", "秒杀库存怎么设置", 5);

        assertTrue(hits.isEmpty());
    }

    @Test
    void shouldRejectNullShopIdForShopScopedRetrieval() {
        List<Map<String, Object>> hits = service.retrieveForAgentForShop(null, "voucher_plan", "秒杀库存怎么设置", 5);

        assertTrue(hits.isEmpty());
    }

    @Test
    void shouldRejectBlankMessageForShopScopedRetrieval() {
        List<Map<String, Object>> hits = service.retrieveForAgentForShop(10143L, "voucher_plan", "   ", 5);

        assertTrue(hits.isEmpty());
    }

    private AgentKnowledgeDoc doc(Long id, Long shopId, String title) {
        return new AgentKnowledgeDoc()
                .setId(id)
                .setShopId(shopId)
                .setTitle(title)
                .setCategory("seckill_rule")
                .setContent(title + "，库存应控制在可承接范围内。")
                .setStatus(1);
    }
}
