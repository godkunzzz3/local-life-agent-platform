package com.hmdp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 行为评测运行摘要。
 */
@Data
public class AgentEvalRunSummaryDTO {

    private Long runId;
    private String caseSource;
    private Integer totalCount;
    private Integer passCount;
    private Integer failCount;
    private String intentAccuracy;
    private String toolAccuracy;
    private String confirmAccuracy;
    private String riskAccuracy;
    private BigDecimal overallScore;
    private String summary;
    private LocalDateTime createTime;
}
