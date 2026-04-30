package com.hmdp.service.impl;

import com.hmdp.dto.*;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_END_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

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
    @Resource
    private StringRedisTemplate stringRedisTemplate;

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

        ShopProfileDTO shopProfile = shopAgentTool.buildShopProfile(shop);
        OrderStatsDTO orderAnalysis = orderAgentTool.buildOrderAnalysis(orders, voucherMap);
        VoucherStatsDTO voucherAnalysis = voucherAgentTool.buildVoucherAnalysis(vouchers, seckillVouchers);
        ReviewStatsDTO reviewAnalysis = reviewAgentTool.buildReviewAnalysis(blogs, comments);
        List<AgentRecommendationDTO> recommendations = buildRecommendations(shop, orders, vouchers, seckillVouchers, blogs, comments);
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
    public Result queryShopDrafts(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (shopAgentTool.getShop(shopId) == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺");
        }

        List<AgentCampaignDraft> drafts = campaignDraftService.query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .last("LIMIT 50")
                .list();
        if (drafts.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentCampaignDraft draft : drafts) {
            // 复用工具层的 DTO/Map 转换，保证草稿详情和草稿列表返回字段一致。
            result.add(voucherAgentTool.draftToMap(draft));
        }
        return Result.ok(result);
    }

    @Override
    public Result queryCampaignDraftDetail(Long draftId) {
        if (draftId == null) {
            return Result.fail("草稿id不能为空");
        }
        AgentCampaignDraft draft = campaignDraftService.getById(draftId);
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(draft.getShopId())) {
            return Result.fail("无权查看该草稿");
        }

        return Result.ok(voucherAgentTool.draftToMap(draft));
    }

    @Override
    @Transactional
    public Result rejectCampaignDraft(Long draftId) {
        if (draftId == null) {
            return Result.fail("草稿id不能为空");
        }
        AgentCampaignDraft draft = campaignDraftService.getById(draftId);
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        if (draft.getStatus() != null && draft.getStatus() != 1) {
            return Result.fail("只有待确认草稿可以拒绝");
        }
        if (!merchantService.hasCurrentUserShopPermission(draft.getShopId())) {
            return Result.fail("无权管理该草稿");
        }

        AgentCampaignDraft updateDraft = new AgentCampaignDraft()
                .setId(draft.getId())
                .setStatus(3);
        campaignDraftService.updateById(updateDraft);
        draft.setStatus(3);

        Map<String, Object> result = voucherAgentTool.draftToMap(draft);
        result.put("message", "草稿已拒绝");
        AgentSuggestion suggestion = agentSuggestionService.getById(draft.getSuggestionId());
        Long sessionId = suggestion == null ? null : suggestion.getSessionId();
        recordAction(sessionId, draft.getShopId(), currentMerchantId(),
                "reject_campaign_draft", "draft", draft.getId(), result);
        return Result.ok(result);
    }

    @Override
    @Transactional
    public Result updateCampaignDraft(Long draftId, MerchantCampaignDraftRequest request) {
        if (draftId == null) {
            return Result.fail("草稿id不能为空");
        }
        if (request == null) {
            return Result.fail("草稿修改内容不能为空");
        }
        AgentCampaignDraft draft = campaignDraftService.getById(draftId);
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        if (draft.getStatus() != null && draft.getStatus() != 1) {
            return Result.fail("只有待确认草稿可以修改");
        }
        if (!merchantService.hasCurrentUserShopPermission(draft.getShopId())) {
            return Result.fail("无权管理该草稿");
        }
        Result validateResult = validateDraftUpdate(draft, request);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        AgentCampaignDraft updateDraft = buildDraftUpdate(draft, request);
        campaignDraftService.updateById(updateDraft);
        AgentCampaignDraft freshDraft = campaignDraftService.getById(draftId);
        Map<String, Object> result = voucherAgentTool.draftToMap(freshDraft);
        result.put("message", "草稿已更新");
        AgentSuggestion suggestion = agentSuggestionService.getById(freshDraft.getSuggestionId());
        Long sessionId = suggestion == null ? null : suggestion.getSessionId();
        recordAction(sessionId, freshDraft.getShopId(), currentMerchantId(),
                "update_campaign_draft", "draft", freshDraft.getId(), result);
        return Result.ok(result);
    }

    @Override
    public Result queryDraftActions(Long draftId) {
        if (draftId == null) {
            return Result.fail("草稿id不能为空");
        }
        AgentCampaignDraft draft = campaignDraftService.getById(draftId);
        if (draft == null) {
            return Result.fail("活动草稿不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(draft.getShopId())) {
            return Result.fail("无权查看该草稿操作日志");
        }

        List<AgentActionLog> actionLogs = agentActionLogService.query()
                .eq("target_type", "draft")
                .eq("target_id", draftId)
                .orderByDesc("create_time")
                .list();
        return Result.ok(buildActionRows(actionLogs));
    }

    @Override
    public Result queryShopActions(Long shopId) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (shopAgentTool.getShop(shopId) == null) {
            return Result.fail("店铺不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权查看该店铺操作日志");
        }

        List<AgentActionLog> actionLogs = agentActionLogService.query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .last("LIMIT 50")
                .list();
        return Result.ok(buildActionRows(actionLogs));
    }

    private Result validateDraftUpdate(AgentCampaignDraft draft, MerchantCampaignDraftRequest request) {
        Long payValue = request.getPayValue() == null ? draft.getPayValue() : request.getPayValue();
        Long actualValue = request.getActualValue() == null ? draft.getActualValue() : request.getActualValue();
        if (payValue != null && payValue <= 0) {
            return Result.fail("支付金额必须大于0");
        }
        if (actualValue != null && actualValue <= 0) {
            return Result.fail("抵扣金额必须大于0");
        }
        if (payValue != null && actualValue != null && payValue >= actualValue) {
            return Result.fail("支付金额必须小于抵扣金额");
        }

        Integer stock = request.getStock() == null ? draft.getStock() : request.getStock();
        if ("seckill".equals(draft.getDraftType()) && (stock == null || stock <= 0)) {
            return Result.fail("秒杀券库存必须大于0");
        }

        LocalDateTime beginTime = request.getBeginTime() == null ? draft.getBeginTime() : request.getBeginTime();
        LocalDateTime endTime = request.getEndTime() == null ? draft.getEndTime() : request.getEndTime();
        if (beginTime != null && endTime != null && !beginTime.isBefore(endTime)) {
            return Result.fail("活动开始时间必须早于结束时间");
        }
        return Result.ok();
    }

    private AgentCampaignDraft buildDraftUpdate(AgentCampaignDraft draft, MerchantCampaignDraftRequest request) {
        AgentCampaignDraft updateDraft = new AgentCampaignDraft().setId(draft.getId());
        updateDraft.setTitle(request.getTitle());
        updateDraft.setSubTitle(request.getSubTitle());
        updateDraft.setPayValue(request.getPayValue());
        updateDraft.setActualValue(request.getActualValue());
        updateDraft.setStock(request.getStock());
        updateDraft.setBeginTime(request.getBeginTime());
        updateDraft.setEndTime(request.getEndTime());
        updateDraft.setRules(request.getRules());
        return updateDraft;
    }

    /**
     * 确认草稿前做最终业务校验。
     *
     * <p>草稿可以被 Agent 生成，也可以被商家二次编辑，所以真正创建优惠券前必须再校验一次。
     * 这里拦住金额倒挂、活动时间错误、秒杀库存缺失等问题，避免生成无法售卖的真实券。</p>
     */
    private Result validateDraftBeforeConfirm(AgentCampaignDraft draft) {
        String draftType = draft.getDraftType();
        if (!"voucher".equals(draftType) && !"seckill".equals(draftType)) {
            return Result.fail("草稿类型只能是普通代金券或秒杀券");
        }
        if (draft.getTitle() == null || draft.getTitle().trim().isEmpty()) {
            return Result.fail("活动标题不能为空");
        }
        if (draft.getPayValue() == null || draft.getPayValue() <= 0) {
            return Result.fail("支付金额必须大于0");
        }
        if (draft.getActualValue() == null || draft.getActualValue() <= 0) {
            return Result.fail("抵扣金额必须大于0");
        }
        if (draft.getPayValue() >= draft.getActualValue()) {
            return Result.fail("支付金额必须小于抵扣金额");
        }
        if (draft.getBeginTime() == null || draft.getEndTime() == null) {
            return Result.fail("活动开始和结束时间不能为空");
        }
        if (!draft.getBeginTime().isBefore(draft.getEndTime())) {
            return Result.fail("活动开始时间必须早于结束时间");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!draft.getEndTime().isAfter(now)) {
            return Result.fail("活动结束时间必须晚于当前时间");
        }
        if ("seckill".equals(draftType)) {
            if (draft.getStock() == null || draft.getStock() <= 0) {
                return Result.fail("秒杀券库存必须大于0");
            }
            if (!draft.getBeginTime().isAfter(now)) {
                return Result.fail("秒杀券开始时间必须晚于当前时间");
            }
        }
        return Result.ok();
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
        Result validateResult = validateDraftBeforeConfirm(draft);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        Voucher voucher = voucherAgentTool.createVoucherFromDraft(draft);

        campaignDraftService.updateById(new AgentCampaignDraft()
                .setId(draft.getId())
                .setStatus(2));
        draft.setStatus(2);
        agentSuggestionService.updateById(new AgentSuggestion()
                .setId(draft.getSuggestionId())
                .setStatus(4));
        registerSeckillCacheInitAfterCommit(draft, voucher);

        Map<String, Object> result = voucherAgentTool.draftToMap(draft);
        result.put("voucherId", voucher.getId());
        result.put("voucherIdText", String.valueOf(voucher.getId()));
        if ("seckill".equals(draft.getDraftType())) {
            result.put("redisStockKey", SECKILL_STOCK_KEY + voucher.getId());
            result.put("redisBeginKey", SECKILL_BEGIN_KEY + voucher.getId());
            result.put("redisEndKey", SECKILL_END_KEY + voucher.getId());
        }
        result.put("message", "活动创建成功");
        AgentSuggestion suggestion = agentSuggestionService.getById(draft.getSuggestionId());
        Long sessionId = suggestion == null ? null : suggestion.getSessionId();
        recordAction(sessionId, draft.getShopId(), currentMerchantId(),
                "confirm_campaign_draft", "voucher", voucher.getId(), result);
        return Result.ok(result);
    }

    /**
     * 秒杀券确认成功后初始化 Redis 热点数据。
     *
     * <p>这里注册 afterCommit 回调，只有 MySQL 事务提交成功后才写 Redis。
     * 这样能避免“数据库回滚了，但 Redis 已经有库存”的缓存脏数据问题。</p>
     */
    private void registerSeckillCacheInitAfterCommit(AgentCampaignDraft draft, Voucher voucher) {
        if (!"seckill".equals(draft.getDraftType())) {
            return;
        }
        Runnable initCacheTask = () -> initSeckillVoucherRedisCache(voucher.getId(), draft);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    initCacheTask.run();
                }
            });
            return;
        }
        initCacheTask.run();
    }

    /**
     * 写入秒杀 Lua 脚本需要读取的 Redis key。
     */
    private void initSeckillVoucherRedisCache(Long voucherId, AgentCampaignDraft draft) {
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, String.valueOf(draft.getStock()));
        stringRedisTemplate.opsForValue().set(SECKILL_BEGIN_KEY + voucherId, String.valueOf(toEpochMilli(draft.getBeginTime())));
        stringRedisTemplate.opsForValue().set(SECKILL_END_KEY + voucherId, String.valueOf(toEpochMilli(draft.getEndTime())));
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
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

    private List<AgentRecommendationDTO> buildRecommendations(Shop shop, List<VoucherOrder> orders,
                                                              List<Voucher> vouchers,
                                                              List<SeckillVoucher> seckillVouchers,
                                                              List<Blog> blogs,
                                                              List<BlogComments> comments) {
        List<AgentRecommendationDTO> recommendations = new ArrayList<>();
        if (vouchers.isEmpty()) {
            recommendations.add(buildRecommendation(
                    "voucher",
                    "创建低风险普通代金券",
                    "当前店铺没有在线优惠券，缺少基础转化工具。",
                    "建议先创建一张低门槛、低库存的普通代金券，用于验证用户转化。",
                    1,
                    1,
                    "帮助店铺获得第一批券订单数据，方便后续判断活动效果。"
            ));
        }

        if (orders.size() < 10) {
            recommendations.add(buildRecommendation(
                    "seckill",
                    "投放小库存秒杀券",
                    "近周期券订单量偏少，说明当前优惠活动对用户吸引力不足。",
                    "建议选择晚间或周末时段，投放一张小库存秒杀券提升曝光。",
                    1,
                    2,
                    "提升短期点击和下单转化，同时通过小库存控制成本风险。"
            ));
        }

        if (seckillVouchers.isEmpty()) {
            recommendations.add(buildRecommendation(
                    "seckill",
                    "补充限时秒杀活动",
                    "当前店铺缺少秒杀券，无法利用限时优惠制造紧迫感。",
                    "可以为高峰时段设计一张限时券，库存建议先控制在 50-100 张。",
                    2,
                    2,
                    "增强用户下单动力，适合测试店铺在高峰期的活动承接能力。"
            ));
        }

        if (blogs.isEmpty()) {
            recommendations.add(buildRecommendation(
                    "content",
                    "补充探店内容",
                    "当前店铺探店内容不足，用户缺少消费前的参考信息。",
                    "建议邀请用户发布体验笔记，沉淀菜品、环境、服务等真实内容。",
                    2,
                    1,
                    "提升店铺内容可信度，增强新用户到店前的信任感。"
            ));
        }

        if (comments.isEmpty()) {
            recommendations.add(buildRecommendation(
                    "review",
                    "加强评价互动",
                    "当前评论互动数据不足，店铺服务亮点没有被持续沉淀。",
                    "建议商家主动回复评价，总结用户高频关注的问题和服务亮点。",
                    3,
                    1,
                    "提升用户感知到的服务温度，也为后续 Agent 分析提供更多文本数据。"
            ));
        }

        if (shop.getScore() != null && shop.getScore() < 40) {
            recommendations.add(buildRecommendation(
                    "shop",
                    "优先修复口碑问题",
                    "店铺评分偏低，直接放大投流或秒杀可能带来更多负面反馈。",
                    "短期不建议大幅降价冲量，应优先处理评价中的服务和体验问题。",
                    1,
                    3,
                    "降低活动放大负面口碑的风险，先改善基础体验再做增长。"
            ));
        }
        if (recommendations.isEmpty()) {
            recommendations.add(buildRecommendation(
                    "member",
                    "设计复购券或会员专享券",
                    "店铺基础运营数据较完整，可以从拉新转向复购运营。",
                    "建议设计复购券或会员专享券，面向已购买用户做二次触达。",
                    2,
                    1,
                    "提升老客回访率，让优惠从一次性促销变成长期运营工具。"
            ));
        }

        return recommendations;
    }

    private AgentRecommendationDTO buildRecommendation(String type, String title, String reason,
                                                       String action, Integer priority,
                                                       Integer riskLevel, String expectedEffect) {
        AgentRecommendationDTO recommendation = new AgentRecommendationDTO();
        recommendation.setType(type);
        recommendation.setTitle(title);
        recommendation.setReason(reason);
        recommendation.setAction(action);
        recommendation.setPriority(priority);
        recommendation.setRiskLevel(riskLevel);
        recommendation.setExpectedEffect(expectedEffect);
        return recommendation;
    }

    private String buildSummary(Shop shop, DateRange range, OrderStatsDTO orderAnalysis,
                                VoucherStatsDTO voucherAnalysis, ReviewStatsDTO reviewAnalysis,
                                List<AgentRecommendationDTO> recommendations) {
        AgentRecommendationDTO topRecommendation = recommendations.get(0);

        return shop.getName() + "在" + range.getLabel()
                + "内产生券订单" + orderAnalysis.getTotalOrders() + "笔，"
                + "已支付" + orderAnalysis.getPaidOrders() + "笔，"
                + "当前配置优惠券" + voucherAnalysis.getTotalVouchers() + "张，"
                + "探店内容" + reviewAnalysis.getBlogCount() + "篇，"
                + "内容互动等级为" + reviewAnalysis.getEngagementLevel() + "。"
                + "优先建议：" + topRecommendation.getTitle()
                + "，" + topRecommendation.getAction();
    }

    private Long saveOperationSuggestion(Long sessionId, Long shopId, String summary,
                                         List<AgentRecommendationDTO> recommendations) {
        AgentSuggestion suggestion = new AgentSuggestion()
                .setId(nextAgentId())
                .setSessionId(sessionId)
                .setShopId(shopId)
                .setSuggestionType("operation")
                .setTitle("店铺运营报告")
                .setSummary(summary)
                .setContent(buildSuggestionContent(recommendations))
                .setConfidenceScore(new BigDecimal("80.00"))
                .setRiskLevel(resolveOverallRiskLevel(recommendations))
                .setStatus(1);
        agentSuggestionService.save(suggestion);
        return suggestion.getId();
    }

    private Integer resolveOverallRiskLevel(List<AgentRecommendationDTO> recommendations) {
        int maxRiskLevel = 1;
        for (AgentRecommendationDTO recommendation : recommendations) {
            if (recommendation.getRiskLevel() != null && recommendation.getRiskLevel() > maxRiskLevel) {
                maxRiskLevel = recommendation.getRiskLevel();
            }
        }
        return maxRiskLevel;
    }

    private String buildSuggestionContent(List<AgentRecommendationDTO> recommendations) {
        List<String> lines = new ArrayList<>();
        for (AgentRecommendationDTO recommendation : recommendations) {
            lines.add("【" + recommendation.getTitle() + "】"
                    + recommendation.getReason()
                    + " " + recommendation.getAction()
                    + " 预期效果：" + recommendation.getExpectedEffect());
        }
        return String.join("\n", lines);
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

    private List<Map<String, Object>> buildActionRows(List<AgentActionLog> actionLogs) {
        if (actionLogs == null || actionLogs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentActionLog actionLog : actionLogs) {
            Map<String, Object> row = new LinkedHashMap<>();
            // 审计日志中同样存在雪花 ID，给前端补充字符串字段，避免 JS 精度丢失。
            row.put("id", actionLog.getId());
            row.put("actionId", String.valueOf(actionLog.getId()));
            row.put("sessionId", actionLog.getSessionId() == null ? null : String.valueOf(actionLog.getSessionId()));
            row.put("shopId", actionLog.getShopId());
            row.put("operatorId", actionLog.getOperatorId() == null ? null : String.valueOf(actionLog.getOperatorId()));
            row.put("operatorType", actionLog.getOperatorType());
            row.put("operatorTypeName", resolveOperatorTypeName(actionLog.getOperatorType()));
            row.put("actionType", actionLog.getActionType());
            row.put("actionTypeName", resolveActionTypeName(actionLog.getActionType()));
            row.put("targetType", actionLog.getTargetType());
            row.put("targetTypeName", resolveTargetTypeName(actionLog.getTargetType()));
            row.put("targetId", actionLog.getTargetId());
            row.put("targetIdText", actionLog.getTargetId() == null ? null : String.valueOf(actionLog.getTargetId()));
            row.put("requestData", actionLog.getRequestData());
            row.put("resultData", actionLog.getResultData());
            row.put("status", actionLog.getStatus());
            row.put("statusName", resolveActionStatusName(actionLog.getStatus()));
            row.put("errorMsg", actionLog.getErrorMsg());
            row.put("createTime", actionLog.getCreateTime());
            result.add(row);
        }
        return result;
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

    private String resolveOperatorTypeName(String operatorType) {
        if ("merchant".equals(operatorType)) {
            return "商家";
        }
        if ("agent".equals(operatorType)) {
            return "Agent";
        }
        if ("system".equals(operatorType)) {
            return "系统";
        }
        return "未知操作方";
    }

    private String resolveActionTypeName(String actionType) {
        if ("generate_operation_report".equals(actionType)) {
            return "生成运营报告";
        }
        if ("create_campaign_draft".equals(actionType)) {
            return "生成活动草稿";
        }
        if ("update_campaign_draft".equals(actionType)) {
            return "修改活动草稿";
        }
        if ("reject_campaign_draft".equals(actionType)) {
            return "拒绝活动草稿";
        }
        if ("confirm_campaign_draft".equals(actionType)) {
            return "确认创建活动";
        }
        return "未知操作";
    }

    private String resolveTargetTypeName(String targetType) {
        if ("suggestion".equals(targetType)) {
            return "Agent建议";
        }
        if ("draft".equals(targetType)) {
            return "活动草稿";
        }
        if ("voucher".equals(targetType)) {
            return "优惠券";
        }
        if ("seckill".equals(targetType)) {
            return "秒杀券";
        }
        return "未知目标";
    }

    private String resolveActionStatusName(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        switch (status) {
            case 1:
                return "成功";
            case 2:
                return "失败";
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
