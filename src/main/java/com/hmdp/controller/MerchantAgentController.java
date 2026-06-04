package com.hmdp.controller;

import com.hmdp.dto.AgentEvalRequest;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.AgentKnowledgeEvaluateRequest;
import com.hmdp.dto.AgentKnowledgeRetrieveRequest;
import com.hmdp.dto.AgentMemoryRequest;
import com.hmdp.dto.MerchantOperationReportRequest;
import com.hmdp.dto.MerchantCampaignDraftRequest;
import com.hmdp.dto.MerchantAgentChatRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMerchantAgentFacadeService;
import com.hmdp.service.IMerchantAgentEvalCaseService;
import com.hmdp.service.IMerchantAgentEvalRunService;
import com.hmdp.service.IMerchantAgentEvalService;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import com.hmdp.service.IMerchantAgentKnowledgeEvalCaseService;
import com.hmdp.service.IMerchantAgentKnowledgeEvalRunService;
import com.hmdp.service.IMerchantAgentMemoryService;
import com.hmdp.service.AgentWorkflowRecorderService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Map;

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
    @Resource
    private IMerchantAgentKnowledgeEvalCaseService merchantAgentKnowledgeEvalCaseService;
    @Resource
    private IMerchantAgentKnowledgeEvalRunService merchantAgentKnowledgeEvalRunService;
    @Resource
    private AgentWorkflowRecorderService agentWorkflowRecorderService;
    @Resource
    private IMerchantAgentEvalCaseService merchantAgentEvalCaseService;
    @Resource
    private IMerchantAgentEvalService merchantAgentEvalService;
    @Resource
    private IMerchantAgentEvalRunService merchantAgentEvalRunService;
    @Resource
    private IMerchantAgentMemoryService merchantAgentMemoryService;

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
     * 查询模型可直接调用的工具清单。
     *
     * <p>这个接口用于学习 Tool Calling 的权限边界：模型只能看到低风险只读工具，
     * 写库工具和需要商家确认的工具仍由后端业务流程控制。</p>
     */
    @GetMapping("/tools/model-callable")
    public Result queryModelCallableAgentTools() {
        return merchantAgentFacadeService.queryModelCallableAgentTools();
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
     * 调试 RAG 召回效果。
     *
     * <p>这个接口不会调用大模型，也不会生成商家回复。它只验证：
     * 当前问题会召回哪些知识、走 semantic_vector 还是关键词兜底、相似度分数是多少。</p>
     */
    @PostMapping("/knowledge-docs/retrieve-debug")
    public Result debugRetrieveKnowledge(@RequestBody AgentKnowledgeRetrieveRequest request) {
        if (request == null) {
            return Result.fail("请输入要调试的商家问题");
        }
        return merchantAgentKnowledgeDocService.debugRetrieveForAgent(
                request.getIntent(), request.getMessage(), request.getLimit());
    }

    /**
     * 批量评测 RAG 召回质量。
     *
     * <p>这个接口用于学习和调试 RAG：批量输入测试问题和期望分类，
     * 后端返回 TopK 是否命中，帮助判断召回策略是否稳定。</p>
     */
    @PostMapping("/knowledge-docs/evaluate")
    public Result evaluateKnowledgeRetrieval(@RequestBody(required = false) AgentKnowledgeEvaluateRequest request) {
        return merchantAgentKnowledgeDocService.evaluateRetrieval(request);
    }

    /**
     * 查询 RAG 召回评测用例。
     *
     * <p>这些用例相当于 Agent 知识库的回归测试集。维护好测试集后，
     * 每次调整知识库、Prompt 或相似度阈值，都能用同一批问题验证召回效果。</p>
     */
    @GetMapping("/knowledge-docs/evaluate-cases")
    public Result queryKnowledgeEvalCases() {
        return merchantAgentKnowledgeEvalCaseService.queryEnabledCases();
    }

    /**
     * 保存 RAG 召回评测用例。
     *
     * <p>学习阶段采用整体替换策略：前端传入当前测试集，后端停用旧用例并写入新用例。
     * 这样比逐条新增/删除更容易理解，也更适合当前单人学习项目。</p>
     */
    @PutMapping("/knowledge-docs/evaluate-cases")
    public Result replaceKnowledgeEvalCases(@RequestBody AgentKnowledgeEvaluateRequest request) {
        return merchantAgentKnowledgeEvalCaseService.replaceEnabledCases(request);
    }

    /**
     * 查询最近的 RAG 召回评测运行记录。
     *
     * <p>评测用例只是“测试题”，运行记录才是每次调参后的“成绩单”。
     * 前端可以用这个接口展示最近几次 Top1/TopK 命中率，帮助判断知识库优化是否有效。</p>
     */
    @GetMapping("/knowledge-docs/evaluate-runs")
    public Result queryKnowledgeEvalRuns(@RequestParam(value = "limit", required = false) Integer limit) {
        return merchantAgentKnowledgeEvalRunService.queryRecentRuns(limit);
    }

    /**
     * 查询单次 RAG 召回评测运行详情。
     *
     * <p>用于从趋势图或历史列表继续下钻：查看每条用例的预期分类、命中分类、
     * Top1 文档和失败原因，帮助定位知识库或召回策略需要优化的位置。</p>
     */
    @GetMapping("/knowledge-docs/evaluate-runs/{runId}")
    public Result queryKnowledgeEvalRunDetail(@PathVariable("runId") Long runId) {
        return merchantAgentKnowledgeEvalRunService.queryRunDetail(runId);
    }

    /**
     * 查询某个店铺最近的 Agent Workflow Run。
     */
    @GetMapping("/workflows/runs")
    public Result queryWorkflowRuns(@RequestParam("shopId") Long shopId) {
        return agentWorkflowRecorderService.queryRuns(shopId);
    }

    /**
     * 查询某次 Agent Workflow Run 的执行步骤。
     */
    @GetMapping("/workflows/runs/{runId}/steps")
    public Result queryWorkflowSteps(@PathVariable("runId") Long runId) {
        return agentWorkflowRecorderService.querySteps(runId);
    }

    /**
     * 查询 Agent 行为评测用例。
     */
    @GetMapping("/eval-cases")
    public Result queryAgentEvalCases() {
        return merchantAgentEvalCaseService.queryEnabledCases();
    }

    /**
     * 整体替换 Agent 行为评测用例。
     */
    @PutMapping("/eval-cases")
    public Result replaceAgentEvalCases(@RequestBody AgentEvalRequest request) {
        return merchantAgentEvalCaseService.replaceEnabledCases(request);
    }

    /**
     * 执行 Agent 行为评测。
     */
    @PostMapping("/evaluate-agent")
    public Result evaluateAgent(@RequestBody(required = false) AgentEvalRequest request) {
        return merchantAgentEvalService.evaluateAgent(request);
    }

    /**
     * 查询最近 Agent 行为评测运行记录。
     */
    @GetMapping("/eval-runs")
    public Result queryAgentEvalRuns(@RequestParam(value = "limit", required = false) Integer limit) {
        return merchantAgentEvalRunService.queryRecentRuns(limit);
    }

    /**
     * 查询单次 Agent 行为评测运行详情。
     */
    @GetMapping("/eval-runs/{runId}")
    public Result queryAgentEvalRunDetail(@PathVariable("runId") Long runId) {
        return merchantAgentEvalRunService.queryRunDetail(runId);
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
     * 重命名历史会话。
     *
     * <p>学习点：重命名只改会话标题，不影响会话下的消息、建议、草稿和审计日志。</p>
     */
    @PutMapping("/sessions/{sessionId}")
    public Result renameSession(@PathVariable("sessionId") Long sessionId,
                                @RequestBody Map<String, String> request) {
        String title = request == null ? null : request.get("title");
        return merchantAgentFacadeService.renameSession(sessionId, title);
    }

    /**
     * 删除历史会话。
     *
     * <p>用于商家工作台清理左侧历史列表。后端只删除会话和消息，不删除建议、
     * 草稿和审计日志，避免清理 UI 时破坏业务追踪链路。</p>
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result deleteSession(@PathVariable("sessionId") Long sessionId) {
        return merchantAgentFacadeService.deleteSession(sessionId);
    }

    /**
     * 查询店铺 Agent 建议
     */
    @GetMapping("/shops/{shopId}/suggestions")
    public Result getShopAgentSuggestion(@PathVariable("shopId") Long shopId) {
        return merchantAgentFacadeService.queryShopSuggestions(shopId);
    }

    /**
     * 删除单条智能行动建议。
     *
     * <p>该接口只删除建议卡片，不会级联删除活动草稿或真实业务数据。</p>
     */
    @DeleteMapping("/suggestions/{suggestionId}")
    public Result deleteSuggestion(@PathVariable("suggestionId") Long suggestionId) {
        return merchantAgentFacadeService.deleteSuggestion(suggestionId);
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
     * 一键清空店铺未创建的活动草稿。
     *
     * <p>已创建真实优惠券的草稿会被跳过，因为它属于 Agent 操作审计链路。</p>
     */
    @DeleteMapping("/shops/{shopId}/drafts")
    public Result clearShopDrafts(@PathVariable("shopId") Long shopId) {
        return merchantAgentFacadeService.clearShopDrafts(shopId);
    }

    /**
     * 查询单个活动草稿详情。
     */
    @GetMapping("/drafts/{draftId}")
    public Result queryCampaignDraftDetail(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.queryCampaignDraftDetail(draftId);
    }

    /**
     * 删除单个未创建真实活动的草稿。
     */
    @DeleteMapping("/drafts/{draftId}")
    public Result deleteCampaignDraft(@PathVariable("draftId") Long draftId) {
        return merchantAgentFacadeService.deleteCampaignDraft(draftId);
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
     * 查询当前店铺的 Agent Memory。
     */
    @GetMapping("/shops/{shopId}/memories")
    public Result queryShopMemories(@PathVariable("shopId") Long shopId,
                                    @RequestParam(value = "status", required = false) Integer status,
                                    @RequestParam(value = "memoryType", required = false) String memoryType) {
        return merchantAgentMemoryService.queryMemories(shopId, status, memoryType);
    }

    /**
     * 新增人工维护的商家偏好 Memory。
     */
    @PostMapping("/shops/{shopId}/memories")
    public Result createShopMemory(@PathVariable("shopId") Long shopId,
                                   @RequestBody AgentMemoryRequest request) {
        return merchantAgentMemoryService.createMemory(shopId, request);
    }

    /**
     * 编辑、启用或禁用 Agent Memory。
     */
    @PutMapping("/memories/{memoryId}")
    public Result updateShopMemory(@PathVariable("memoryId") Long memoryId,
                                   @RequestBody AgentMemoryRequest request) {
        return merchantAgentMemoryService.updateMemory(memoryId, request);
    }

    /**
     * 逻辑删除 Agent Memory，第一版使用禁用实现。
     */
    @DeleteMapping("/memories/{memoryId}")
    public Result deleteShopMemory(@PathVariable("memoryId") Long memoryId) {
        return merchantAgentMemoryService.deleteMemory(memoryId);
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
