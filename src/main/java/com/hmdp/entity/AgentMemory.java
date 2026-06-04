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
 * 商家运营 Agent 长期记忆。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_memory")
public class AgentMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long shopId;

    private Long merchantId;

    private String memoryType;

    private String memoryKey;

    private String memoryValue;

    private BigDecimal confidence;

    private String sourceType;

    private Long sourceSessionId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
