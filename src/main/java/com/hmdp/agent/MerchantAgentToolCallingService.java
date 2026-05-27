package com.hmdp.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.AgentFlowStepDTO;
import com.hmdp.dto.AgentToolDefinitionDTO;
import com.hmdp.dto.MerchantAgentChatRequest;
import com.hmdp.entity.AgentMessage;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import com.hmdp.tool.OrderAgentTool;
import com.hmdp.tool.ReviewAgentTool;
import com.hmdp.tool.ShopAgentTool;
import com.hmdp.tool.AgentToolRegistry;
import com.hmdp.tool.VoucherAgentTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LangChain4j Tool Calling 学习版编排服务。
 *
 * <p>这个类专门用于学习“模型选择工具”的流程，不替换现有 /chat 主链路。
 * 第一版只开放只读工具：店铺信息、订单统计、优惠券列表、评价摘要。
 * 写操作仍然走草稿 + 商家确认，不能直接暴露给模型调用。</p>
 */
@Component
public class MerchantAgentToolCallingService {

    private static final String TOOL_GET_SHOP_PROFILE = "getShopProfile";
    private static final String TOOL_GET_ORDER_STATS = "getShopOrderStats";
    private static final String TOOL_GET_VOUCHERS = "getShopVouchers";
    private static final String TOOL_GET_REVIEW_SUMMARY = "getShopReviewSummary";
    private static final String TOOL_GET_OPERATION_DIAGNOSIS = "getOperationDiagnosis";

    @Value("${merchant-agent.model.api-key:}")
    private String apiKey;

    @Value("${merchant-agent.model.base-url:}")
    private String baseUrl;

    @Value("${merchant-agent.model.model-name:qwen-turbo}")
    private String modelName;

    @Value("${merchant-agent.model.temperature:0.3}")
    private Float temperature;

    @Value("${merchant-agent.model.max-tokens:800}")
    private Integer maxTokens;

    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ShopAgentTool shopAgentTool;
    @Resource
    private OrderAgentTool orderAgentTool;
    @Resource
    private VoucherAgentTool voucherAgentTool;
    @Resource
    private ReviewAgentTool reviewAgentTool;
    @Resource
    private AgentToolRegistry agentToolRegistry;
    @Resource
    private MerchantAgentPromptTemplateService promptTemplateService;
    @Resource
    private IMerchantAgentKnowledgeDocService agentKnowledgeDocService;
    @Resource
    private MerchantAgentRulePolicyService rulePolicyService;

    /**
     * 执行一次 Tool Calling 对话。
     *
     * <p>流程分两轮：
     * 第一轮把工具说明书交给模型，让模型选择要调用的工具；
     * Java 后端执行工具后，第二轮把工具结果交回模型生成最终回复。</p>
     */
    public Map<String, Object> chat(Shop shop, MerchantAgentChatRequest request) {
        return chat(shop, request, new ArrayList<>());
    }

    /**
     * 带历史消息的 Tool Calling 对话。
     *
     * <p>历史消息只取最近几条 user/assistant 自然语言内容，不把 tool 结果全文塞给模型。
     * 这样可以让模型理解“继续、刚才、它”这类上下文，同时避免工具 JSON 过长导致 prompt 膨胀。</p>
     */
    public Map<String, Object> chat(Shop shop, MerchantAgentChatRequest request, List<AgentMessage> historyMessages) {
        if (isBlank(apiKey)) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，Tool Calling 学习接口需要真实模型支持。");
        }

        long start = System.currentTimeMillis();
        String userMessage = request.getMessage().trim();
        String intent = resolveToolCallingIntent(userMessage);
        List<AgentFlowStepDTO> flowTrace = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();
        List<Map<String, Object>> ragKnowledge = retrieveToolCallingRagKnowledge(intent, userMessage);
        String ragRetrievalMode = resolveRagRetrievalMode(ragKnowledge);
        Map<String, Object> promptContext = buildPromptContext(shop, userMessage, intent, ragRetrievalMode, ragKnowledge);

        messages.add(SystemMessage.from(buildSystemPrompt(shop, userMessage, ragKnowledge)));
        appendHistoryMessages(messages, historyMessages);
        messages.add(UserMessage.from(userMessage));
        flowTrace.add(flow("receive_message", "接收商家问题", "success",
                "收到商家输入：" + userMessage, null, null));
        flowTrace.add(flow("retrieve_knowledge", "检索运营知识", "success",
                ragKnowledge.isEmpty()
                        ? "Tool Calling 本轮未召回知识文档，将只基于工具数据回答"
                        : "Tool Calling 已召回 " + ragKnowledge.size() + " 条运营知识",
                "knowledge_retrieval", null));
        if (isProhibitedOperation(userMessage)) {
            flowTrace.add(flow("guardrail_check", "高风险动作拦截", "success",
                    "识别到退款、删除、支付核销状态修改或群发等高风险动作，已拒绝直接执行", null, null));
            Map<String, Object> result = buildResult("这个动作属于高风险操作，我不能直接执行。退款、取消订单、修改支付/核销状态、删除活动或群发消息都需要人工审核流程；我可以先帮你分析数据并生成待确认方案。",
                    toolCalls, flowTrace, promptContext, start);
            result.put("provider", "policy_guard");
            result.put("modelName", "merchant-agent-guardrail");
            result.put("guardrail", true);
            return result;
        }

        ChatModel chatModel = buildChatModel();
        ChatRequest.Builder firstRequestBuilder = ChatRequest.builder().messages(messages);
        if (!"off_topic".equals(intent)) {
            firstRequestBuilder.toolSpecifications(toolSpecifications())
                    .toolChoice(ToolChoice.AUTO);
        }
        ChatRequest firstRequest = firstRequestBuilder.build();
        ChatResponse firstResponse = chatModel.chat(firstRequest);
        AiMessage aiMessage = firstResponse.aiMessage();

        if (aiMessage == null || !aiMessage.hasToolExecutionRequests()) {
            String answer = aiMessage == null ? "模型未返回有效回复。" : sanitizeMerchantReply(aiMessage.text());
            flowTrace.add(flow("select_tool", "模型选择工具", "skipped",
                    "模型没有请求工具调用，直接生成回复", null, null));
            return buildResult(answer, toolCalls, flowTrace, promptContext, start);
        }

        messages.add(aiMessage);
        flowTrace.add(flow("select_tool", "模型选择工具", "success",
                "模型请求调用 " + aiMessage.toolExecutionRequests().size() + " 个工具", null, null));

        for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
            long toolStart = System.currentTimeMillis();
            Object toolResult;
            try {
                toolResult = executeReadonlyTool(shop, toolRequest);
            } catch (Exception e) {
                toolResult = toolError("工具执行失败：" + e.getMessage());
            }
            long costMillis = System.currentTimeMillis() - toolStart;
            String toolResultJson = toJson(toolResult);
            boolean toolSuccess = !isToolError(toolResult);

            Map<String, Object> callRow = new LinkedHashMap<>();
            callRow.put("toolName", toolRequest.name());
            callRow.put("arguments", parseJsonMap(toolRequest.arguments()));
            callRow.put("success", toolSuccess);
            callRow.put("result", toolResult);
            callRow.put("costMillis", costMillis);
            toolCalls.add(callRow);

            messages.add(ToolExecutionResultMessage.from(toolRequest, toolResultJson));
            flowTrace.add(flow("execute_tool", "执行只读工具", toolSuccess ? "success" : "failed",
                    toolSuccess ? "已执行工具：" + toolRequest.name() : String.valueOf(((Map<?, ?>) toolResult).get("error")),
                    toolRequest.name(), costMillis));
        }

        messages.add(UserMessage.from(buildFinalAnswerInstruction(ragKnowledge)));
        ChatRequest finalRequest = ChatRequest.builder()
                .messages(messages)
                .build();
        ChatResponse finalResponse = chatModel.chat(finalRequest);
        String answer = finalResponse.aiMessage() == null ? "模型未返回最终回复。" : sanitizeMerchantReply(finalResponse.aiMessage().text());
        flowTrace.add(flow("generate_reply", "生成最终回复", "success",
                "模型已基于工具结果生成最终回复", null, null));
        return buildResult(answer, toolCalls, flowTrace, promptContext, start);
    }

    private void appendHistoryMessages(List<ChatMessage> messages, List<AgentMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return;
        }
        int fromIndex = Math.max(0, historyMessages.size() - 6);
        for (AgentMessage history : historyMessages.subList(fromIndex, historyMessages.size())) {
            if (history == null || isBlank(history.getContent())) {
                continue;
            }
            if ("user".equals(history.getRole())) {
                messages.add(UserMessage.from(history.getContent()));
            } else if ("assistant".equals(history.getRole())) {
                messages.add(AiMessage.from(history.getContent()));
            }
        }
    }

    private ChatModel buildChatModel() {
        QwenChatModel.QwenChatModelBuilder builder = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens);
        if (!isBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private String buildSystemPrompt(Shop shop, String userMessage, List<Map<String, Object>> ragKnowledge) {
        return promptTemplateService.toolCallingFrame()
                .replace("{{systemPrompt}}", promptTemplateService.systemPrompt())
                .replace("{{behaviorBoundary}}", promptTemplateService.behaviorBoundary())
                .replace("{{shopId}}", String.valueOf(shop.getId()))
                .replace("{{shopName}}", shop.getName())
                .replace("{{userMessage}}", userMessage)
                .replace("{{ragKnowledge}}", buildRagPromptSection(ragKnowledge));
    }

    /**
     * 构建暴露给大模型的 LangChain4j Tool 列表。
     *
     * <p>学习重点：这里不再手写固定工具数组，而是读取 AgentToolRegistry。
     * 这样新增只读工具时，只要工具定义里标记 modelCallable=true，
     * 模型可见工具列表就会自动更新；草稿/执行类写工具则会被注册表过滤掉。</p>
     */
    private List<ToolSpecification> toolSpecifications() {
        List<ToolSpecification> specifications = new ArrayList<>();
        for (AgentToolDefinitionDTO definition : agentToolRegistry.listModelCallableDefinitions()) {
            String modelToolName = resolveModelToolName(definition);
            ToolSpecification specification = ToolSpecification.builder()
                    .name(modelToolName)
                    .description(definition.getDescription())
                    .parameters(toolParameterSchema(modelToolName))
                    .build();
            specifications.add(specification);
        }
        return specifications;
    }

    private JsonObjectSchema baseShopIdSchema() {
        return JsonObjectSchema.builder()
                .addIntegerProperty("shopId", "店铺ID，必须使用当前店铺ID")
                .required("shopId")
                .additionalProperties(false)
                .build();
    }

    /**
     * 根据模型函数名生成参数 schema。
     *
     * <p>不同工具需要的参数不一样：店铺画像只需要 shopId，
     * 订单统计和综合诊断还需要 dateRange。schema 写清楚后，
     * 大模型才知道调用工具时应该传哪些参数。</p>
     */
    private JsonObjectSchema toolParameterSchema(String modelToolName) {
        if (TOOL_GET_ORDER_STATS.equals(modelToolName) || TOOL_GET_OPERATION_DIAGNOSIS.equals(modelToolName)) {
            return JsonObjectSchema.builder()
                    .addIntegerProperty("shopId", "店铺ID，必须使用当前店铺ID")
                    .addEnumProperty("dateRange", Arrays.asList("TODAY", "LAST_7_DAYS", "LAST_30_DAYS"),
                            "统计范围，默认 LAST_7_DAYS")
                    .required("shopId")
                    .additionalProperties(false)
                    .build();
        }
        return baseShopIdSchema();
    }

    /**
     * 解析模型侧工具名。
     *
     * <p>内部工具名用于后端审计，例如 order_analysis_tool；
     * 模型工具名用于 Tool Calling，例如 getShopOrderStats。
     * 两者分开后，既方便后端排查日志，也能让模型看到更自然的函数名。</p>
     */
    private String resolveModelToolName(AgentToolDefinitionDTO definition) {
        if (definition == null) {
            return "";
        }
        if (!isBlank(definition.getModelToolName())) {
            return definition.getModelToolName();
        }
        return definition.getName();
    }

    private Object executeReadonlyTool(Shop shop, ToolExecutionRequest toolRequest) {
        Map<String, Object> args = parseJsonMap(toolRequest.arguments());
        Long requestedShopId = toLong(args.get("shopId"));
        if (requestedShopId != null && !shop.getId().equals(requestedShopId)) {
            return toolError("工具只能查询当前店铺数据，不能跨店铺调用。当前店铺ID：" + shop.getId());
        }

        String toolName = toolRequest.name();
        if (TOOL_GET_SHOP_PROFILE.equals(toolName)) {
            return shopAgentTool.buildShopProfile(shop);
        }
        if (TOOL_GET_ORDER_STATS.equals(toolName)) {
            DateRange range = resolveDateRange(String.valueOf(args.getOrDefault("dateRange", "LAST_7_DAYS")));
            List<Voucher> vouchers = voucherAgentTool.queryShopVouchers(shop.getId());
            List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
            Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, voucher -> voucher));
            List<VoucherOrder> orders = orderAgentTool.queryOrders(voucherIds, range.startTime);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dateRange", range.code);
            result.put("orderAnalysis", orderAgentTool.buildOrderAnalysis(orders, voucherMap));
            return result;
        }
        if (TOOL_GET_VOUCHERS.equals(toolName)) {
            List<Voucher> vouchers = voucherAgentTool.queryShopVouchers(shop.getId());
            List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
            List<SeckillVoucher> seckillVouchers = voucherAgentTool.querySeckillVouchers(voucherIds);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("voucherAnalysis", voucherAgentTool.buildVoucherAnalysis(vouchers, seckillVouchers));
            result.put("vouchers", vouchers);
            return result;
        }
        if (TOOL_GET_REVIEW_SUMMARY.equals(toolName)) {
            List<Blog> blogs = reviewAgentTool.queryShopBlogs(shop.getId());
            List<BlogComments> comments = reviewAgentTool.queryComments(blogs);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reviewAnalysis", reviewAgentTool.buildReviewAnalysis(blogs, comments));
            return result;
        }
        if (TOOL_GET_OPERATION_DIAGNOSIS.equals(toolName)) {
            // 综合诊断工具是只读组合工具：一次性读取店铺、订单、优惠券和评价上下文，
            // 适合“分析经营情况”这类宽泛问题，避免模型连续调用多个小工具导致链路过长。
            DateRange range = resolveDateRange(String.valueOf(args.getOrDefault("dateRange", "LAST_7_DAYS")));
            List<Voucher> vouchers = voucherAgentTool.queryShopVouchers(shop.getId());
            List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());
            Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, voucher -> voucher));
            List<VoucherOrder> orders = orderAgentTool.queryOrders(voucherIds, range.startTime);
            List<SeckillVoucher> seckillVouchers = voucherAgentTool.querySeckillVouchers(voucherIds);
            List<Blog> blogs = reviewAgentTool.queryShopBlogs(shop.getId());
            List<BlogComments> comments = reviewAgentTool.queryComments(blogs);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dateRange", range.code);
            result.put("shopProfile", shopAgentTool.buildShopProfile(shop));
            result.put("orderAnalysis", orderAgentTool.buildOrderAnalysis(orders, voucherMap));
            result.put("voucherAnalysis", voucherAgentTool.buildVoucherAnalysis(vouchers, seckillVouchers));
            result.put("reviewAnalysis", reviewAgentTool.buildReviewAnalysis(blogs, comments));
            return result;
        }
        return toolError("不支持的工具：" + toolName);
    }

    private Map<String, Object> buildResult(String answer, List<Map<String, Object>> toolCalls,
                                            List<AgentFlowStepDTO> flowTrace,
                                            Map<String, Object> promptContext,
                                            long start) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("reply", answer);
        result.put("provider", "langchain4j");
        result.put("modelName", modelName);
        result.put("promptVersion", promptTemplateService.promptVersion());
        result.put("toolCalling", true);
        result.put("toolCalls", toolCalls);
        result.put("calledTools", toolCalls.stream().map(item -> item.get("toolName")).collect(Collectors.toList()));
        result.put("promptContext", promptContext);
        result.put("flowTrace", flowTrace);
        result.put("costMillis", System.currentTimeMillis() - start);
        return result;
    }

    private Map<String, Object> buildPromptContext(Shop shop, String userMessage, String intent,
                                                   String ragRetrievalMode, List<Map<String, Object>> ragKnowledge) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("scene", "merchant_tool_calling_chat");
        context.put("shopId", shop.getId());
        context.put("shopName", shop.getName());
        context.put("userMessage", userMessage);
        context.put("intent", intent);
        context.put("promptVersion", promptTemplateService.promptVersion());
        context.put("ragRetrievalMode", ragRetrievalMode);
        context.put("ragKnowledge", ragKnowledge);
        context.put("constraints", Arrays.asList(
                "Tool Calling 模式由模型决定调用哪个只读工具",
                "RAG 知识只提供运营规则背景，实时经营数据必须来自工具调用",
                "涉及创建真实活动时只能提示生成草稿和商家确认，不能直接执行"
        ));
        return context;
    }

    private List<Map<String, Object>> retrieveToolCallingRagKnowledge(String intent, String userMessage) {
        if ("off_topic".equals(intent) || isProhibitedOperation(userMessage)) {
            return new ArrayList<>();
        }
        try {
            return agentKnowledgeDocService.retrieveForAgent(intent, userMessage, 3);
        } catch (Exception e) {
            // RAG 是增强链路，检索失败时不能阻断 Tool Calling 主流程。
            // 生产环境可在这里补充告警日志，学习阶段先降级为空知识。
            return new ArrayList<>();
        }
    }

    private String resolveRagRetrievalMode(List<Map<String, Object>> ragKnowledge) {
        if (ragKnowledge == null || ragKnowledge.isEmpty()) {
            return "skipped_or_no_hit";
        }
        Object mode = ragKnowledge.get(0).get("retrievalMode");
        return mode == null ? "unknown" : String.valueOf(mode);
    }

    private String resolveToolCallingIntent(String userMessage) {
        if (!isBusinessRelatedQuestion(userMessage)) {
            return "off_topic";
        }
        if (containsAny(userMessage, "秒杀", "优惠券", "代金券", "活动", "草稿")) {
            return "voucher_plan";
        }
        if (containsAny(userMessage, "评价", "评论", "口碑", "探店")) {
            return "review_analysis";
        }
        if (containsAny(userMessage, "订单", "收入", "营收", "成本", "利润", "转化", "店铺", "经营", "数据", "分析")) {
            return "order_analysis";
        }
        return "operation_chat";
    }

    private boolean isBusinessRelatedQuestion(String userMessage) {
        return containsAny(userMessage,
                "店铺", "经营", "订单", "数据", "分析", "收入", "营收", "成本", "利润", "转化",
                "优惠券", "代金券", "秒杀", "活动", "草稿", "评价", "评论", "口碑", "探店", "用户", "客单价");
    }

    private boolean isProhibitedOperation(String userMessage) {
        return rulePolicyService.isProhibitedOperation(userMessage);
    }

    private String buildRagPromptSection(List<Map<String, Object>> ragKnowledge) {
        if (ragKnowledge == null || ragKnowledge.isEmpty()) {
            return "本轮未召回知识库内容，请只基于工具返回的数据回答。";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (Map<String, Object> doc : ragKnowledge) {
            builder.append(index++)
                    .append(". ")
                    .append(String.valueOf(doc.getOrDefault("title", "未命名知识")));
            Object score = doc.get("similarityScore");
            if (score != null) {
                builder.append("，相似度=").append(score);
            }
            builder.append("，检索模式=")
                    .append(String.valueOf(doc.getOrDefault("retrievalMode", "unknown")))
                    .append("\n")
                    .append("   规则摘要：")
                    .append(shortText(String.valueOf(doc.getOrDefault("content", "")), 260))
                    .append("\n");
        }
        return builder.toString();
    }

    private String buildFinalAnswerInstruction(List<Map<String, Object>> ragKnowledge) {
        String knowledgeRequirement = ragKnowledge == null || ragKnowledge.isEmpty()
                ? "本轮没有知识库依据，必须只基于工具结果回答。"
                : "本轮已经召回运营知识，请把它转化成自然建议，但不要暴露“知识库、规则、检索、召回、依据”等内部来源词。";
        return "请现在生成最终回复。面向商家的回复中禁止出现内部工具名、函数名、tool calling、getShopOrderStats、getShopProfile、getShopVouchers、getShopReviewSummary，也不要说“我调用了某工具”。"
                + knowledgeRequirement
                + "回复要像专业运营顾问，先给结论，再说明关键原因，最后给一个可执行动作；不要使用固定标题“数据判断、知识依据、下一步动作”。";
    }

    private String sanitizeMerchantReply(String reply) {
        if (reply == null) {
            return "";
        }
        return reply
                .replace("getShopOrderStats", "订单统计")
                .replace("getShopProfile", "店铺信息")
                .replace("getShopVouchers", "优惠券数据")
                .replace("getShopReviewSummary", "评价摘要")
                .replace("Tool Calling", "智能分析")
                .replace("tool calling", "智能分析")
                .replace("调用了订单统计工具，", "")
                .replace("调用了订单统计工具", "")
                .replace("调用了工具，", "")
                .replace("调用了工具", "")
                .replace("我调用了", "我已查看")
                .replace("知识依据：", "")
                .replace("知识依据:", "")
                .replace("依据知识库规则，", "")
                .replace("依据知识库规则", "")
                .replace("根据知识库规则，", "")
                .replace("根据知识库规则", "")
                .replace("根据知识库，", "")
                .replace("根据知识库", "")
                .replace("知识库规则", "运营经验")
                .replace("数据判断：", "")
                .replace("数据判断:", "")
                .replace("下一步动作：", "")
                .replace("下一步动作:", "")
                .trim();
    }

    private Map<String, Object> toolError(String errorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", errorMessage);
        return result;
    }

    private boolean isToolError(Object toolResult) {
        return toolResult instanceof Map && Boolean.FALSE.equals(((Map<?, ?>) toolResult).get("success"));
    }

    private DateRange resolveDateRange(String value) {
        String code = isBlank(value) ? "LAST_7_DAYS" : value;
        LocalDateTime now = LocalDateTime.now();
        if ("TODAY".equals(code)) {
            return new DateRange("TODAY", now.toLocalDate().atStartOfDay());
        }
        if ("LAST_30_DAYS".equals(code)) {
            return new DateRange("LAST_30_DAYS", now.minusDays(30));
        }
        return new DateRange("LAST_7_DAYS", now.minusDays(7));
    }

    private AgentFlowStepDTO flow(String stepCode, String stepName, String status,
                                  String detail, String toolName, Long costMillis) {
        return new AgentFlowStepDTO()
                .setStepCode(stepCode)
                .setStepName(stepName)
                .setStatus(status)
                .setDetail(detail)
                .setToolName(toolName)
                .setCostMillis(costMillis);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String shortText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private static class DateRange {
        private final String code;
        private final LocalDateTime startTime;

        private DateRange(String code, LocalDateTime startTime) {
            this.code = code;
            this.startTime = startTime;
        }
    }
}
