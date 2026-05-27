package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentWorkflowStep;
import com.hmdp.mapper.AgentWorkflowStepMapper;
import com.hmdp.service.IMerchantAgentWorkflowStepService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent Workflow Step 服务实现。
 */
@Service
public class MerchantAgentWorkflowStepServiceImpl
        extends ServiceImpl<AgentWorkflowStepMapper, AgentWorkflowStep>
        implements IMerchantAgentWorkflowStepService {
}
