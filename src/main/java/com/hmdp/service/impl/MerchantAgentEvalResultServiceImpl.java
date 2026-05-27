package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentEvalResult;
import com.hmdp.mapper.AgentEvalResultMapper;
import com.hmdp.service.IMerchantAgentEvalResultService;
import org.springframework.stereotype.Service;

/**
 * Agent 行为评测明细服务实现。
 */
@Service
public class MerchantAgentEvalResultServiceImpl
        extends ServiceImpl<AgentEvalResultMapper, AgentEvalResult>
        implements IMerchantAgentEvalResultService {
}
