package com.hmdp.service.impl;

import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AgentActionLog;
import com.hmdp.entity.AgentCampaignDraft;
import com.hmdp.entity.AgentMessage;
import com.hmdp.entity.AgentSession;
import com.hmdp.entity.AgentSuggestion;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.service.IMerchantAgentFacadeService;
import com.hmdp.service.IMerchantAgentActionLogService;
import com.hmdp.service.IMerchantAgentMessageService;
import com.hmdp.service.IMerchantAgentSessionService;
import com.hmdp.service.IMerchantAgentSuggestionService;
import com.hmdp.service.IMerchantCampaignDraftService;
import com.hmdp.service.IMerchantService;
import com.hmdp.tool.OrderAgentTool;
import com.hmdp.tool.ReviewAgentTool;
import com.hmdp.tool.ShopAgentTool;
import com.hmdp.tool.VoucherAgentTool;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商家运营 Agent 门面服务实现。
 *
 * <p>门面层负责跨表业务编排，例如：创建会话、保存用户消息、调用工具、保存建议、
 * 记录审计日志。当前先注册为 Spring Bean，后续实现运营报告接口时在这里继续扩展。</p>
 */
@Service
public class MerchantAgentFacadeServiceImpl implements IMerchantAgentFacadeService {

    @Resource
    private ShopAgentTool shopAgentTool;
    @Resource
    private OrderAgentTool orderAgentTool;
    @Resource
    private VoucherAgentTool voucherAgentTool;
    @Resource
    private ReviewAgentTool reviewAgentTool;
    @Resource
    private IMerchantAgentSessionService agentSessionService;
    @Resource
    private IMerchantAgentMessageService agentMessageService;
    @Resource
    private IMerchantAgentSuggestionService agentSuggestionService;
    @Resource
    private IMerchantCampaignDraftService campaignDraftService;
    @Resource
    private IMerchantAgentActionLogService agentActionLogService;
    @Resource
    private IMerchantService merchantService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public Result generateOperationReport(Long shopId, String dateRange) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        Shop shop = shopAgentTool.getShop(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺");
        }

        DateRange range = resolveDateRange(dateRange);
        List<Voucher> vouchers = voucherAgentTool.queryShopVouchers(shopId);
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
        List<VoucherOrder> orders = orderAgentTool.queryOrders(voucherIds, range.getStartTime());
        Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, voucher -> voucher));
        List<SeckillVoucher> seckillVouchers = voucherAgentTool.querySeckillVouchers(voucherIds);
        List<Blog> blogs = reviewAgentTool.queryShopBlogs(shopId);
        List<BlogComments> comments = reviewAgentTool.queryComments(blogs);

        Map<String, Object> shopProfile = shopAgentTool.buildShopProfile(shop);
        Map<String, Object> orderAnalysis = orderAgentTool.buildOrderAnalysis(orders, voucherMap);
        Map<String, Object> voucherAnalysis = voucherAgentTool.buildVoucherAnalysis(vouchers, seckillVouchers);
        Map<String, Object> reviewAnalysis = reviewAgentTool.buildReviewAnalysis(blogs, comments);
        List<String> recommendations = buildRecommendations(shop, orders, vouchers, seckillVouchers, blogs, comments);
        String summary = buildSummary(shop, range, orderAnalysis, voucherAnalysis, reviewAnalysis, recommendations);

        Long sessionId = nextAgentId();
        Long merchantId = currentMerchantId();
        AgentSession session = new AgentSession()
                .setId(sessionId)
                .setShopId(shopId)
                .setMerchantId(merchantId)
                .setTitle(shop.getName() + "运营报告")
                .setScene("operation_report")
                .setStatus(2);
        agentSessionService.save(session);

        saveMessage(sessionId, shopId, "user", "生成" + range.getLabel() + "运营报告", null, null, null);
        Long suggestionId = saveOperationSuggestion(sessionId, shopId, summary, recommendations);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("sessionId", String.valueOf(sessionId));
        report.put("suggestionId", String.valueOf(suggestionId));
        report.put("shopId", shopId);
        report.put("dateRange", range.getCode());
        report.put("startTime", range.getStartTime());
        report.put("endTime", range.getEndTime());
        report.put("summary", summary);
        report.put("shopProfile", shopProfile);
        report.put("orderAnalysis", orderAnalysis);
        report.put("voucherAnalysis", voucherAnalysis);
        report.put("reviewAnalysis", reviewAnalysis);
        report.put("recommendations", recommendations);

        saveMessage(sessionId, shopId, "assistant", summary, null, null, toSimpleJson(report));
        recordAction(sessionId, shopId, merchantId, "generate_operation_report", "suggestion", suggestionId, report);
        return Result.ok(report);
    }

    @Override
    public Result queryShopSessions(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (shopAgentTool.getShop(shopId) == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺");
        }

        List<AgentSession> sessions = agentSessionService.query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .last("LIMIT 50")
                .list();
        if (sessions.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> sessionIds = sessions.stream().map(AgentSession::getId).collect(Collectors.toList());
        Map<Long, Integer> messageCountMap = countMessagesBySession(sessionIds);
        Map<Long, AgentSuggestion> latestSuggestionMap = queryLatestSuggestionBySession(sessionIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentSession session : sessions) {
            AgentSuggestion suggestion = latestSuggestionMap.get(session.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            // 雪花 ID 超过 JS 安全整数范围，面向前端的 id 一律补充字符串字段。
            row.put("id", session.getId());
            row.put("sessionId", String.valueOf(session.getId()));
            row.put("shopId", session.getShopId());
            row.put("merchantId", String.valueOf(session.getMerchantId()));
            row.put("title", session.getTitle());
            row.put("scene", session.getScene());
            row.put("sceneName", resolveSceneName(session.getScene()));
            row.put("status", session.getStatus());
            row.put("statusName", resolveSessionStatusName(session.getStatus()));
            row.put("messageCount", messageCountMap.getOrDefault(session.getId(), 0));
            row.put("latestSuggestion", suggestion == null ? "" : suggestion.getSummary());
            row.put("createTime", session.getCreateTime());
            row.put("updateTime", session.getUpdateTime());
            result.add(row);
        }
        return Result.ok(result);
    }

    @Override
    public Result querySessionMessages(Long sessionId) {
        if (sessionId == null) {
            return Result.fail("会话id不能为空");
        }
        AgentSession session = agentSessionService.getById(sessionId);
        if (session == null) {
            return Result.fail("会话不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(session.getShopId())) {
            return Result.fail("无权查看该会话");
        }

        List<AgentMessage> messages = agentMessageService.query()
                .eq("session_id", sessionId)
                .orderByAsc("create_time")
                .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentMessage message : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", message.getId());
            row.put("messageId", String.valueOf(message.getId()));
            row.put("sessionId", String.valueOf(message.getSessionId()));
            row.put("shopId", message.getShopId());
            row.put("role", message.getRole());
            row.put("content", message.getContent());
            row.put("toolName", message.getToolName());
            row.put("toolArgs", message.getToolArgs());
            row.put("toolResult", message.getToolResult());
            row.put("createTime", message.getCreateTime());
            result.add(row);
        }
        return Result.ok(result);
    }

    @Override
    public Result queryShopSuggestions(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (shopAgentTool.getShop(shopId) == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺");
        }

        List<AgentSuggestion> suggestions = agentSuggestionService.query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .last("LIMIT 50")
                .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentSuggestion suggestion : suggestions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", suggestion.getId());
            row.put("suggestionId", String.valueOf(suggestion.getId()));
            row.put("sessionId", String.valueOf(suggestion.getSessionId()));
            row.put("shopId", suggestion.getShopId());
            row.put("type", suggestion.getSuggestionType());
            row.put("typeName", resolveSuggestionTypeName(suggestion.getSuggestionType()));
            row.put("title", suggestion.getTitle());
            row.put("summary", suggestion.getSummary());
            row.put("content", suggestion.getContent());
            row.put("confidenceScore", suggestion.getConfidenceScore());
            row.put("riskLevel", suggestion.getRiskLevel());
            row.put("riskLevelName", resolveRiskLevelName(suggestion.getRiskLevel()));
            row.put("status", suggestion.getStatus());
            row.put("statusName", resolveSuggestionStatusName(suggestion.getStatus()));
            row.put("createTime", suggestion.getCreateTime());
            row.put("updateTime", suggestion.getUpdateTime());
            result.add(row);
        }
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result createCampaignDraft(Long suggestionId, MerchantCampaignDraftRequest request) {
        if (suggestionId == null) {
            return Result.fail("建议id不能为空");
        }
        AgentSuggestion suggestion = agentSuggestionService.getById(suggestionId);
        if (suggestion == null) {
            return Result.fail("Agent建议不存在");
        }
        if (suggestion.getStatus() != null && suggestion.getStatus() == 4) {
            return Result.fail("该建议已经执行，不能重复生成草稿");
        }
        Shop shop = shopAgentTool.getShop(suggestion.getShopId());
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(suggestion.getShopId())) {
            return Result.fail("无权管理该店铺");
        }

        AgentCampaignDraft draft = voucherAgentTool.buildCampaignDraft(suggestion, shop, request, nextAgentId());
        campaignDraftService.save(draft);
        agentSuggestionService.updateById(new AgentSuggestion()
                .setId(suggestion.getId())
                .setStatus(2));
        recordAction(suggestion.getSessionId(), suggestion.getShopId(), currentMerchantId(),
                "create_campaign_draft", "draft", draft.getId(), voucherAgentTool.draftToMap(draft));
        return Result.ok(voucherAgentTool.draftToMap(draft));
    }

    @Override
    @Transactional
    public Result confirmCampaignDraft(Long draftId) {
        if (draftId == null) {
            return Result.fail("草稿id不能为空");
        }
        AgentCampaignDraft draft = campaignDraftService.getById(draftId);
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        if (draft.getStatus() != null && draft.getStatus() != 1) {
            return Result.fail("活动草稿不是待确认状态，不能重复创建");
        }
        if (!merchantService.hasCurrentUserShopPermission(draft.getShopId())) {
            return Result.fail("无权管理该店铺");
        }

        Voucher voucher = voucherAgentTool.createVoucherFromDraft(draft);

        campaignDraftService.updateById(new AgentCampaignDraft()
                .setId(draft.getId())
                .setStatus(2));
        draft.setStatus(2);
        agentSuggestionService.updateById(new AgentSuggestion()
                .setId(draft.getSuggestionId())
                .setStatus(4));

        Map<String, Object> result = voucherAgentTool.draftToMap(draft);
        result.put("voucherId", voucher.getId());
        result.put("voucherIdText", String.valueOf(voucher.getId()));
        result.put("message", "活动创建成功");
        AgentSuggestion suggestion = agentSuggestionService.getById(draft.getSuggestionId());
        Long sessionId = suggestion == null ? null : suggestion.getSessionId();
        recordAction(sessionId, draft.getShopId(), currentMerchantId(),
                "confirm_campaign_draft", "voucher", voucher.getId(), result);
        return Result.ok(result);
    }

    private Map<Long, Integer> countMessagesBySession(List<Long> sessionIds) {
        Map<Long, Integer> result = new HashMap<>();
        List<AgentMessage> messages = agentMessageService.query()
                .in("session_id", sessionIds)
                .list();
        for (AgentMessage message : messages) {
            result.put(message.getSessionId(), result.getOrDefault(message.getSessionId(), 0) + 1);
        }
        return result;
    }

    private Map<Long, AgentSuggestion> queryLatestSuggestionBySession(List<Long> sessionIds) {
        Map<Long, AgentSuggestion> result = new HashMap<>();
        List<AgentSuggestion> suggestions = agentSuggestionService.query()
                .in("session_id", sessionIds)
                .orderByDesc("create_time")
                .list();
        for (AgentSuggestion suggestion : suggestions) {
            // 查询结果已经按创建时间倒序排列，第一次出现的就是该会话最新建议。
            result.putIfAbsent(suggestion.getSessionId(), suggestion);
        }
        return result;
    }

    private List<String> buildRecommendations(Shop shop, List<VoucherOrder> orders, List<Voucher> vouchers,
                                              List<SeckillVoucher> seckillVouchers, List<Blog> blogs,
                                              List<BlogComments> comments) {
        List<String> recommendations = new ArrayList<>();
        if (vouchers.isEmpty()) {
            recommendations.add("当前店铺没有在线优惠券，建议先创建一张低风险普通代金券，用于验证用户转化。");
        }
        if (orders.size() < 10) {
            recommendations.add("近周期券订单量偏少，建议做小库存秒杀券提升曝光，例如晚间或周末时段限量投放。");
        }
        if (seckillVouchers.isEmpty()) {
            recommendations.add("当前缺少秒杀券，可以为高峰时段设计一张限时券，但库存建议先控制在 50-100 张。");
        }
        if (blogs.isEmpty()) {
            recommendations.add("探店内容不足，建议邀请用户发布体验笔记，提升店铺内容可信度。");
        }
        if (comments.isEmpty()) {
            recommendations.add("评论互动数据不足，建议商家主动回复评价，沉淀服务亮点和常见问题。");
        }
        if (shop.getScore() != null && shop.getScore() < 40) {
            recommendations.add("店铺评分偏低，短期不建议大幅降价冲量，应优先处理评价中的服务问题。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("店铺基础运营数据较完整，建议下一步做复购券或会员专享券，提升老客回访。");
        }
        return recommendations;
    }

    private String buildSummary(Shop shop, DateRange range, Map<String, Object> orderAnalysis,
                                Map<String, Object> voucherAnalysis, Map<String, Object> reviewAnalysis,
                                List<String> recommendations) {
        return shop.getName() + "在" + range.getLabel()
                + "内产生券订单" + orderAnalysis.get("totalOrders") + "笔，"
                + "已支付" + orderAnalysis.get("paidOrders") + "笔，"
                + "当前配置优惠券" + voucherAnalysis.get("totalVouchers") + "张，"
                + "探店内容" + reviewAnalysis.get("blogCount") + "篇。"
                + "优先建议：" + recommendations.get(0);
    }

    private Long saveOperationSuggestion(Long sessionId, Long shopId, String summary, List<String> recommendations) {
        AgentSuggestion suggestion = new AgentSuggestion()
                .setId(nextAgentId())
                .setSessionId(sessionId)
                .setShopId(shopId)
                .setSuggestionType("operation")
                .setTitle("店铺运营报告")
                .setSummary(summary)
                .setContent(String.join("\n", recommendations))
                .setConfidenceScore(new BigDecimal("80.00"))
                .setRiskLevel(1)
                .setStatus(1);
        agentSuggestionService.save(suggestion);
        return suggestion.getId();
    }

    private void saveMessage(Long sessionId, Long shopId, String role, String content,
                             String toolName, String toolArgs, String toolResult) {
        AgentMessage message = new AgentMessage()
                .setId(nextAgentId())
                .setSessionId(sessionId)
                .setShopId(shopId)
                .setRole(role)
                .setContent(content)
                .setToolName(toolName)
                .setToolArgs(toolArgs)
                .setToolResult(toolResult);
        agentMessageService.save(message);
    }

    private void recordAction(Long sessionId, Long shopId, Long merchantId, String actionType,
                              String targetType, Long targetId, Map<String, Object> result) {
        AgentActionLog actionLog = new AgentActionLog()
                .setId(nextAgentId())
                .setSessionId(sessionId)
                .setShopId(shopId)
                .setOperatorId(merchantId)
                .setOperatorType("merchant")
                .setActionType(actionType)
                .setTargetType(targetType)
                .setTargetId(targetId)
                .setResultData(toSimpleJson(result))
                .setStatus(1);
        agentActionLogService.save(actionLog);
    }

    private DateRange resolveDateRange(String dateRange) {
        String code = dateRange == null || dateRange.trim().isEmpty() ? "LAST_30_DAYS" : dateRange.trim().toUpperCase();
        LocalDateTime end = LocalDateTime.now();
        switch (code) {
            case "TODAY":
                return new DateRange("TODAY", "今天", LocalDate.now().atStartOfDay(), end);
            case "LAST_7_DAYS":
                return new DateRange("LAST_7_DAYS", "近7天", end.minusDays(7), end);
            case "LAST_30_DAYS":
                return new DateRange("LAST_30_DAYS", "近30天", end.minusDays(30), end);
            default:
                return new DateRange("LAST_30_DAYS", "近30天", end.minusDays(30), end);
        }
    }

    private Long currentMerchantId() {
        UserDTO user = UserHolder.getUser();
        return user == null ? 0L : user.getId();
    }

    private String resolveSceneName(String scene) {
        if ("operation_report".equals(scene)) {
            return "运营报告";
        }
        if ("voucher_plan".equals(scene)) {
            return "优惠券方案";
        }
        if ("review_reply".equals(scene)) {
            return "评价回复";
        }
        return "未知场景";
    }

    private String resolveSessionStatusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case 1:
                return "进行中";
            case 2:
                return "已完成";
            case 3:
                return "已关闭";
            default:
                return "未知状态";
        }
    }

    private String resolveSuggestionTypeName(String type) {
        if ("voucher".equals(type)) {
            return "普通券建议";
        }
        if ("seckill".equals(type)) {
            return "秒杀券建议";
        }
        if ("review".equals(type)) {
            return "评价建议";
        }
        if ("operation".equals(type)) {
            return "运营建议";
        }
        return "未知建议";
    }

    private String resolveRiskLevelName(Integer riskLevel) {
        if (riskLevel == null) {
            return "未知风险";
        }
        switch (riskLevel) {
            case 1:
                return "低风险";
            case 2:
                return "中风险";
            case 3:
                return "高风险";
            default:
                return "未知风险";
        }
    }

    private String resolveSuggestionStatusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case 1:
                return "待确认";
            case 2:
                return "已采纳";
            case 3:
                return "已拒绝";
            case 4:
                return "已执行";
            default:
                return "未知状态";
        }
    }

    private String toSimpleJson(Map<String, Object> data) {
        if (data == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }

    private long nextAgentId() {
        // Agent 模块涉及会话、消息、建议、草稿、审计多张表。统一使用同一个 keyPrefix，
        // 可以避免同一秒内不同业务 key 生成相同数值，前端调试和日志追踪会更直观。
        return redisIdWorker.nextId("agent");
    }

    private static class DateRange {
        private final String code;
        private final String label;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;

        private DateRange(String code, String label, LocalDateTime startTime, LocalDateTime endTime) {
            this.code = code;
            this.label = label;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        private String getCode() {
            return code;
        }

        private String getLabel() {
            return label;
        }

        private LocalDateTime getStartTime() {
            return startTime;
        }

        private LocalDateTime getEndTime() {
            return endTime;
        }
    }
}
