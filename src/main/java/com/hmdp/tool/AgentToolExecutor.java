package com.hmdp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.AgentRecommendationDTO;
import com.hmdp.dto.AgentToolExecutionRequestDTO;
import com.hmdp.dto.AgentToolExecutionResultDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Agent 工具执行器。
 *
 * <p>工具类本身只关心单个领域的查询和计算；执行器负责把多个工具按业务意图组合起来，
 * 并把调用参数、返回结果、耗时、异常包装成统一结构。后续接入大模型时，可以把这里作为
 * Function Calling 的后端执行入口。</p>
 */
@Component
public class AgentToolExecutor {

    @Resource
    private ShopAgentTool shopAgentTool;
    @Resource
    private OrderAgentTool orderAgentTool;
    @Resource
    private VoucherAgentTool voucherAgentTool;
    @Resource
    private ReviewAgentTool reviewAgentTool;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 包装一次已经完成的工具调用。
     *
     * <p>有些流程已经在 Facade 中完成了多工具编排，为了避免重复查询数据库，
     * 可以把已生成的业务结果交给执行器统一包装，保证消息表和审计日志里的结构一致。</p>
     */
    public AgentToolExecutionResultDTO wrapResult(String toolName, Object args, Object data) {
        long start = System.currentTimeMillis();
        return new AgentToolExecutionResultDTO()
                .setToolName(toolName)
                .setSuccess(true)
                .setToolArgs(toJson(args))
                .setData(data)
                .setCostMillis(System.currentTimeMillis() - start);
    }

    /**
     * 按对话意图执行工具。
     *
     * <p>目前规则版 Agent 的意图有四类：
     * 订单分析、活动规划、评价分析、综合运营咨询。不同意图返回不同的数据切片，
     * 但所有调用都统一包成 AgentToolExecutionResultDTO。</p>
     */
    public AgentToolExecutionResultDTO executeChatTool(Shop shop, AgentToolExecutionRequestDTO request,
                                                       List<AgentRecommendationDTO> recommendations) {
        String intent = request.getIntent();
        String toolName = resolveToolName(intent);
        return execute(toolName, request, () -> {
            Map<String, Object> data = buildBaseResult(shop, request);
            List<Voucher> vouchers = voucherAgentTool.queryShopVouchers(shop.getId());
            List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
            Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, voucher -> voucher));

            if ("order_analysis".equals(intent)) {
                List<VoucherOrder> orders = orderAgentTool.queryOrders(voucherIds, request.getStartTime());
                data.put("orderAnalysis", orderAgentTool.buildOrderAnalysis(orders, voucherMap));
                return data;
            }

            if ("voucher_plan".equals(intent)) {
                List<SeckillVoucher> seckillVouchers = voucherAgentTool.querySeckillVouchers(voucherIds);
                data.put("voucherAnalysis", voucherAgentTool.buildVoucherAnalysis(vouchers, seckillVouchers));
                data.put("recommendations", recommendations == null ? Collections.emptyList() : recommendations);
                return data;
            }

            if ("review_analysis".equals(intent)) {
                List<Blog> blogs = reviewAgentTool.queryShopBlogs(shop.getId());
                List<BlogComments> comments = reviewAgentTool.queryComments(blogs);
                data.put("reviewAnalysis", reviewAgentTool.buildReviewAnalysis(blogs, comments));
                data.put("recommendations", recommendations == null ? Collections.emptyList() : recommendations);
                return data;
            }

            List<VoucherOrder> orders = orderAgentTool.queryOrders(voucherIds, request.getStartTime());
            List<SeckillVoucher> seckillVouchers = voucherAgentTool.querySeckillVouchers(voucherIds);
            List<Blog> blogs = reviewAgentTool.queryShopBlogs(shop.getId());
            List<BlogComments> comments = reviewAgentTool.queryComments(blogs);
            data.put("orderAnalysis", orderAgentTool.buildOrderAnalysis(orders, voucherMap));
            data.put("voucherAnalysis", voucherAgentTool.buildVoucherAnalysis(vouchers, seckillVouchers));
            data.put("reviewAnalysis", reviewAgentTool.buildReviewAnalysis(blogs, comments));
            data.put("recommendations", recommendations == null ? Collections.emptyList() : recommendations);
            return data;
        });
    }

    private AgentToolExecutionResultDTO execute(String toolName, Object args, Supplier<Object> supplier) {
        long start = System.currentTimeMillis();
        AgentToolExecutionResultDTO result = new AgentToolExecutionResultDTO()
                .setToolName(toolName)
                .setToolArgs(toJson(args));
        try {
            Object data = supplier.get();
            return result
                    .setSuccess(true)
                    .setData(data)
                    .setCostMillis(System.currentTimeMillis() - start);
        } catch (Exception e) {
            return result
                    .setSuccess(false)
                    .setErrorMsg(e.getMessage())
                    .setCostMillis(System.currentTimeMillis() - start);
        }
    }

    private Map<String, Object> buildBaseResult(Shop shop, AgentToolExecutionRequestDTO request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dateRange", request.getDateRange());
        data.put("startTime", request.getStartTime());
        data.put("endTime", request.getEndTime());
        data.put("shopProfile", shopAgentTool.buildShopProfile(shop));
        return data;
    }

    private String resolveToolName(String intent) {
        if ("order_analysis".equals(intent)) {
            return "order_analysis_tool";
        }
        if ("voucher_plan".equals(intent)) {
            return "voucher_campaign_tool";
        }
        if ("review_analysis".equals(intent)) {
            return "review_content_tool";
        }
        return "operation_diagnosis_tool";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
