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
     * 是否直接生成活动草稿。
     *
     * <p>为 true 时，活动类问题会直接生成待确认草稿；为空时后端会根据 message 中是否包含
     * “生成、设计、草稿、秒杀券、代金券”等动作词自动判断。</p>
     */
    private Boolean autoDraft;
}
