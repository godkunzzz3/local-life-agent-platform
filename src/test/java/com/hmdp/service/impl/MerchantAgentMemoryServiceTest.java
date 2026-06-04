package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.AgentMemoryDTO;
import com.hmdp.dto.AgentMemoryRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AgentMemory;
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

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAgentMemoryServiceTest {

    private MerchantAgentMemoryServiceImpl memoryService;

    @Mock
    private IMerchantService merchantService;
    @Mock
    private RedisIdWorker redisIdWorker;

    @BeforeEach
    void setUp() {
        memoryService = spy(new MerchantAgentMemoryServiceImpl());
        ReflectionTestUtils.setField(memoryService, "merchantService", merchantService);
        ReflectionTestUtils.setField(memoryService, "redisIdWorker", redisIdWorker);
        UserDTO user = new UserDTO();
        user.setId(1010L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void shouldCreateMemory() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        when(redisIdWorker.nextId("agent")).thenReturn(1L);
        doReturn(true).when(memoryService).save(any(AgentMemory.class));

        Result result = memoryService.createMemory(10143L, request("PREFERENCE", "activity_style", "偏好周末轻量秒杀活动", 1));

        assertEquals(Boolean.TRUE, result.getSuccess());
        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(memoryService).save(captor.capture());
        AgentMemory saved = captor.getValue();
        assertEquals(1L, saved.getId());
        assertEquals(1010L, saved.getMerchantId());
        assertEquals("PREFERENCE", saved.getMemoryType());
        assertEquals("manual", saved.getSourceType());
    }

    @Test
    void shouldUpdateMemory() {
        AgentMemory oldMemory = new AgentMemory()
                .setId(1L)
                .setShopId(10143L)
                .setStatus(1);
        AgentMemory freshMemory = new AgentMemory()
                .setId(1L)
                .setShopId(10143L)
                .setMemoryType("CONSTRAINT")
                .setMemoryKey("budget")
                .setMemoryValue("活动预算偏保守")
                .setStatus(1);
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        doReturn(oldMemory, freshMemory).when(memoryService).getById(1L);
        doReturn(true).when(memoryService).updateById(any(AgentMemory.class));

        Result result = memoryService.updateMemory(1L, request("CONSTRAINT", "budget", "活动预算偏保守", 1));

        assertEquals(Boolean.TRUE, result.getSuccess());
        AgentMemoryDTO dto = (AgentMemoryDTO) result.getData();
        assertEquals("CONSTRAINT", dto.getMemoryType());
        assertEquals("budget", dto.getMemoryKey());
    }

    @Test
    void shouldDisableMemoryOnDelete() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        doReturn(new AgentMemory().setId(1L).setShopId(10143L)).when(memoryService).getById(1L);
        doReturn(true).when(memoryService).updateById(any(AgentMemory.class));

        Result result = memoryService.deleteMemory(1L);

        assertEquals(Boolean.TRUE, result.getSuccess());
        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(memoryService).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldQueryEnabledMemory() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        doReturn(Collections.singletonList(new AgentMemory()
                .setId(1L)
                .setShopId(10143L)
                .setMemoryType("PREFERENCE")
                .setMemoryKey("activity_style")
                .setMemoryValue("偏好周末活动")
                .setStatus(1))).when(memoryService).list(any(QueryWrapper.class));

        Result result = memoryService.queryMemories(10143L, 1, "PREFERENCE");

        assertEquals(Boolean.TRUE, result.getSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(1, data.get("total"));
    }

    @Test
    void shouldRejectBlankMemoryKey() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);

        Result result = memoryService.createMemory(10143L, request("PREFERENCE", " ", "偏好周末活动", 1));

        assertEquals(Boolean.FALSE, result.getSuccess());
    }

    @Test
    void shouldRejectBlankMemoryValue() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);

        Result result = memoryService.createMemory(10143L, request("PREFERENCE", "activity_style", " ", 1));

        assertEquals(Boolean.FALSE, result.getSuccess());
    }

    @Test
    void shouldRejectTooLongMemoryValue() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 513; i++) {
            value.append('a');
        }

        Result result = memoryService.createMemory(10143L, request("PREFERENCE", "activity_style", value.toString(), 1));

        assertEquals(Boolean.FALSE, result.getSuccess());
    }

    @Test
    void shouldRejectPhoneInMemoryValue() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);

        Result result = memoryService.createMemory(10143L, request("PREFERENCE", "contact", "商家手机号13800138000", 1));

        assertEquals(Boolean.FALSE, result.getSuccess());
    }

    @Test
    void shouldRejectSensitiveFieldsInMemoryValue() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(true);

        assertFalse(memoryService.createMemory(10143L, request("PREFERENCE", "token", "abc", 1)).getSuccess());
        assertFalse(memoryService.createMemory(10143L, request("PREFERENCE", "api", "apiKey=mock-key", 1)).getSuccess());
        assertFalse(memoryService.createMemory(10143L, request("PREFERENCE", "pwd", "password mock secret", 1)).getSuccess());
        assertFalse(memoryService.createMemory(10143L, request("PREFERENCE", "auth", "authorization Bearer abc", 1)).getSuccess());
    }

    @Test
    void shouldRejectWithoutShopPermission() {
        when(merchantService.hasCurrentUserShopPermission(10143L)).thenReturn(false);

        Result result = memoryService.createMemory(10143L, request("PREFERENCE", "activity_style", "偏好周末活动", 1));

        assertEquals(Boolean.FALSE, result.getSuccess());
    }

    private AgentMemoryRequest request(String type, String key, String value, Integer status) {
        AgentMemoryRequest request = new AgentMemoryRequest();
        request.setMemoryType(type);
        request.setMemoryKey(key);
        request.setMemoryValue(value);
        request.setStatus(status);
        return request;
    }
}
