package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent 行为评测用例 DTO。
 */
@Data
public class AgentEvalCaseItemDTO {

    private Long caseId;
    private String caseName;
    private String userInput;
    private String expectedIntent;
    private List<String> expectedTools;
    private Boolean expectedNeedConfirm;
    private String expectedRiskLevel;
    private List<String> expectedKeywords;
    private String caseType;
    private Integer sortOrder;
    private Integer status;
}
