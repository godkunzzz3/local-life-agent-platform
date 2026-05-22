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
        return sortedDefinitions(tools);
    }

    /**
     * 查询允许暴露给模型自主选择的工具。
     *
     * <p>这是 Tool Calling 的安全边界之一：不是所有后端工具都能交给模型。
     * 只读工具可以直接暴露；写数据库、需要人工确认的工具必须留在业务流程里由后端控制。</p>
     */
    public List<AgentToolDefinitionDTO> listModelCallableDefinitions() {
        List<AgentToolDescriptor> callableTools = tools.stream()
                .filter(tool -> Boolean.TRUE.equals(tool.definition().getModelCallable()))
                .collect(Collectors.toList());
        return sortedDefinitions(callableTools);
    }

    private List<AgentToolDefinitionDTO> sortedDefinitions(List<AgentToolDescriptor> descriptors) {
        return descriptors.stream()
                .map(AgentToolDescriptor::definition)
                .sorted(Comparator.comparing(AgentToolDefinitionDTO::getName))
                .collect(Collectors.toList());
    }
}
