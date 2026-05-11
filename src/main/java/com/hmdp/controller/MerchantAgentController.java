package com.hmdp.controller;

import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.MerchantOperationReportRequest;
import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.MerchantAgentChatRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMerchantAgentFacadeService;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    @Resource
    private IMerchantAgentKnowledgeDocService merchantAgentKnowledgeDocService;

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
     * 新增 Agent 知识库文档。
     *
     * <p>这是 RAG 的地基接口：先把运营规则、行业案例、风控规则沉淀到 MySQL。
     * 后续向量化时，会从这些启用文档中生成 embedding。</p>
     */
    @PostMapping("/knowledge-docs")
    public Result createKnowledgeDoc(@RequestBody AgentKnowledgeDocRequest request) {
        return merchantAgentKnowledgeDocService.createKnowledgeDoc(request);
    }

    /**
     * 查询知识文档列表，给商家运营后台或调试页面使用。
     */
    @GetMapping("/knowledge-docs")
    public Result queryKnowledgeDocs(@RequestParam(value = "category", required = false) String category,
                                     @RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "status", required = false) Integer status) {
        return merchantAgentKnowledgeDocService.queryKnowledgeDocs(category, keyword, status);
    }

    /**
     * 修改知识文档。
     */
    @PutMapping("/knowledge-docs/{docId}")
    public Result updateKnowledgeDoc(@PathVariable("docId") Long docId,
                                     @RequestBody AgentKnowledgeDocRequest request) {
        return merchantAgentKnowledgeDocService.updateKnowledgeDoc(docId, request);
    }

    /**
     * 停用知识文档。
     *
     * <p>学习阶段先做软停用，不直接删除知识，避免误删后无法复盘 Agent 为什么少了某条规则。</p>
     */
    @PostMapping("/knowledge-docs/{docId}/disable")
    public Result disableKnowledgeDoc(@PathVariable("docId") Long docId) {
        return merchantAgentKnowledgeDocService.disableKnowledgeDoc(docId);
    }

    /**
     * RAG 第一版检索接口。
     *
     * <p>当前使用 MySQL 关键词检索；下一步接向量库时，接口语义可以保持不变，
     * 只替换底层检索实现。</p>
     */
    @GetMapping("/knowledge-docs/search")
    public Result searchKnowledgeDocs(@RequestParam(value = "category", required = false) String category,
                                      @RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "limit", required = false) Integer limit) {
        return merchantAgentKnowledgeDocService.searchKnowledgeDocs(category, keyword, limit);
    }

    /**
     * 上传 txt/md 文件导入知识库。
     *
     * <p>这是 RAG 知识摄入的第一版：只处理纯文本文件，一份文件导入为一条知识文档。
     * 后续支持 PDF/DOCX 时，再增加文档解析、切片和向量化。</p>
     */
    @PostMapping("/knowledge-docs/upload")
    public Result uploadKnowledgeDoc(@RequestParam("category") String category,
                                     @RequestParam(value = "title", required = false) String title,
                                     @RequestParam("file") MultipartFile file) {
        return merchantAgentKnowledgeDocService.uploadKnowledgeDoc(category, title, file);
    }

    /**
     * 向量化单条知识 chunk。
     *
     * <p>这一步会调用 Embedding 模型，把知识正文转成向量，向量本体存 Redis，
     * MySQL 的 vector_id 字段保存 Redis key。</p>
     */
    @PostMapping("/knowledge-docs/{docId}/vectorize")
    public Result vectorizeKnowledgeDoc(@PathVariable("docId") Long docId) {
        return merchantAgentKnowledgeDocService.vectorizeKnowledgeDoc(docId);
    }

    /**
     * 批量向量化启用知识。
     *
     * <p>学习阶段保留 limit 参数，避免一次性调用过多 Embedding API 消耗额度。</p>
     */
    @PostMapping("/knowledge-docs/vectorize")
    public Result vectorizeKnowledgeDocs(@RequestParam(value = "category", required = false) String category,
                                         @RequestParam(value = "limit", required = false) Integer limit) {
        return merchantAgentKnowledgeDocService.vectorizeKnowledgeDocs(category, limit);
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

    /**
     * LangChain4j Tool Calling 学习版对话。
     *
     * <p>这个接口用于学习“模型自己选择工具”的 Agent 流程。
     * 第一版只开放只读工具，不创建优惠券、不修改订单、不改库存。</p>
     */
    @PostMapping("/shops/{shopId}/tool-chat")
    public Result toolChatWithAgent(@PathVariable("shopId") Long shopId,
                                    @RequestBody MerchantAgentChatRequest request) {
        return merchantAgentFacadeService.toolChatWithAgent(shopId, request);
    }

}
