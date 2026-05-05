package com.hmdp.controller;

import com.hmdp.dto.MerchantOperationReportRequest;
import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.MerchantAgentChatRequest;
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
     * 查询 Agent 工具清单。
     *
     * <p>这个接口先服务于学习和调试：你可以看到当前 Agent 拥有哪些工具、
     * 哪些工具只读、哪些工具会写库、哪些动作必须商家确认。</p>
     */
    @GetMapping("/tools")
    public Result queryAgentTools() {
        return merchantAgentFacadeService.queryAgentTools();
    }

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

    /**
     * 查询当前店铺所有草稿，按创建时间倒序返回。
     */
    @GetMapping("/shops/{shopId}/drafts")
    public Result queryShopDrafts(@PathVariable("shopId") Long shopId) {
        return merchantAgentFacadeService.queryShopDrafts(shopId);
    }

    /**
     * 查询单个活动草稿详情。
     */
    @GetMapping("/drafts/{draftId}")
    public Result queryCampaignDraftDetail(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.queryCampaignDraftDetail(draftId);
    }
    /**
     * 商家觉得 Agent 生成的草稿不合适，可以拒绝。
     *
     *
     */
    @PostMapping("/drafts/{draftId}/reject")
    public Result rejectCampaignDraft(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.rejectCampaignDraft(draftId);
    }

    /**
     * 商家确认前，可以微调草稿，比如标题、金额、库存、活动时间、规则。
     */
    @PutMapping("/drafts/{draftId}")
    public Result updateCampaignDraft(@PathVariable("draftId") Long draftId,
                                      @RequestBody MerchantCampaignDraftRequest request) {
        return merchantAgentFacadeService.updateCampaignDraft(draftId, request);
    }

    /**
     * 查询单个活动草稿的操作日志。
     */
    @GetMapping("/drafts/{draftId}/actions")
    public Result queryDraftActions(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.queryDraftActions(draftId);
    }

    /**
     * 查询活动效果复盘。
     *
     * <p>草稿确认后会创建真实优惠券。这个接口把草稿和真实券订单串起来，
     * 用于判断 Agent 建议是否真的带来了成交和核销。</p>
     */
    @GetMapping("/drafts/{draftId}/effect")
    public Result queryCampaignEffect(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.queryCampaignEffect(draftId);
    }

    /**
     * 基于活动效果生成下一步运营建议。
     *
     * <p>商家看完复盘后，可以让 Agent 继续判断下一步动作。
     * autoDraft=true 时会直接生成待确认草稿，仍然需要商家确认后才创建真实活动。</p>
     */
    @PostMapping("/drafts/{draftId}/effect-suggestion")
    public Result createEffectSuggestion(@PathVariable("draftId") Long draftId,
                                         @RequestParam(value = "autoDraft", required = false) Boolean autoDraft) {
        return merchantAgentFacadeService.createEffectSuggestion(draftId, autoDraft);
    }

    /**
     * 查询店铺维度的 Agent 操作动态。
     */
    @GetMapping("/shops/{shopId}/actions")
    public Result queryShopActions(@PathVariable("shopId") Long shopId) {
        return merchantAgentFacadeService.queryShopActions(shopId);
    }

    /**
     * 商家与运营 Agent 对话。
     *
     * <p>这是从“固定按钮式报告”升级到“可对话 Agent”的入口。Controller 只接收问题，
     * 具体的意图识别、工具调用、消息落库和草稿生成都交给 Facade Service。</p>
     */
    @PostMapping("/shops/{shopId}/chat")
    public Result chatWithAgent(@PathVariable("shopId") Long shopId,
                                @RequestBody MerchantAgentChatRequest request) {
        return merchantAgentFacadeService.chatWithAgent(shopId, request);
    }
}
