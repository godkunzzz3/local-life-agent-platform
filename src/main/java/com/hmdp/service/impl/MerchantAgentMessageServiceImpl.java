package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentMessage;
import com.hmdp.mapper.AgentMessageMapper;
import com.hmdp.service.IMerchantAgentMessageService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent 消息服务实现。
 */
@Service
public class MerchantAgentMessageServiceImpl extends ServiceImpl<AgentMessageMapper, AgentMessage> implements IMerchantAgentMessageService {
}
