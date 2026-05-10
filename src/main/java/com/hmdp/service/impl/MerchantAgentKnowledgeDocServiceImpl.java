package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AgentKnowledgeDocRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeDoc;
import com.hmdp.mapper.AgentKnowledgeDocMapper;
import com.hmdp.service.IMerchantAgentKnowledgeDocService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家运营 Agent 知识库文档服务实现。
 *
 * <p>当前是 RAG 的最小可行版本：文档保存在 MySQL，检索使用标题/正文关键词匹配。
 * 这一步先让知识库具备“可维护、可查询、可被 Agent 读取”的业务闭环。</p>
 */
@Service
public class MerchantAgentKnowledgeDocServiceImpl
        extends ServiceImpl<AgentKnowledgeDocMapper, AgentKnowledgeDoc>
        implements IMerchantAgentKnowledgeDocService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result createKnowledgeDoc(AgentKnowledgeDocRequest request) {
        Result validateResult = validateCreateRequest(request);
        if (!validateResult.getSuccess()) {
            return validateResult;
        }

        AgentKnowledgeDoc doc = new AgentKnowledgeDoc()
                .setId(redisIdWorker.nextId("agent"))
                .setTitle(request.getTitle().trim())
                .setCategory(request.getCategory().trim())
                .setContent(request.getContent().trim())
                .setVectorId(trimToNull(request.getVectorId()))
                .setStatus(request.getStatus() == null ? 1 : request.getStatus());
        save(doc);
        return Result.ok(toDocRow(doc));
    }

    @Override
    public Result updateKnowledgeDoc(Long docId, AgentKnowledgeDocRequest request) {
        if (docId == null) {
            return Result.fail("知识文档id不能为空");
        }
        if (request == null) {
            return Result.fail("知识文档修改内容不能为空");
        }
        AgentKnowledgeDoc oldDoc = getById(docId);
        if (oldDoc == null) {
            return Result.fail("知识文档不存在");
        }

        AgentKnowledgeDoc updateDoc = new AgentKnowledgeDoc().setId(docId);
        if (!isBlank(request.getTitle())) {
            updateDoc.setTitle(request.getTitle().trim());
        }
        if (!isBlank(request.getCategory())) {
            updateDoc.setCategory(request.getCategory().trim());
        }
        if (!isBlank(request.getContent())) {
            updateDoc.setContent(request.getContent().trim());
        }
        if (request.getVectorId() != null) {
            updateDoc.setVectorId(trimToNull(request.getVectorId()));
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                return Result.fail("知识文档状态只能是启用或停用");
            }
            updateDoc.setStatus(request.getStatus());
        }
        updateById(updateDoc);
        return Result.ok(toDocRow(getById(docId)));
    }

    @Override
    public Result disableKnowledgeDoc(Long docId) {
        if (docId == null) {
            return Result.fail("知识文档id不能为空");
        }
        AgentKnowledgeDoc doc = getById(docId);
        if (doc == null) {
            return Result.fail("知识文档不存在");
        }
        updateById(new AgentKnowledgeDoc().setId(docId).setStatus(0));
        doc.setStatus(0);
        return Result.ok(toDocRow(doc));
    }

    @Override
    public Result queryKnowledgeDocs(String category, String keyword, Integer status) {
        List<AgentKnowledgeDoc> docs = query()
                .eq(!isBlank(category), "category", category)
                .eq(status != null, "status", status)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("create_time")
                .last("LIMIT 100")
                .list();
        return Result.ok(toDocRows(docs));
    }

    @Override
    public Result searchKnowledgeDocs(String category, String keyword, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 5 : Math.min(limit, 20);
        List<AgentKnowledgeDoc> docs = query()
                .eq("status", 1)
                .eq(!isBlank(category), "category", category)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("update_time")
                .last("LIMIT " + safeLimit)
                .list();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retrievalMode", "mysql_keyword");
        result.put("category", category);
        result.put("keyword", keyword);
        result.put("limit", safeLimit);
        result.put("documents", toDocRows(docs));
        result.put("message", docs.isEmpty()
                ? "暂未检索到相关运营知识，可以先补充知识文档"
                : "已基于关键词召回运营知识文档");
        return Result.ok(result);
    }

    @Override
    public List<Map<String, Object>> retrieveForAgent(String intent, String userMessage, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 3 : Math.min(limit, 8);
        String category = resolveCategoryByIntent(intent);
        String keyword = resolveKeyword(userMessage, intent);
        List<AgentKnowledgeDoc> docs = query()
                .eq("status", 1)
                .eq(!isBlank(category), "category", category)
                .and(!isBlank(keyword), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("content", keyword))
                .orderByDesc("update_time")
                .last("LIMIT " + safeLimit)
                .list();

        // 如果关键词没有命中，退一步按分类取最近知识，避免 Agent 完全没有运营规则可参考。
        if (docs.isEmpty() && !isBlank(category)) {
            docs = query()
                    .eq("status", 1)
                    .eq("category", category)
                    .orderByDesc("update_time")
                    .last("LIMIT " + safeLimit)
                    .list();
        }
        return toDocRows(docs);
    }

    private Result validateCreateRequest(AgentKnowledgeDocRequest request) {
        if (request == null) {
            return Result.fail("知识文档内容不能为空");
        }
        if (isBlank(request.getTitle())) {
            return Result.fail("知识标题不能为空");
        }
        if (isBlank(request.getCategory())) {
            return Result.fail("知识分类不能为空");
        }
        if (isBlank(request.getContent())) {
            return Result.fail("知识正文不能为空");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            return Result.fail("知识文档状态只能是启用或停用");
        }
        return Result.ok();
    }

    private List<Map<String, Object>> toDocRows(List<AgentKnowledgeDoc> docs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentKnowledgeDoc doc : docs) {
            rows.add(toDocRow(doc));
        }
        return rows;
    }

    private Map<String, Object> toDocRow(AgentKnowledgeDoc doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getId());
        row.put("docId", String.valueOf(doc.getId()));
        row.put("title", doc.getTitle());
        row.put("category", doc.getCategory());
        row.put("categoryName", resolveCategoryName(doc.getCategory()));
        row.put("content", doc.getContent());
        row.put("vectorId", doc.getVectorId());
        row.put("status", doc.getStatus());
        row.put("statusName", doc.getStatus() != null && doc.getStatus() == 1 ? "启用" : "停用");
        row.put("createTime", doc.getCreateTime());
        row.put("updateTime", doc.getUpdateTime());
        return row;
    }

    private String resolveCategoryName(String category) {
        if ("voucher_rule".equals(category)) {
            return "优惠券规则";
        }
        if ("seckill_rule".equals(category)) {
            return "秒杀活动规则";
        }
        if ("industry_case".equals(category)) {
            return "行业运营案例";
        }
        if ("risk_rule".equals(category)) {
            return "平台风控规则";
        }
        if ("cost_rule".equals(category)) {
            return "成本计算规则";
        }
        return "其他知识";
    }

    private String resolveCategoryByIntent(String intent) {
        if ("voucher_plan".equals(intent)) {
            return "seckill_rule";
        }
        if ("review_analysis".equals(intent)) {
            return "industry_case";
        }
        if ("order_analysis".equals(intent) || "operation_chat".equals(intent)) {
            return "cost_rule";
        }
        return null;
    }

    private String resolveKeyword(String userMessage, String intent) {
        if (containsAny(userMessage, "秒杀", "周末")) {
            return "秒杀";
        }
        if (containsAny(userMessage, "优惠券", "代金券")) {
            return "优惠券";
        }
        if (containsAny(userMessage, "评价", "评论", "口碑")) {
            return "评价";
        }
        if (containsAny(userMessage, "成本", "利润", "收入", "营收")) {
            return "成本";
        }
        if ("voucher_plan".equals(intent)) {
            return "活动";
        }
        if ("review_analysis".equals(intent)) {
            return "评价";
        }
        return "";
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
