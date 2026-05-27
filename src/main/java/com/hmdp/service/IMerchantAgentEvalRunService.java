package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentEvalRun;

/**
 * Agent 行为评测运行记录服务。
 */
public interface IMerchantAgentEvalRunService extends IService<AgentEvalRun> {

    Result queryRecentRuns(Integer limit);

    Result queryRunDetail(Long runId);
}
