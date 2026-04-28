package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentSession;
import com.hmdp.mapper.AgentSessionMapper;
import com.hmdp.service.IMerchantAgentSessionService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent 会话服务实现。
 *
 * <p>当前实现继承 ServiceImpl，先提供基础 CRUD。复杂业务不要直接堆在这里，
 * 后续放到 MerchantAgentFacadeServiceImpl 中统一编排。</p>
 */
@Service
public class MerchantAgentSessionServiceImpl extends ServiceImpl<AgentSessionMapper, AgentSession> implements IMerchantAgentSessionService {
}
