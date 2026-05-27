package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentEvalCaseItemDTO;
import com.hmdp.dto.AgentEvalRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentEvalCase;

import java.util.List;

/**
 * Agent 行为评测用例服务。
 */
public interface IMerchantAgentEvalCaseService extends IService<AgentEvalCase> {

    Result queryEnabledCases();

    Result replaceEnabledCases(AgentEvalRequest request);

    List<AgentEvalCaseItemDTO> listEnabledCaseItems();
}
