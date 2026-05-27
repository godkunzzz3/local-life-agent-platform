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
 * Agent Workflow 单步执行记录。
 *
 * <p>每条 step 记录一次 RAG、意图识别、工具执行、模型回复等节点。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_workflow_step")
public class AgentWorkflowStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long runId;

    private Long sessionId;

    private Long shopId;

    private Integer stepOrder;

    private String stepCode;

    private String stepName;

    private String nodeType;

    private String toolName;

    /**
     * 状态：1成功，2失败，3跳过。
     */
    private Integer status;

    private String inputJson;

    private String outputJson;

    private String detail;

    private String errorMsg;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long costMillis;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
