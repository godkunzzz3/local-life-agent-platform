package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 注入 Prompt 的最小 Memory 数据。
 */
@Data
@Accessors(chain = true)
public class AgentMemoryPromptDTO {

    private String memoryType;

    private String memoryKey;

    private String memoryValue;
}
