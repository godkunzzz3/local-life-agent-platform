package com.hmdp.service;

import com.hmdp.dto.AgentEvalRequest;
import com.hmdp.dto.Result;

/**
 * Agent 行为评测执行服务。
 */
public interface IMerchantAgentEvalService {

    Result evaluateAgent(AgentEvalRequest request);
}
