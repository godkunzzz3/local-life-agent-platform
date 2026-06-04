package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent Memory 前端展示 DTO。
 */
@Data
@Accessors(chain = true)
public class AgentMemoryDTO {

    private Long memoryId;

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
