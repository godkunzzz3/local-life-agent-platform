package com.hmdp.dto;

import lombok.Data;

/**
 * Agent Memory 候选生成请求。
 */
@Data
public class AgentMemoryCandidateGenerateRequest {

    private String text;

    private Long sessionId;

    private Long sourceMessageId;
}
