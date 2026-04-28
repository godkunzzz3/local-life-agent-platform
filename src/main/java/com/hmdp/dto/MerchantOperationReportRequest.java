package com.hmdp.dto;

import lombok.Data;

/**
 * 商家运营报告请求参数。
 *
 * <p>第一版只接收时间范围，后续可以继续扩展运营目标，例如拉新、复购、工作日引流等。
 * Controller 使用 DTO 接参，避免接口参数越来越多时方法签名失控。</p>
 */
@Data
public class MerchantOperationReportRequest {

    /**
     * 统计时间范围：TODAY / LAST_7_DAYS / LAST_30_DAYS。为空时默认 LAST_30_DAYS。
     */
    private String dateRange;
}
