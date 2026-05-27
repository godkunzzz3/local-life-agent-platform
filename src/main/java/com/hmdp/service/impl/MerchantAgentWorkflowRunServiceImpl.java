package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentWorkflowRun;
import com.hmdp.mapper.AgentWorkflowRunMapper;
import com.hmdp.service.IMerchantAgentWorkflowRunService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent Workflow Run 服务实现。
 */
@Service
public class MerchantAgentWorkflowRunServiceImpl
        extends ServiceImpl<AgentWorkflowRunMapper, AgentWorkflowRun>
        implements IMerchantAgentWorkflowRunService {
}
