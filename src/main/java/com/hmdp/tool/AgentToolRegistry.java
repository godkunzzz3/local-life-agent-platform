package com.hmdp.tool;

import com.hmdp.dto.AgentToolDefinitionDTO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 工具注册表。
 *
 * <p>Spring 会自动注入所有 AgentToolDescriptor 实现类。后续无论是前端展示、
 * Prompt 拼装，还是 Function Calling 工具注册，都从这里拿统一工具清单。</p>
 */
@Component
public class AgentToolRegistry {

    private final List<AgentToolDescriptor> tools;

    public AgentToolRegistry(List<AgentToolDescriptor> tools) {
        this.tools = tools;
    }

    /**
     * 查询全部工具定义。
     */
    public List<AgentToolDefinitionDTO> listDefinitions() {
        return tools.stream()
                .map(AgentToolDescriptor::definition)
                .sorted(Comparator.comparing(AgentToolDefinitionDTO::getName))
                .collect(Collectors.toList());
    }
}
