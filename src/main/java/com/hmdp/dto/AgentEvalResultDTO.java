package com.hmdp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent 行为评测单条结果。
 */
@Data
public class AgentEvalResultDTO {

    private Long resultId;
    private Long caseId;
    private String caseName;
    private String userInput;
    private String expectedIntent;
    private String actualIntent;
    private List<String> expectedTools;
    private List<String> actualTools;
    private Boolean expectedNeedConfirm;
    private Boolean actualNeedConfirm;
    private String expectedRiskLevel;
    private String actualRiskLevel;
    private Boolean intentPassed;
    private Boolean toolPassed;
    private Boolean confirmPassed;
    private Boolean riskPassed;
    private Boolean passed;
    private BigDecimal score;
    private String diagnosis;
}
