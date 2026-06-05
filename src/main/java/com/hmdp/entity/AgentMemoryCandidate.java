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
 * 商家运营 Agent 候选记忆。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_memory_candidate")
public class AgentMemoryCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long shopId;

    private Long merchantId;

    private Long sessionId;

    private Long sourceMessageId;

    private String candidateType;

    private String memoryKey;

    private String memoryValue;

    private String reason;

    private BigDecimal confidence;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
