package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.AgentSession;

/**
 * 商家运营 Agent 会话服务。
 *
 * <p>IService 提供 MyBatis Plus 标准 CRUD 能力。后续如果要封装“创建会话并写入首条消息”
 * 这类跨表流程，建议放到 IMerchantAgentFacadeService 中，避免单表 Service 过重。</p>
 */
public interface IMerchantAgentSessionService extends IService<AgentSession> {
}
