package com.hmdp.tool;

import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单 Agent 工具。
 *
 * <p>负责查询和汇总店铺券订单数据。统计结果用 Map 返回，是为了方便后续直接序列化给大模型。</p>
 */
@Component
public class OrderAgentTool {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 按券ID集合查询指定时间之后的订单。
     */
    public List<VoucherOrder> queryOrders(List<Long> voucherIds, LocalDateTime startTime) {
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Collections.emptyList();
        }
        return voucherOrderService.query()
                .in("voucher_id", voucherIds)
                .ge("create_time", startTime)
                .list();
    }

    /**
     * 汇总订单表现：订单数、支付数、收入、优惠成本、热门券等。
     */
    public Map<String, Object> buildOrderAnalysis(List<VoucherOrder> orders, Map<Long, Voucher> voucherMap) {
        int total = orders.size();
        int paid = 0;
        int used = 0;
        int pending = 0;
        int refunded = 0;
        long revenue = 0L;
        long discount = 0L;
        Map<Long, Integer> voucherOrderCount = new HashMap<>();

        for (VoucherOrder order : orders) {
            Integer status = order.getStatus();
            if (status != null && status == 1) {
                pending++;
            }
            if (status != null && status == 3) {
                used++;
            }
            if (status != null && (status == 5 || status == 6)) {
                refunded++;
            }
            if (status != null && (status == 2 || status == 3)) {
                paid++;
                Voucher voucher = voucherMap.get(order.getVoucherId());
                if (voucher != null) {
                    revenue += safeLong(voucher.getPayValue());
                    discount += Math.max(0L, safeLong(voucher.getActualValue()) - safeLong(voucher.getPayValue()));
                }
            }
            voucherOrderCount.put(order.getVoucherId(), voucherOrderCount.getOrDefault(order.getVoucherId(), 0) + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOrders", total);
        result.put("paidOrders", paid);
        result.put("usedOrders", used);
        result.put("pendingOrders", pending);
        result.put("refundedOrders", refunded);
        result.put("estimatedRevenue", revenue);
        result.put("estimatedDiscount", discount);
        result.put("averageOrderValue", paid == 0 ? 0 : revenue / paid);
        result.put("conversionRate", total == 0 ? "0.00%" : percent(paid, total));
        result.put("topVoucher", resolveTopVoucher(voucherOrderCount, voucherMap));
        return result;
    }

    private String resolveTopVoucher(Map<Long, Integer> voucherOrderCount, Map<Long, Voucher> voucherMap) {
        Long topVoucherId = null;
        int topCount = 0;
        for (Map.Entry<Long, Integer> entry : voucherOrderCount.entrySet()) {
            if (entry.getValue() > topCount) {
                topVoucherId = entry.getKey();
                topCount = entry.getValue();
            }
        }
        if (topVoucherId == null) {
            return "暂无";
        }
        Voucher voucher = voucherMap.get(topVoucherId);
        return voucher == null ? "未知券" : voucher.getTitle() + "（" + topCount + "单）";
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String percent(int numerator, int denominator) {
        return new BigDecimal(numerator)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }
}
