package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent Memory 候选前端展示 DTO。
 */
@Data
@Accessors(chain = true)
public class AgentMemoryCandidateDTO {

    private Long candidateId;

    private Long shopId;

    private Long merchantId;

    private Long sessionId;

    private Long sourceMessageId;

    private String candidateType;

    private String memoryKey;

    private String memoryValue;

    private String reason;

    private BigDecimal confidence;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
