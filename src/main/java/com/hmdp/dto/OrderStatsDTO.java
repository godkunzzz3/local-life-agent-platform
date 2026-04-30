

package com.hmdp.dto;

import lombok.Data;

    /**
     * 店铺订单统计 DTO。
     *
     * <p>这个 DTO 是 Agent Tool 的稳定输出结构。相比 Map<String, Object>，
     * DTO 字段更清晰，后续接 LangChain4j 或前端展示都更容易维护。</p>
     */
    @Data
    public class OrderStatsDTO {

        /**
         * 周期内券订单总数。
         */
        private Integer totalOrders;

        /**
         * 已支付订单数。
         */
        private Integer paidOrders;

        /**
         * 已核销订单数。
         */
        private Integer usedOrders;

        /**
         * 待支付订单数。
         */
        private Integer pendingOrders;

        /**
         * 退款中或已退款订单数。
         */
        private Integer refundedOrders;

        /**
         * 预计收入，单位分。
         */
        private Long estimatedRevenue;

        /**
         * 预计优惠成本，单位分。
         */
        private Long estimatedDiscount;

        /**
         * 平均客单价，单位分。
         */
        private Long averageOrderValue;

        /**
         * 支付转化率，例如 100.00%。
         */
        private String conversionRate;

        /**
         * 周期内销量最高的券。
         */
        private String topVoucher;
    }



