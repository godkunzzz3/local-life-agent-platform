package com.hmdp.agent;

import com.hmdp.dto.AgentModelRequestDTO;
import com.hmdp.dto.AgentModelResponseDTO;

/**
 * 商家运营 Agent 模型客户端。
 *
 * <p>这是业务系统和模型框架之间的隔离层。Facade 只依赖这个接口，
 * 不直接依赖 LangChain4j、OpenAI SDK 或其他模型厂商 SDK。</p>
 */
public interface MerchantAgentModelClient {

    /**
     * 根据 Prompt 上下文和工具结果生成商家运营回复。
     *
     * @param request 模型请求上下文
     * @return 标准模型响应
     */
    AgentModelResponseDTO generateReply(AgentModelRequestDTO request);
}
