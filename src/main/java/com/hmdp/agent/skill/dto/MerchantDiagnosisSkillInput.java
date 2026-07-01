package com.hmdp.agent.skill.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 商家运营诊断 Skill 输入。
 */
@Data
@Accessors(chain = true)
public class MerchantDiagnosisSkillInput {

    private Long shopId;

    /**
     * 时间范围，例如 last_7_days / last_30_days。
     */
    private String timeRange;

    /**
     * 用户原始诊断需求。
     */
    private String userQuestion;
}
