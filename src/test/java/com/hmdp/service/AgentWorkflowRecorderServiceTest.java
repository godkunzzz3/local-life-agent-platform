package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.AgentWorkflowRun;
import com.hmdp.entity.AgentWorkflowStep;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWorkflowRecorderServiceTest {

    @InjectMocks
    private AgentWorkflowRecorderService recorderService;

    @Mock
    private IMerchantAgentWorkflowRunService workflowRunService;
    @Mock
    private IMerchantAgentWorkflowStepService workflowStepService;
    @Mock
    private IMerchantService merchantService;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldStartRun() {
        when(redisIdWorker.nextId("agent")).thenReturn(1L);

        Long runId = recorderService.startRun(10L, 10143L, 100L,
                "agent_chat", "merchant_message", "手机号13812345678的店铺问题", "order_analysis");

        ArgumentCaptor<AgentWorkflowRun> captor = ArgumentCaptor.forClass(AgentWorkflowRun.class);
        verify(workflowRunService).save(captor.capture());
        AgentWorkflowRun run = captor.getValue();
        assertEquals(1L, runId);
        assertEquals("agent_chat", run.getScene());
        assertEquals(AgentWorkflowRecorderService.RUN_STATUS_RUNNING, run.getStatus());
        assertFalse(run.getUserMessage().contains("13812345678"));
        assertTrue(run.getUserMessage().contains("138****5678"));
    }

    @Test
    void shouldRecordStep() {
        when(redisIdWorker.nextId("agent")).thenReturn(2L);

        recorderService.recordStep(1L, 10L, 10143L, 1,
                "execute_tool", "执行工具", "TOOL_EXECUTE", "getShopProfile", "success",
                Collections.singletonMap("shopId", 10143L),
                Collections.singletonMap("token", "secret"), "执行成功", null, 12L);

        ArgumentCaptor<AgentWorkflowStep> captor = ArgumentCaptor.forClass(AgentWorkflowStep.class);
        verify(workflowStepService).save(captor.capture());
        AgentWorkflowStep step = captor.getValue();
        assertEquals(2L, step.getId());
        assertEquals("execute_tool", step.getStepCode());
        assertEquals(AgentWorkflowRecorderService.STEP_STATUS_SUCCESS, step.getStatus());
        assertEquals(12L, step.getCostMillis());
    }

    @Test
    void shouldRecordMemoryLoadStepWithoutFullMemoryValue() {
        when(redisIdWorker.nextId("agent")).thenReturn(2L);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("shopId", 10143L);
        input.put("status", 1);
        input.put("memoryTypes", Collections.singletonList("PREFERENCE"));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("hitCount", 1);
        output.put("memoryKeys", Collections.singletonList("activity_style"));
        output.put("truncatedSummary", "activity_style:偏好周末轻量活动");

        recorderService.recordStep(1L, 10L, 10143L, 1,
                "MEMORY_LOAD", "加载商家记忆", "MEMORY_LOAD", null, "success",
                input, output, "本轮命中 1 条启用商家记忆", null, null);

        ArgumentCaptor<AgentWorkflowStep> captor = ArgumentCaptor.forClass(AgentWorkflowStep.class);
        verify(workflowStepService).save(captor.capture());
        AgentWorkflowStep step = captor.getValue();
        assertEquals("MEMORY_LOAD", step.getStepCode());
        assertEquals("MEMORY_LOAD", step.getNodeType());
        assertTrue(step.getOutputJson().contains("activity_style"));
        assertFalse(step.getOutputJson().contains("偏好周末轻量活动且不要打折超过20个点"));
    }

    @Test
    void shouldMaskSensitiveJsonFields() {
        when(redisIdWorker.nextId("agent")).thenReturn(2L);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("token", "abc-token");
        output.put("apiKey", "sk-123");
        output.put("api_key", "sk-456");
        output.put("authorization", "Bearer abc");
        output.put("password", "pwd");
        output.put("secret", "secret-value");
        output.put("code", "123456");
        output.put("verifyCode", "654321");
        output.put("captcha", "9999");
        output.put("phone", "13800138000");
        output.put("mobile", "13900139000");

        recorderService.recordStep(1L, 10L, 10143L, 1,
                "execute_tool", "执行工具", "TOOL_EXECUTE", null, "success",
                null, output, "执行成功", null, null);

        ArgumentCaptor<AgentWorkflowStep> captor = ArgumentCaptor.forClass(AgentWorkflowStep.class);
        verify(workflowStepService).save(captor.capture());
        String outputJson = captor.getValue().getOutputJson();
        assertFalse(outputJson.contains("abc-token"));
        assertFalse(outputJson.contains("sk-123"));
        assertFalse(outputJson.contains("sk-456"));
        assertFalse(outputJson.contains("Bearer abc"));
        assertFalse(outputJson.contains("pwd"));
        assertFalse(outputJson.contains("secret-value"));
        assertFalse(outputJson.contains("123456"));
        assertFalse(outputJson.contains("654321"));
        assertFalse(outputJson.contains("9999"));
        assertFalse(outputJson.contains("13800138000"));
        assertFalse(outputJson.contains("13900139000"));
        assertTrue(outputJson.contains("***"));
    }

    @Test
    void shouldMaskSensitiveFieldsCaseInsensitive() {
        when(redisIdWorker.nextId("agent")).thenReturn(2L);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("Token", "abc");
        output.put("Authorization", "Bearer xyz");
        output.put("APIKEY", "sk-upper");

        recorderService.recordStep(1L, 10L, 10143L, 1,
                "execute_tool", "执行工具", "TOOL_EXECUTE", null, "success",
                null, output, "执行成功", null, null);

        ArgumentCaptor<AgentWorkflowStep> captor = ArgumentCaptor.forClass(AgentWorkflowStep.class);
        verify(workflowStepService).save(captor.capture());
        String outputJson = captor.getValue().getOutputJson();
        assertFalse(outputJson.contains("abc"));
        assertFalse(outputJson.contains("Bearer xyz"));
        assertFalse(outputJson.contains("sk-upper"));
        assertTrue(outputJson.contains("***"));
    }

    @Test
    void shouldStillMaskPlainTextPhone() {
        when(redisIdWorker.nextId("agent")).thenReturn(1L);

        recorderService.startRun(10L, 10143L, 100L,
                "agent_chat", "merchant_message", "商家手机号是13800138000，请联系", "order_analysis");

        ArgumentCaptor<AgentWorkflowRun> captor = ArgumentCaptor.forClass(AgentWorkflowRun.class);
        verify(workflowRunService).save(captor.capture());
        String userMessage = captor.getValue().getUserMessage();
        assertFalse(userMessage.contains("13800138000"));
        assertTrue(userMessage.contains("138****8000"));
    }

    @Test
    void shouldFinishRun() {
        when(workflowRunService.getById(1L)).thenReturn(new AgentWorkflowRun()
                .setId(1L)
                .setStartTime(LocalDateTime.now().minusSeconds(1)));

        recorderService.finishRun(1L, "已完成回复");

        ArgumentCaptor<AgentWorkflowRun> captor = ArgumentCaptor.forClass(AgentWorkflowRun.class);
        verify(workflowRunService).updateById(captor.capture());
        assertEquals(AgentWorkflowRecorderService.RUN_STATUS_SUCCESS, captor.getValue().getStatus());
        assertEquals("已完成回复", captor.getValue().getSummary());
    }

    @Test
    void shouldFailRun() {
        when(workflowRunService.getById(1L)).thenReturn(new AgentWorkflowRun()
                .setId(1L)
                .setStartTime(LocalDateTime.now().minusSeconds(1)));

        recorderService.failRun(1L, "模型调用失败");

        ArgumentCaptor<AgentWorkflowRun> captor = ArgumentCaptor.forClass(AgentWorkflowRun.class);
        verify(workflowRunService).updateById(captor.capture());
        assertEquals(AgentWorkflowRecorderService.RUN_STATUS_FAILED, captor.getValue().getStatus());
        assertEquals("模型调用失败", captor.getValue().getErrorMsg());
    }

    @Test
    void shouldIgnoreRecordStepException() {
        when(redisIdWorker.nextId("agent")).thenReturn(2L);
        doThrow(new RuntimeException("db down")).when(workflowStepService).save(any(AgentWorkflowStep.class));

        assertDoesNotThrow(() -> recorderService.recordStep(1L, 10L, 10143L, 1,
                "execute_tool", "执行工具", "TOOL_EXECUTE", null, "success",
                null, null, "执行成功", null, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCheckPermissionWhenQueryRuns() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        when(workflowRunService.list(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                new AgentWorkflowRun().setId(1L).setShopId(10143L).setStatus(2).setScene("agent_chat")
        ));

        assertEquals(Boolean.TRUE, recorderService.queryRuns(10143L).getSuccess());
    }

    @Test
    void shouldRejectQueryRunsWithoutPermission() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(false);

        assertEquals(Boolean.FALSE, recorderService.queryRuns(10143L).getSuccess());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCheckRunShopPermissionWhenQuerySteps() {
        when(workflowRunService.getById(1L)).thenReturn(new AgentWorkflowRun().setId(1L).setShopId(10143L));
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        when(workflowStepService.list(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                new AgentWorkflowStep().setId(2L).setRunId(1L).setShopId(10143L).setStepOrder(1).setStatus(1)
        ));

        assertEquals(Boolean.TRUE, recorderService.querySteps(1L).getSuccess());
    }

    @Test
    void shouldRejectQueryStepsWithoutPermission() {
        when(workflowRunService.getById(1L)).thenReturn(new AgentWorkflowRun().setId(1L).setShopId(10143L));
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(false);

        assertEquals(Boolean.FALSE, recorderService.querySteps(1L).getSuccess());
    }
}
