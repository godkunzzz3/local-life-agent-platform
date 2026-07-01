package com.hmdp.agent.skill.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家运营诊断 Skill 输出。
 */
@Data
@Accessors(chain = true)
public class MerchantDiagnosisSkillOutput {

    private Long shopId;

    private String timeRange;

    private Map<String, Object> shopProfile = new LinkedHashMap<>();

    private Map<String, Object> orderStats = new LinkedHashMap<>();

    private Map<String, Object> voucherStats = new LinkedHashMap<>();

    private Map<String, Object> reviewSummary = new LinkedHashMap<>();

    private List<String> keyFindings = new ArrayList<>();

    private List<String> possibleReasons = new ArrayList<>();

    private List<String> suggestions = new ArrayList<>();

    private List<String> usedTools = new ArrayList<>();
}
