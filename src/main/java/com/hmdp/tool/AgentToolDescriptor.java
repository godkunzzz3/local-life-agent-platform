package com.hmdp.tool;

import com.hmdp.dto.AgentToolDefinitionDTO;

/**
 * Agent 工具描述接口。
 *
 * <p>所有可被 Agent 编排的工具都实现这个接口，统一暴露工具元信息。
 * 这样 Facade 或后续的大模型编排层不需要硬编码每个工具的说明。</p>
 */
public interface AgentToolDescriptor {

    /**
     * 返回当前工具的标准化定义。
     */
    AgentToolDefinitionDTO definition();
}
