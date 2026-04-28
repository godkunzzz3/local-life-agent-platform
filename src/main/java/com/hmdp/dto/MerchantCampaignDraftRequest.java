package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 活动草稿请求参数。
 *
 * <p>草稿可以由 Agent 自动生成，也允许商家端传入覆盖字段。这样前端既能“一键生成”，
 * 也能在确认前微调标题、金额、库存和活动时间。</p>
 */
@Data
public class MerchantCampaignDraftRequest {

    /**
     * 草稿类型：voucher / seckill。为空时默认 seckill。
     */
    private String draftType;

    /**
     * 活动标题。
     */
    private String title;

    /**
     * 活动副标题。
     */
    private String subTitle;

    /**
     * 用户支付金额，单位分。
     */
    private Long payValue;

    /**
     * 抵扣金额，单位分。
     */
    private Long actualValue;

    /**
     * 库存。秒杀券建议必填，不传时后端给安全默认值。
     */
    private Integer stock;

    /**
     * 活动开始时间。
     */
    private LocalDateTime beginTime;

    /**
     * 活动结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 活动规则 JSON 字符串。
     */
    private String rules;
}
