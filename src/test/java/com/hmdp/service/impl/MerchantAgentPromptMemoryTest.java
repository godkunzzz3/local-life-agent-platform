package com.hmdp.service.impl;

import com.hmdp.dto.AgentMemoryPromptDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantAgentPromptMemoryTest {

    private final MerchantAgentMemoryServiceImpl memoryService = new MerchantAgentMemoryServiceImpl();

    @Test
    void shouldAppendEnabledMemoryToPrompt() {
        String prompt = memoryService.buildMemoryPrompt(Collections.singletonList(
                memory("PREFERENCE", "activity_style", "偏好周末轻量秒杀活动")
        ));

        assertTrue(prompt.contains("activity_style"));
        assertTrue(prompt.contains("偏好周末轻量秒杀活动"));
    }

    @Test
    void shouldNotAppendDisabledMemoryWhenInputExcludesIt() {
        String prompt = memoryService.buildMemoryPrompt(Collections.singletonList(
                memory("PREFERENCE", "activity_style", "偏好周末轻量秒杀活动")
        ));

        assertFalse(prompt.contains("禁用后不应进入 Prompt"));
    }

    @Test
    void shouldIncludeToolResultPriorityRule() {
        String prompt = memoryService.buildMemoryPrompt(Collections.singletonList(
                memory("CONSTRAINT", "budget", "活动预算偏保守")
        ));

        assertTrue(prompt.contains("工具查询结果为准"));
        assertTrue(prompt.contains("不是真实业务数据"));
    }

    @Test
    void shouldLimitMemoryPromptLength() {
        List<AgentMemoryPromptDTO> memories = new ArrayList<>();
        char[] chars = new char[300];
        Arrays.fill(chars, 'a');
        String longValue = new String(chars);
        for (int i = 0; i < 10; i++) {
            memories.add(memory("PREFERENCE", "key_" + i, longValue));
        }

        String prompt = memoryService.buildMemoryPrompt(memories);

        assertTrue(prompt.length() <= 1200);
    }

    private AgentMemoryPromptDTO memory(String type, String key, String value) {
        return new AgentMemoryPromptDTO()
                .setMemoryType(type)
                .setMemoryKey(key)
                .setMemoryValue(value);
    }
}
