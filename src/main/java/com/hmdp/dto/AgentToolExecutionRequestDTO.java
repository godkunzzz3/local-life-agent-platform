package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 工具执行请求。
 *
 * <p>当前先覆盖商家运营 Agent 的核心上下文参数。后续如果工具变多，可以继续增加
 * suggestionId、draftId、message 等字段，或者演进为通用 Map 参数。</p>
 */
@Data
@Accessors(chain = true)
public class AgentToolExecutionRequestDTO {

    /**
     * 店铺ID。
     */
    private Long shopId;

    /**
     * 对话意图：order_analysis / voucher_plan / review_analysis / operation_chat。
     */
    private String intent;

    /**
     * 时间范围编码：TODAY / LAST_7_DAYS / LAST_30_DAYS。
     */
    private String dateRange;

    /**
     * 统计开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 统计结束时间。
     */
    private LocalDateTime endTime;
}
