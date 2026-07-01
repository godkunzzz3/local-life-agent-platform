package com.hmdp.agent.skill.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 优惠券策略草稿 Skill 输入。
 */
@Data
@Accessors(chain = true)
public class CouponDraftSkillInput {

    private Long shopId;

    private String campaignGoal;

    private String timeRange;

    private Integer budgetLimit;

    private String userRequirement;

    /**
     * 草稿类型：voucher / seckill。
     */
    private String draftType;
}
