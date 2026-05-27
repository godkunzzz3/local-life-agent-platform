package com.hmdp.service.impl;

import com.hmdp.agent.MerchantAgentRulePolicyService;
import com.hmdp.dto.AgentEvalCaseItemDTO;
import com.hmdp.dto.AgentEvalRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentEvalResult;
import com.hmdp.entity.AgentEvalRun;
import com.hmdp.service.IMerchantAgentEvalCaseService;
import com.hmdp.service.IMerchantAgentEvalResultService;
import com.hmdp.service.IMerchantAgentEvalRunService;
import com.hmdp.tool.AgentToolDescriptor;
import com.hmdp.tool.AgentToolRegistry;
import com.hmdp.tool.OperationDiagnosisToolDescriptor;
import com.hmdp.tool.OrderAgentTool;
import com.hmdp.tool.ReviewAgentTool;
import com.hmdp.tool.ShopAgentTool;
import com.hmdp.tool.VoucherAgentTool;
import com.hmdp.tool.VoucherAnalysisToolDescriptor;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAgentEvalServiceTest {

    private MerchantAgentEvalServiceImpl evalService;

    @Mock
    private IMerchantAgentEvalCaseService agentEvalCaseService;
    @Mock
    private IMerchantAgentEvalRunService agentEvalRunService;
    @Mock
    private IMerchantAgentEvalResultService agentEvalResultService;
    @Mock
    private RedisIdWorker redisIdWorker;

    @BeforeEach
    void setUp() {
        evalService = new MerchantAgentEvalServiceImpl();
        ReflectionTestUtils.setField(evalService, "rulePolicyService", buildRulePolicyService());
        ReflectionTestUtils.setField(evalService, "agentEvalCaseService", agentEvalCaseService);
        ReflectionTestUtils.setField(evalService, "agentEvalRunService", agentEvalRunService);
        ReflectionTestUtils.setField(evalService, "agentEvalResultService", agentEvalResultService);
        ReflectionTestUtils.setField(evalService, "redisIdWorker", redisIdWorker);
        AtomicLong id = new AtomicLong(100L);
        when(redisIdWorker.nextId("agent")).thenAnswer(invocation -> id.getAndIncrement());
    }

    @Test
    void shouldEvaluateCustomCases() {
        AgentEvalRequest request = new AgentEvalRequest();
        request.setCases(Collections.singletonList(caseItem("订单分析", "帮我分析最近7天订单情况",
                "order_analysis", "order_analysis_tool", false, "LOW")));

        Result result = evalService.evaluateAgent(request);

        Map<String, Object> data = data(result);
        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals("custom", data.get("caseSource"));
        assertEquals(1, data.get("totalCount"));
        assertEquals(1, data.get("passCount"));
        assertEquals(new BigDecimal("100.00"), data.get("overallScore"));
    }

    @Test
    void shouldEvaluatePersistedCases() {
        when(agentEvalCaseService.listEnabledCaseItems()).thenReturn(Collections.singletonList(
                caseItem("评价分析", "最近用户主要吐槽什么",
                        "review_analysis", "review_content_tool", false, "LOW")
        ));

        Result result = evalService.evaluateAgent(null);

        Map<String, Object> data = data(result);
        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals("persisted", data.get("caseSource"));
        assertEquals(1, data.get("passCount"));
    }

    @Test
    void shouldUseDefaultCasesWhenNoPersistedCases() {
        when(agentEvalCaseService.listEnabledCaseItems()).thenReturn(Collections.emptyList());

        Result result = evalService.evaluateAgent(null);

        Map<String, Object> data = data(result);
        assertEquals(Boolean.TRUE, result.getSuccess());
        assertEquals("default", data.get("caseSource"));
        assertEquals(8, data.get("totalCount"));
        assertEquals(8, data.get("passCount"));
    }

    @Test
    void shouldCalculateMetrics() {
        AgentEvalRequest request = new AgentEvalRequest();
        request.setCases(Arrays.asList(
                caseItem("订单分析", "帮我分析最近7天订单情况",
                        "order_analysis", "order_analysis_tool", false, "LOW"),
                caseItem("错误期望", "帮我分析最近7天订单情况",
                        "review_analysis", "review_content_tool", true, "HIGH")
        ));

        Result result = evalService.evaluateAgent(request);

        Map<String, Object> data = data(result);
        assertEquals(2, data.get("totalCount"));
        assertEquals(1, data.get("passCount"));
        assertEquals("50.00%", data.get("intentAccuracy"));
        assertEquals("50.00%", data.get("toolAccuracy"));
        assertEquals("50.00%", data.get("confirmAccuracy"));
        assertEquals("50.00%", data.get("riskAccuracy"));
        assertEquals(new BigDecimal("50.00"), data.get("overallScore"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldSaveEvalRunAndResults() {
        AgentEvalRequest request = new AgentEvalRequest();
        request.setCases(Collections.singletonList(caseItem("活动", "帮我设计一个周末秒杀活动",
                "voucher_plan", "voucher_campaign_tool", true, "MEDIUM")));

        evalService.evaluateAgent(request);

        ArgumentCaptor<AgentEvalRun> runCaptor = ArgumentCaptor.forClass(AgentEvalRun.class);
        ArgumentCaptor<List<AgentEvalResult>> resultCaptor = ArgumentCaptor.forClass(List.class);
        verify(agentEvalRunService).save(runCaptor.capture());
        verify(agentEvalResultService).saveBatch(resultCaptor.capture());

        assertEquals("custom", runCaptor.getValue().getCaseSource());
        assertEquals(1, resultCaptor.getValue().size());
        assertEquals("voucher_plan", resultCaptor.getValue().get(0).getActualIntent());
        assertEquals(Integer.valueOf(1), resultCaptor.getValue().get(0).getPassed());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldDiagnoseFailedCase() {
        AgentEvalRequest request = new AgentEvalRequest();
        request.setCases(Collections.singletonList(caseItem("失败诊断", "帮我分析最近7天订单情况",
                "review_analysis", "review_content_tool", true, "HIGH")));

        Result result = evalService.evaluateAgent(request);

        Map<String, Object> data = data(result);
        List<Object> items = (List<Object>) data.get("items");
        Object firstItem = items.get(0);
        String itemText = String.valueOf(firstItem);

        assertEquals(0, data.get("passCount"));
        assertTrue(itemText.contains("意图不匹配"));
        assertTrue(itemText.contains("工具不匹配"));
        assertTrue(itemText.contains("确认判断不匹配"));
        assertTrue(itemText.contains("风险等级不匹配"));
    }

    private AgentEvalCaseItemDTO caseItem(String caseName, String userInput, String expectedIntent,
                                          String expectedTool, boolean expectedNeedConfirm,
                                          String expectedRiskLevel) {
        AgentEvalCaseItemDTO item = new AgentEvalCaseItemDTO();
        item.setCaseName(caseName);
        item.setUserInput(userInput);
        item.setExpectedIntent(expectedIntent);
        item.setExpectedTools(Collections.singletonList(expectedTool));
        item.setExpectedNeedConfirm(expectedNeedConfirm);
        item.setExpectedRiskLevel(expectedRiskLevel);
        return item;
    }

    private MerchantAgentRulePolicyService buildRulePolicyService() {
        List<AgentToolDescriptor> descriptors = Arrays.asList(
                new ShopAgentTool(),
                new OrderAgentTool(),
                new ReviewAgentTool(),
                new VoucherAnalysisToolDescriptor(),
                new OperationDiagnosisToolDescriptor(),
                new VoucherAgentTool()
        );
        return new MerchantAgentRulePolicyService(new AgentToolRegistry(descriptors));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Result result) {
        return (Map<String, Object>) result.getData();
    }
}
