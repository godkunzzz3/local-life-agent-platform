package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家运营 Agent 建议。
 *
 * <p>Agent 的关键输出不能只停留在聊天文本里。建议表把“运营报告、优惠券建议、
 * 秒杀建议、评价回复建议”等结构化保存，方便前端做卡片展示、商家采纳，以及后续统计
 * Agent 建议的命中率。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_suggestion")
public class AgentSuggestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。项目内统一使用 RedisIdWorker 生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 所属会话 ID。
     */
    private Long sessionId;

    /**
     * 建议作用的店铺 ID。
     */
    private Long shopId;

    /**
     * 建议类型：voucher / seckill / review / operation。
     */
    private String suggestionType;

    /**
     * 建议标题。
     */
    private String title;

    /**
     * 建议摘要，适合前端列表展示。
     */
    private String summary;

    /**
     * 完整建议内容，保存 Agent 生成的详细分析。
     */
    private String content;

    /**
     * 置信度，取值 0-100。第一版可以为空，后续再由模型或规则计算。
     */
    private BigDecimal confidenceScore;

    /**
     * 风险等级：1低，2中，3高。
     */
    private Integer riskLevel;

    /**
     * 状态：1待确认，2已采纳，3已拒绝，4已执行。
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
