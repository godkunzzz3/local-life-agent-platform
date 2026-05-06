package com.hmdp.dto;

import lombok.Data;

/**
 * 商家与运营 Agent 对话请求。
 *
 * <p>当前阶段先做规则版 Agent：后端根据 message 识别意图并调用工具。
 * 后续接入大模型时，可以继续复用这个请求结构，把 message 交给 LLM 做意图识别和回复生成。</p>
 */
@Data
public class MerchantAgentChatRequest {

    /**
     * 商家输入的问题或指令，例如“帮我分析最近7天订单”“设计一张周末秒杀券”。
     */
    private String message;

    /**
     * 统计时间范围：TODAY / LAST_7_DAYS / LAST_30_DAYS。为空时由 Agent 根据问题自动判断。
     */
    private String dateRange;

    /**
     * 当前会话 ID。
     *
     * <p>前端继续追问时会把上一次返回的 sessionId 带回来，后端就可以把新消息追加到
     * 同一条 Agent 会话中。这样既能在页面上保留多轮消息，也能为后续大模型上下文记忆提供数据。</p>
     */
    private String sessionId;

    /**
     * conversationId 是给后续接入标准 Agent/LLM 框架预留的别名。
     *
     * <p>有些框架习惯叫 conversationId，有些业务表叫 sessionId。这里两个字段都支持，
     * 实际处理时优先使用 sessionId。</p>
     */
    private String conversationId;

    /**
     * 是否直接生成活动草稿。
     *
     * <p>为 true 时，活动类问题会直接生成待确认草稿；为空时后端会根据 message 中是否包含
     * “生成、设计、草稿、秒杀券、代金券”等动作词自动判断。</p>
     */
    private Boolean autoDraft;
}
