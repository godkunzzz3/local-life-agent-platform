package com.hmdp.dto;

import lombok.Data;

/**
 * Agent Memory 候选确认请求，第一版预留扩展字段。
 */
@Data
public class AgentMemoryCandidateConfirmRequest {

    private String memoryKey;

    private String memoryValue;

    private String candidateType;
}
