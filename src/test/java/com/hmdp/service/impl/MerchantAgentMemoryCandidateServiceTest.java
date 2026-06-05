package com.hmdp.service.impl;

import com.hmdp.agent.MerchantAgentMemoryCandidateExtractor;
import com.hmdp.dto.AgentMemoryCandidateDTO;
import com.hmdp.dto.AgentMemoryCandidateGenerateRequest;
import com.hmdp.dto.AgentMemoryCandidateGenerateResultDTO;
import com.hmdp.dto.AgentMemoryCandidateRequest;
import com.hmdp.dto.AgentMemoryDTO;
import com.hmdp.dto.AgentMemoryRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AgentMemoryCandidate;
import com.hmdp.service.AgentMemoryValidator;
import com.hmdp.service.AgentWorkflowRecorderService;
import com.hmdp.service.IMerchantAgentMemoryService;
import com.hmdp.service.IMerchantService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAgentMemoryCandidateServiceTest {

    private static final Long SHOP_ID = 10143L;
    private static final Long MERCHANT_ID = 1010L;

    private MerchantAgentMemoryCandidateServiceImpl candidateService;

    @Mock
    private IMerchantService merchantService;
    @Mock
    private IMerchantAgentMemoryService memoryService;
    @Mock
    private AgentWorkflowRecorderService workflowRecorderService;
    @Mock
    private RedisIdWorker redisIdWorker;

    @BeforeEach
    void setUp() {
        candidateService = spy(new MerchantAgentMemoryCandidateServiceImpl());
        ReflectionTestUtils.setField(candidateService, "merchantService", merchantService);
        ReflectionTestUtils.setField(candidateService, "agentMemoryService", memoryService);
        ReflectionTestUtils.setField(candidateService, "agentWorkflowRecorderService", workflowRecorderService);
        ReflectionTestUtils.setField(candidateService, "redisIdWorker", redisIdWorker);
        ReflectionTestUtils.setField(candidateService, "agentMemoryValidator", new AgentMemoryValidator());
        ReflectionTestUtils.setField(candidateService, "candidateExtractor", new MerchantAgentMemoryCandidateExtractor());
        UserDTO user = new UserDTO();
        user.setId(MERCHANT_ID);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldGeneratePendingCandidates() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        when(redisIdWorker.nextId("agent")).thenReturn(1L);
        when(workflowRecorderService.startRun(eq(null), eq(SHOP_ID), eq(MERCHANT_ID), eq("memory_candidate"),
                eq("manual_generate"), any(), eq("memory_candidate"))).thenReturn(99L);
        doReturn(true).when(candidateService).save(any(AgentMemoryCandidate.class));

        Result result = candidateService.generateCandidates(SHOP_ID, generateRequest("以后活动文案都轻松一点"));

        assertTrue(result.getSuccess());
        AgentMemoryCandidateGenerateResultDTO data = (AgentMemoryCandidateGenerateResultDTO) result.getData();
        assertEquals(1, data.getHitCount());
        assertEquals("activity_style", data.getCandidates().get(0).getMemoryKey());
        ArgumentCaptor<AgentMemoryCandidate> captor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(candidateService).save(captor.capture());
        assertEquals(MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING, captor.getValue().getStatus());
        verify(workflowRecorderService).recordStep(eq(99L), eq(null), eq(SHOP_ID), eq(1),
                eq("MEMORY_CANDIDATE_GENERATE"), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEmptyWhenNoCandidateGenerated() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);

        Result result = candidateService.generateCandidates(SHOP_ID, generateRequest("帮我看一下订单"));

        assertTrue(result.getSuccess());
        AgentMemoryCandidateGenerateResultDTO data = (AgentMemoryCandidateGenerateResultDTO) result.getData();
        assertEquals(0, data.getHitCount());
        verify(candidateService, never()).save(any(AgentMemoryCandidate.class));
    }

    @Test
    void shouldEditPendingCandidate() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING)).when(candidateService).getById(1L);
        doReturn(true).when(candidateService).updateById(any(AgentMemoryCandidate.class));

        Result result = candidateService.updateCandidate(1L,
                candidateRequest("CONSTRAINT", "discount_limit", "商家不希望折扣力度过大"));

        assertTrue(result.getSuccess());
        ArgumentCaptor<AgentMemoryCandidate> captor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(candidateService).updateById(captor.capture());
        assertEquals("discount_limit", captor.getValue().getMemoryKey());
    }

    @Test
    void shouldRejectEditNonPendingCandidate() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_CREATED)).when(candidateService).getById(1L);

        Result result = candidateService.updateCandidate(1L,
                candidateRequest("PREFERENCE", "activity_style", "文案轻松一点"));

        assertFalse(result.getSuccess());
        verify(candidateService, never()).updateById(any(AgentMemoryCandidate.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldConfirmCandidateAndCreateMemory() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        AgentMemoryCandidate candidate = candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING);
        doReturn(candidate).when(candidateService).getById(1L);
        doReturn(true).when(candidateService).updateById(any(AgentMemoryCandidate.class));
        when(memoryService.createMemory(eq(SHOP_ID), any(AgentMemoryRequest.class)))
                .thenReturn(Result.ok(new AgentMemoryDTO().setMemoryId(88L)));

        Result result = candidateService.confirmCandidate(1L, null);

        assertTrue(result.getSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(88L, data.get("createdMemoryId"));
        ArgumentCaptor<AgentMemoryRequest> memoryCaptor = ArgumentCaptor.forClass(AgentMemoryRequest.class);
        verify(memoryService).createMemory(eq(SHOP_ID), memoryCaptor.capture());
        assertEquals("activity_style", memoryCaptor.getValue().getMemoryKey());
        ArgumentCaptor<AgentMemoryCandidate> updateCaptor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(candidateService).updateById(updateCaptor.capture());
        assertEquals(MerchantAgentMemoryCandidateServiceImpl.STATUS_CREATED, updateCaptor.getValue().getStatus());
    }

    @Test
    void shouldRejectConfirmNonPendingCandidate() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_CREATED)).when(candidateService).getById(1L);

        Result result = candidateService.confirmCandidate(1L, null);

        assertFalse(result.getSuccess());
        verify(memoryService, never()).createMemory(any(), any());
    }

    @Test
    void shouldRejectSensitiveCandidateOnConfirm() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        AgentMemoryCandidate candidate = candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING)
                .setMemoryValue("商家 token 是 abc");
        doReturn(candidate).when(candidateService).getById(1L);

        Result result = candidateService.confirmCandidate(1L, null);

        assertFalse(result.getSuccess());
        verify(memoryService, never()).createMemory(any(), any());
    }

    @Test
    void shouldRejectCrossShopConfirm() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(false);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING)).when(candidateService).getById(1L);

        Result result = candidateService.confirmCandidate(1L, null);

        assertFalse(result.getSuccess());
        verify(memoryService, never()).createMemory(any(), any());
    }

    @Test
    void shouldRejectDuplicateConfirm() {
        shouldRejectConfirmNonPendingCandidate();
    }

    @Test
    void shouldRejectPendingCandidate() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING)).when(candidateService).getById(1L);
        doReturn(true).when(candidateService).updateById(any(AgentMemoryCandidate.class));

        Result result = candidateService.rejectCandidate(1L);

        assertTrue(result.getSuccess());
        ArgumentCaptor<AgentMemoryCandidate> captor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(candidateService).updateById(captor.capture());
        assertEquals(MerchantAgentMemoryCandidateServiceImpl.STATUS_REJECTED, captor.getValue().getStatus());
    }

    @Test
    void shouldDeletePendingCandidate() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING)).when(candidateService).getById(1L);
        doReturn(true).when(candidateService).updateById(any(AgentMemoryCandidate.class));

        Result result = candidateService.deleteCandidate(1L);

        assertTrue(result.getSuccess());
        ArgumentCaptor<AgentMemoryCandidate> captor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(candidateService).updateById(captor.capture());
        assertEquals(MerchantAgentMemoryCandidateServiceImpl.STATUS_DELETED, captor.getValue().getStatus());
    }

    @Test
    void shouldRecordWorkflowCandidateGenerateSafely() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        when(redisIdWorker.nextId("agent")).thenReturn(1L);
        doThrow(new RuntimeException("workflow down")).when(workflowRecorderService)
                .startRun(any(), any(), any(), any(), any(), any(), any());
        doReturn(true).when(candidateService).save(any(AgentMemoryCandidate.class));

        Result result = candidateService.generateCandidates(SHOP_ID, generateRequest("文案轻松一点"));

        assertTrue(result.getSuccess());
    }

    @Test
    void shouldRecordWorkflowCandidateConfirmSafely() {
        when(merchantService.hasCurrentUserShopPermission(SHOP_ID)).thenReturn(true);
        doReturn(candidate(1L, MerchantAgentMemoryCandidateServiceImpl.STATUS_PENDING)).when(candidateService).getById(1L);
        doReturn(true).when(candidateService).updateById(any(AgentMemoryCandidate.class));
        when(memoryService.createMemory(eq(SHOP_ID), any(AgentMemoryRequest.class)))
                .thenReturn(Result.ok(new AgentMemoryDTO().setMemoryId(88L)));
        doThrow(new RuntimeException("workflow down")).when(workflowRecorderService)
                .startRun(any(), any(), any(), any(), any(), any(), any());

        Result result = candidateService.confirmCandidate(1L, null);

        assertTrue(result.getSuccess());
    }

    private AgentMemoryCandidateGenerateRequest generateRequest(String text) {
        AgentMemoryCandidateGenerateRequest request = new AgentMemoryCandidateGenerateRequest();
        request.setText(text);
        return request;
    }

    private AgentMemoryCandidateRequest candidateRequest(String type, String key, String value) {
        AgentMemoryCandidateRequest request = new AgentMemoryCandidateRequest();
        request.setCandidateType(type);
        request.setMemoryKey(key);
        request.setMemoryValue(value);
        request.setReason("测试候选");
        return request;
    }

    private AgentMemoryCandidate candidate(Long id, String status) {
        return new AgentMemoryCandidate()
                .setId(id)
                .setShopId(SHOP_ID)
                .setMerchantId(MERCHANT_ID)
                .setCandidateType("PREFERENCE")
                .setMemoryKey("activity_style")
                .setMemoryValue("商家偏好活动文案轻松、亲切")
                .setReason("从商家表达的活动文案偏好中提取")
                .setStatus(status);
    }
}
