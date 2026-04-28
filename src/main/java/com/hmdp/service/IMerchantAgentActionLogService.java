package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.AgentActionLog;

/**
 * 商家运营 Agent 操作审计服务。
 *
 * <p>审计日志后续会被多个业务流程复用，例如创建草稿、商家确认、拒绝建议等，
 * 所以单独保留一个 Service，避免审计逻辑散落在 Controller 中。</p>
 */
public interface IMerchantAgentActionLogService extends IService<AgentActionLog> {
}
