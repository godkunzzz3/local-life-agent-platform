package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * Agent 行为评测请求。
 */
@Data
public class AgentEvalRequest {

    /**
     * 自定义临时评测用例。为空时使用持久化用例，持久化为空时使用默认用例。
     */
    private List<AgentEvalCaseItemDTO> cases;
}
