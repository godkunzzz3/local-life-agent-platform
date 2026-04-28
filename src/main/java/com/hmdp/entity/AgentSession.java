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
 * 商家运营 Agent 会话。
 *
 * <p>一条会话对应商家围绕某个店铺发起的一次 Agent 咨询，例如“生成运营报告”
 * 或“设计周末优惠券活动”。企业项目中会话表通常作为消息、建议、草稿和审计日志的
 * 聚合入口，便于后续做上下文恢复、问题追踪和操作回放。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_session")
public class AgentSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。项目内统一使用 RedisIdWorker 生成，避免分布式场景下数据库自增 ID 冲突。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * Agent 服务的目标店铺。
     */
    private Long shopId;

    /**
     * 发起咨询的商家用户 ID。学习阶段可先复用 tb_user 的用户 ID。
     */
    private Long merchantId;

    /**
     * 会话标题，展示在商家端会话列表中。
     */
    private String title;

    /**
     * 业务场景：operation_report / voucher_plan / review_reply。
     */
    private String scene;

    /**
     * 会话状态：1进行中，2已完成，3已关闭。
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
