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
 * Agent 操作审计日志。
 *
 * <p>凡是 Agent 或商家基于 Agent 建议触发的关键动作，都应该写入审计表。
 * 这能回答三个企业项目里非常重要的问题：谁发起的、对什么目标做了什么、执行结果如何。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_action_log")
public class AgentActionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。项目内统一使用 RedisIdWorker 生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关联会话 ID。系统自动任务可能为空。
     */
    private Long sessionId;

    /**
     * 操作所属店铺 ID。
     */
    private Long shopId;

    /**
     * 操作人 ID。Agent 自动分析时可为空，商家确认动作时保存当前用户 ID。
     */
    private Long operatorId;

    /**
     * 操作人类型：agent / merchant / system。
     */
    private String operatorType;

    /**
     * 操作类型，例如 create_draft、confirm_draft、reject_suggestion。
     */
    private String actionType;

    /**
     * 目标类型：voucher / seckill / suggestion / draft。
     */
    private String targetType;

    /**
     * 目标 ID。
     */
    private Long targetId;

    /**
     * 请求参数 JSON。
     */
    private String requestData;

    /**
     * 执行结果 JSON。
     */
    private String resultData;

    /**
     * 状态：1成功，2失败。
     */
    private Integer status;

    /**
     * 失败原因。成功时为空。
     */
    private String errorMsg;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
