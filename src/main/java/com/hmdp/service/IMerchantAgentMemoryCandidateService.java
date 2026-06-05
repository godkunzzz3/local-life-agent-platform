package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.AgentMemoryCandidateConfirmRequest;
import com.hmdp.dto.AgentMemoryCandidateGenerateRequest;
import com.hmdp.dto.AgentMemoryCandidateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentMemoryCandidate;

/**
 * 商家运营 Agent 候选记忆服务。
 */
public interface IMerchantAgentMemoryCandidateService extends IService<AgentMemoryCandidate> {

    Result queryCandidates(Long shopId, String status, Integer limit);

    Result generateCandidates(Long shopId, AgentMemoryCandidateGenerateRequest request);

    Result updateCandidate(Long candidateId, AgentMemoryCandidateRequest request);

    Result confirmCandidate(Long candidateId, AgentMemoryCandidateConfirmRequest request);

    Result rejectCandidate(Long candidateId);

    Result deleteCandidate(Long candidateId);
}
