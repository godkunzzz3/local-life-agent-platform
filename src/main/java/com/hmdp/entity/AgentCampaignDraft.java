package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 活动草稿。
 *
 * <p>Agent 生成优惠券或秒杀活动时，先写入草稿表，而不是直接写真实业务表。
 * 商家确认后再把草稿转换成 tb_voucher / tb_seckill_voucher，这就是 Agent 场景里常见的
 * human-in-the-loop 机制。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_campaign_draft")
public class AgentCampaignDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。项目内统一使用 RedisIdWorker 生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联的 Agent 建议 ID。
     */
    private Long suggestionId;

    /**
     * 活动所属店铺 ID。
     */
    private Long shopId;

    /**
     * 草稿类型：voucher / seckill。
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
     * 活动库存。普通券可为空，秒杀券建议必填。
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
     * 活动规则 JSON，例如适用时段、限购规则、不可用日期。
     */
    private String rules;

    /**
     * Agent 推荐理由。
     */
    private String reason;

    /**
     * 状态：1待确认，2已创建，3已拒绝，4已过期。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
