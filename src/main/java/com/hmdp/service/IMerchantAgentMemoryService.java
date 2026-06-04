package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentMemoryPromptDTO;
import com.hmdp.dto.AgentMemoryRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentMemory;

import java.util.List;
import java.util.Map;

/**
 * 商家运营 Agent Memory 服务。
 */
public interface IMerchantAgentMemoryService extends IService<AgentMemory> {

    Result queryMemories(Long shopId, Integer status, String memoryType);

    Result createMemory(Long shopId, AgentMemoryRequest request);

    Result updateMemory(Long memoryId, AgentMemoryRequest request);

    Result deleteMemory(Long memoryId);

    List<AgentMemoryPromptDTO> listPromptMemories(Long shopId);

    String buildMemoryPrompt(List<AgentMemoryPromptDTO> memories);

    Map<String, Object> buildMemoryLoadSummary(List<AgentMemoryPromptDTO> memories);
}
