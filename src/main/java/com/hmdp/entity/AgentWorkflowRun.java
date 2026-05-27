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
 * Agent Workflow 运行记录。
 *
 * <p>一条 run 对应一次 Agent 执行，例如普通对话或 Tool Calling 对话。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_workflow_run")
public class AgentWorkflowRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long sessionId;

    private Long shopId;

    private Long merchantId;

    private String scene;

    private String triggerType;

    private String userMessage;

    private String intent;

    /**
     * 状态：1运行中，2成功，3失败。
     */
    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long costMillis;

    private String errorMsg;

    private String summary;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
