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
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IMerchantAgentFacadeService;
import com.hmdp.service.IMerchantAgentActionLogService;
import com.hmdp.service.IMerchantAgentMessageService;
import com.hmdp.service.IMerchantAgentSessionService;
import com.hmdp.service.IMerchantAgentSuggestionService;
import com.hmdp.service.IMerchantCampaignDraftService;
import com.hmdp.service.IMerchantService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private IShopService shopService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private IBlogService blogService;
    @Resource
    private IBlogCommentsService blogCommentsService;
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
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺");
        }

        DateRange range = resolveDateRange(dateRange);
        List<Voucher> vouchers = voucherService.query().eq("shop_id", shopId).list();
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
        List<VoucherOrder> orders = queryOrders(voucherIds, range.getStartTime());
        Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, voucher -> voucher));
        List<SeckillVoucher> seckillVouchers = querySeckillVouchers(voucherIds);
        List<Blog> blogs = blogService.query().eq("shop_id", shopId).orderByDesc("create_time").list();
        List<BlogComments> comments = queryComments(blogs);

        Map<String, Object> shopProfile = buildShopProfile(shop);
        Map<String, Object> orderAnalysis = buildOrderAnalysis(orders, voucherMap);
        Map<String, Object> voucherAnalysis = buildVoucherAnalysis(vouchers, seckillVouchers);
        Map<String, Object> reviewAnalysis = buildReviewAnalysis(blogs, comments);
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
        if (shopService.getById(shopId) == null) {
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
        if (shopService.getById(shopId) == null) {
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
        Shop shop = shopService.getById(suggestion.getShopId());
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(suggestion.getShopId())) {
            return Result.fail("无权管理该店铺");
        }

        AgentCampaignDraft draft = buildCampaignDraft(suggestion, shop, request);
        campaignDraftService.save(draft);
        agentSuggestionService.updateById(new AgentSuggestion()
                .setId(suggestion.getId())
                .setStatus(2));
        recordAction(suggestion.getSessionId(), suggestion.getShopId(), currentMerchantId(),
                "create_campaign_draft", "draft", draft.getId(), draftToMap(draft));
        return Result.ok(draftToMap(draft));
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

        Voucher voucher = new Voucher()
                .setShopId(draft.getShopId())
                .setTitle(draft.getTitle())
                .setSubTitle(draft.getSubTitle())
                .setRules(draft.getRules())
                .setPayValue(draft.getPayValue())
                .setActualValue(draft.getActualValue())
                .setStatus(1);
        if ("seckill".equals(draft.getDraftType())) {
            voucher.setType(1)
                    .setStock(draft.getStock() == null ? 50 : draft.getStock())
                    .setBeginTime(draft.getBeginTime())
                    .setEndTime(draft.getEndTime());
            voucherService.addSeckillVoucher(voucher);
        } else {
            voucher.setType(0);
            voucherService.save(voucher);
        }

        campaignDraftService.updateById(new AgentCampaignDraft()
                .setId(draft.getId())
                .setStatus(2));
        draft.setStatus(2);
        agentSuggestionService.updateById(new AgentSuggestion()
                .setId(draft.getSuggestionId())
                .setStatus(4));

        Map<String, Object> result = draftToMap(draft);
        result.put("voucherId", voucher.getId());
        result.put("voucherIdText", String.valueOf(voucher.getId()));
        result.put("message", "活动创建成功");
        AgentSuggestion suggestion = agentSuggestionService.getById(draft.getSuggestionId());
        Long sessionId = suggestion == null ? null : suggestion.getSessionId();
        recordAction(sessionId, draft.getShopId(), currentMerchantId(),
                "confirm_campaign_draft", "voucher", voucher.getId(), result);
        return Result.ok(result);
    }

    private List<VoucherOrder> queryOrders(List<Long> voucherIds, LocalDateTime startTime) {
        if (voucherIds.isEmpty()) {
            return Collections.emptyList();
        }
        return voucherOrderService.query()
                .in("voucher_id", voucherIds)
                .ge("create_time", startTime)
                .list();
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

    private AgentCampaignDraft buildCampaignDraft(AgentSuggestion suggestion, Shop shop, MerchantCampaignDraftRequest request) {
        String draftType = normalizeDraftType(request == null ? null : request.getDraftType());
        LocalDateTime beginTime = request != null && request.getBeginTime() != null
                ? request.getBeginTime()
                : LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endTime = request != null && request.getEndTime() != null
                ? request.getEndTime()
                : beginTime.plusDays("seckill".equals(draftType) ? 2 : 30);

        long defaultActualValue = resolveDefaultActualValue(shop);
        long defaultPayValue = "seckill".equals(draftType)
                ? Math.max(100L, Math.round(defaultActualValue * 0.59))
                : Math.max(100L, Math.round(defaultActualValue * 0.80));
        String typeName = "seckill".equals(draftType) ? "秒杀券" : "代金券";

        return new AgentCampaignDraft()
                .setId(nextAgentId())
                .setSuggestionId(suggestion.getId())
                .setShopId(suggestion.getShopId())
                .setDraftType(draftType)
                .setTitle(firstNotBlank(request == null ? null : request.getTitle(), shop.getName() + typeName))
                .setSubTitle(firstNotBlank(request == null ? null : request.getSubTitle(), "Agent推荐活动，适合短期验证转化"))
                .setPayValue(request != null && request.getPayValue() != null ? request.getPayValue() : defaultPayValue)
                .setActualValue(request != null && request.getActualValue() != null ? request.getActualValue() : defaultActualValue)
                .setStock(request != null && request.getStock() != null ? request.getStock() : ("seckill".equals(draftType) ? 80 : null))
                .setBeginTime(beginTime)
                .setEndTime(endTime)
                .setRules(firstNotBlank(request == null ? null : request.getRules(), buildDefaultRules(draftType)))
                .setReason(firstNotBlank(suggestion.getSummary(), suggestion.getContent()))
                .setStatus(1);
    }

    private String normalizeDraftType(String draftType) {
        if ("voucher".equalsIgnoreCase(draftType)) {
            return "voucher";
        }
        return "seckill";
    }

    private long resolveDefaultActualValue(Shop shop) {
        Long avgPrice = shop.getAvgPrice();
        if (avgPrice == null || avgPrice <= 0) {
            return 10000L;
        }
        // 店铺均价字段按“元”保存，优惠券金额字段按“分”保存。
        long avgPriceFen = avgPrice * 100;
        long rounded = Math.round(avgPriceFen / 1000.0) * 1000L;
        return Math.max(3000L, rounded);
    }

    private String buildDefaultRules(String draftType) {
        if ("seckill".equals(draftType)) {
            return "{\"limitPerUser\":1,\"verify\":\"到店出示券码核销\",\"source\":\"agent\"}";
        }
        return "{\"verify\":\"到店出示券码核销\",\"source\":\"agent\"}";
    }

    private String firstNotBlank(String first, String fallback) {
        return first == null || first.trim().isEmpty() ? fallback : first.trim();
    }

    private Map<String, Object> draftToMap(AgentCampaignDraft draft) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", draft.getId());
        row.put("draftId", String.valueOf(draft.getId()));
        row.put("suggestionId", String.valueOf(draft.getSuggestionId()));
        row.put("shopId", draft.getShopId());
        row.put("draftType", draft.getDraftType());
        row.put("draftTypeName", "seckill".equals(draft.getDraftType()) ? "秒杀券草稿" : "普通代金券草稿");
        row.put("title", draft.getTitle());
        row.put("subTitle", draft.getSubTitle());
        row.put("payValue", draft.getPayValue());
        row.put("actualValue", draft.getActualValue());
        row.put("stock", draft.getStock());
        row.put("beginTime", draft.getBeginTime());
        row.put("endTime", draft.getEndTime());
        row.put("rules", draft.getRules());
        row.put("reason", draft.getReason());
        row.put("status", draft.getStatus());
        row.put("statusName", resolveDraftStatusName(draft.getStatus()));
        row.put("createTime", draft.getCreateTime());
        row.put("updateTime", draft.getUpdateTime());
        return row;
    }

    private List<SeckillVoucher> querySeckillVouchers(List<Long> voucherIds) {
        if (voucherIds.isEmpty()) {
            return Collections.emptyList();
        }
        return seckillVoucherService.query().in("voucher_id", voucherIds).list();
    }

    private List<BlogComments> queryComments(List<Blog> blogs) {
        if (blogs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> blogIds = blogs.stream().map(Blog::getId).collect(Collectors.toList());
        return blogCommentsService.query()
                .in("blog_id", blogIds)
                .eq("status", 0)
                .orderByDesc("create_time")
                .list();
    }

    private Map<String, Object> buildShopProfile(Shop shop) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", shop.getName());
        profile.put("typeId", shop.getTypeId());
        profile.put("area", shop.getArea());
        profile.put("address", shop.getAddress());
        profile.put("avgPrice", shop.getAvgPrice());
        profile.put("score", shop.getScore());
        profile.put("sold", shop.getSold());
        profile.put("comments", shop.getComments());
        profile.put("openHours", shop.getOpenHours());
        return profile;
    }

    private Map<String, Object> buildOrderAnalysis(List<VoucherOrder> orders, Map<Long, Voucher> voucherMap) {
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

    private Map<String, Object> buildVoucherAnalysis(List<Voucher> vouchers, List<SeckillVoucher> seckillVouchers) {
        int normal = 0;
        int seckill = 0;
        int online = 0;
        for (Voucher voucher : vouchers) {
            if (voucher.getType() != null && voucher.getType() == 1) {
                seckill++;
            } else {
                normal++;
            }
            if (voucher.getStatus() != null && voucher.getStatus() == 1) {
                online++;
            }
        }

        int seckillStock = 0;
        for (SeckillVoucher seckillVoucher : seckillVouchers) {
            seckillStock += seckillVoucher.getStock() == null ? 0 : seckillVoucher.getStock();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVouchers", vouchers.size());
        result.put("onlineVouchers", online);
        result.put("normalVouchers", normal);
        result.put("seckillVouchers", seckill);
        result.put("seckillStock", seckillStock);
        result.put("hasSeckill", seckill > 0);
        return result;
    }

    private Map<String, Object> buildReviewAnalysis(List<Blog> blogs, List<BlogComments> comments) {
        int liked = 0;
        int blogCommentCount = 0;
        List<String> recentContents = new ArrayList<>();
        for (Blog blog : blogs) {
            liked += blog.getLiked() == null ? 0 : blog.getLiked();
            blogCommentCount += blog.getComments() == null ? 0 : blog.getComments();
            if (recentContents.size() < 3 && blog.getContent() != null) {
                recentContents.add(trim(blog.getContent(), 60));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blogCount", blogs.size());
        result.put("likedCount", liked);
        result.put("commentCount", Math.max(blogCommentCount, comments.size()));
        result.put("recentContents", recentContents);
        result.put("engagementLevel", resolveEngagementLevel(blogs.size(), liked, comments.size()));
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

    private String resolveEngagementLevel(int blogCount, int likedCount, int commentCount) {
        int score = blogCount * 2 + likedCount + commentCount * 2;
        if (score >= 30) {
            return "高";
        }
        if (score >= 10) {
            return "中";
        }
        return "低";
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

    private String resolveDraftStatusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case 1:
                return "待确认";
            case 2:
                return "已创建";
            case 3:
                return "已拒绝";
            case 4:
                return "已过期";
            default:
                return "未知状态";
        }
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

    private String trim(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
