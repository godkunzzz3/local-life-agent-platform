package com.hmdp.dto;

import lombok.Data;

/**
 * Agent Memory 新增/编辑请求。
 */
@Data
public class AgentMemoryRequest {

    private String memoryType;

    private String memoryKey;

    private String memoryValue;

    private Integer status;
}
