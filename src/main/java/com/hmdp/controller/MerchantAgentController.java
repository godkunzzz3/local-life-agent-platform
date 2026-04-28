package com.hmdp.controller;

import com.hmdp.dto.MerchantOperationReportRequest;
import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMerchantAgentFacadeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商家运营 Agent 控制器。
 *
 * <p>Controller 只负责 HTTP 参数承接和结果返回。真正的跨表编排、报告生成、
 * 建议落库和审计记录全部下沉到 Facade Service，保持接口层轻薄。</p>
 */
@RestController
@RequestMapping("/merchant-agent")
public class MerchantAgentController {

    @Resource
    private IMerchantAgentFacadeService merchantAgentFacadeService;

    /**
     * 生成店铺运营报告。
     *
     * <p>这是 MerchantOperationAgent 的第一个接口。当前先使用 Java 规则生成报告，
     * 后续接入大模型后，可以把这里查询出来的店铺、订单、优惠券、评价统计作为 Agent 工具结果。</p>
     */
    @PostMapping("/shops/{shopId}/operation-report")
    public Result generateOperationReport(@PathVariable("shopId") Long shopId,
                                           @RequestBody(required = false) MerchantOperationReportRequest request) {
        String dateRange = request == null ? null : request.getDateRange();
        return merchantAgentFacadeService.generateOperationReport(shopId, dateRange);
    }
    /**
     * 查询店铺 Agent 会话列表
     */
    @GetMapping("/shops/{shopId}/sessions")
    public Result getSessionList(@PathVariable("shopId") Long shopId) {
        return merchantAgentFacadeService.queryShopSessions(shopId);
    }

    /**
     * 查询店铺 Agent 会话消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result getSessionMessage(@PathVariable("sessionId") Long sessionId) {
        return merchantAgentFacadeService.querySessionMessages(sessionId);
    }

    /**
     * 查询店铺 Agent 建议
     */
    @GetMapping("/shops/{shopId}/suggestions")
    public Result getShopAgentSuggestion(@PathVariable("shopId") Long shopId) {
        return merchantAgentFacadeService.queryShopSuggestions(shopId);
    }
    /**
     * 生成优惠券、秒杀券草稿
     */
    @PostMapping("/suggestions/{suggestionId}/drafts")
    public Result generateCampaignDraft(@PathVariable("suggestionId") Long suggestionId,
                                        @RequestBody(required = false) MerchantCampaignDraftRequest request) {
        return merchantAgentFacadeService.createCampaignDraft(suggestionId, request);
    }

    /**
     * 商家确认活动草稿后，创建真实优惠券。
     *
     * <p>这是 Agent 模块的关键权限边界：Agent 只能生成草稿，只有商家确认后才允许写入真实业务表。</p>
     */
    @PostMapping("/drafts/{draftId}/confirm")
    public Result confirmCampaignDraft(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.confirmCampaignDraft(draftId);
    }

}
