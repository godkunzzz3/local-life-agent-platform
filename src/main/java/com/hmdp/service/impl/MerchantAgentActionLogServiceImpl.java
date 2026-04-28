package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentActionLog;
import com.hmdp.mapper.AgentActionLogMapper;
import com.hmdp.service.IMerchantAgentActionLogService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent 操作审计服务实现。
 */
@Service
public class MerchantAgentActionLogServiceImpl extends ServiceImpl<AgentActionLogMapper, AgentActionLog> implements IMerchantAgentActionLogService {
}
