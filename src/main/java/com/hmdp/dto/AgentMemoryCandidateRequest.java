package com.hmdp.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent Memory 候选新增/编辑请求。
 */
@Data
public class AgentMemoryCandidateRequest {

    private String candidateType;

    private String memoryKey;

    private String memoryValue;

    private String reason;

    private BigDecimal confidence;
}
