package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AgentKnowledgeEvaluateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeEvalCase;
import com.hmdp.mapper.AgentKnowledgeEvalCaseMapper;
import com.hmdp.service.IMerchantAgentKnowledgeEvalCaseService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * RAG 召回评测用例服务实现。
 *
 * <p>第一版采用“整体替换”的保存策略：保存时软停用旧用例，再插入新用例。
 * 这样前端可以像编辑一份测试集一样操作，避免一开始就引入复杂的单条版本管理。</p>
 */
@Service
public class MerchantAgentKnowledgeEvalCaseServiceImpl
        extends ServiceImpl<AgentKnowledgeEvalCaseMapper, AgentKnowledgeEvalCase>
        implements IMerchantAgentKnowledgeEvalCaseService {

    private static final int MAX_EVALUATION_CASES = 20;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result queryEnabledCases() {
        List<Map<String, Object>> rows = toRows(listEnabledCases());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cases", rows);
        result.put("total", rows.size());
        result.put("maxCaseCount", MAX_EVALUATION_CASES);
        return Result.ok(result);
    }

    @Override
    public Result replaceEnabledCases(AgentKnowledgeEvaluateRequest request) {
        List<AgentKnowledgeEvaluateRequest.CaseItem> cases = sanitizeCases(request == null ? null : request.getCases());
        if (cases.isEmpty()) {
            return Result.fail("请至少配置一条完整评测用例");
        }

        // 旧用例不物理删除，保留审计和复盘空间；正式查询只看 status=1。
        update(new UpdateWrapper<AgentKnowledgeEvalCase>()
                .eq("status", 1)
                .set("status", 0));

        List<AgentKnowledgeEvalCase> entities = new ArrayList<>();
        int sortOrder = 1;
        for (AgentKnowledgeEvaluateRequest.CaseItem item : cases) {
            entities.add(new AgentKnowledgeEvalCase()
                    .setId(redisIdWorker.nextId("agent"))
                    .setMessage(item.getMessage())
                    .setIntent(item.getIntent())
                    .setExpectedCategories(JSONUtil.toJsonStr(item.getExpectedCategories()))
                    .setSortOrder(sortOrder++)
                    .setStatus(1));
        }
        saveBatch(entities);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("savedCount", entities.size());
        result.put("cases", toRows(entities));
        result.put("maxCaseCount", MAX_EVALUATION_CASES);
        return Result.ok(result);
    }

    @Override
    public List<AgentKnowledgeEvaluateRequest.CaseItem> listEnabledCaseItems() {
        List<AgentKnowledgeEvaluateRequest.CaseItem> items = new ArrayList<>();
        for (AgentKnowledgeEvalCase entity : listEnabledCases()) {
            AgentKnowledgeEvaluateRequest.CaseItem item = new AgentKnowledgeEvaluateRequest.CaseItem();
            item.setMessage(entity.getMessage());
            item.setIntent(entity.getIntent());
            item.setExpectedCategories(parseExpectedCategories(entity.getExpectedCategories()));
            items.add(item);
        }
        return items;
    }

    private List<AgentKnowledgeEvalCase> listEnabledCases() {
        return query()
                .eq("status", 1)
                .orderByAsc("sort_order")
                .orderByAsc("create_time")
                .list();
    }

    private List<Map<String, Object>> toRows(List<AgentKnowledgeEvalCase> cases) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentKnowledgeEvalCase item : cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", String.valueOf(item.getId()));
            row.put("message", item.getMessage());
            row.put("intent", item.getIntent());
            row.put("expectedCategories", parseExpectedCategories(item.getExpectedCategories()));
            row.put("sortOrder", item.getSortOrder());
            row.put("status", item.getStatus());
            row.put("createTime", item.getCreateTime());
            row.put("updateTime", item.getUpdateTime());
            rows.add(row);
        }
        return rows;
    }

    private List<AgentKnowledgeEvaluateRequest.CaseItem> sanitizeCases(List<AgentKnowledgeEvaluateRequest.CaseItem> rawCases) {
        List<AgentKnowledgeEvaluateRequest.CaseItem> cases = new ArrayList<>();
        if (rawCases == null) {
            return cases;
        }
        for (AgentKnowledgeEvaluateRequest.CaseItem rawCase : rawCases) {
            if (cases.size() >= MAX_EVALUATION_CASES) {
                break;
            }
            if (rawCase == null || isBlank(rawCase.getMessage())) {
                continue;
            }
            List<String> expectedCategories = sanitizeCategories(rawCase.getExpectedCategories());
            if (expectedCategories.isEmpty()) {
                continue;
            }
            AgentKnowledgeEvaluateRequest.CaseItem item = new AgentKnowledgeEvaluateRequest.CaseItem();
            item.setMessage(rawCase.getMessage().trim());
            item.setIntent(isBlank(rawCase.getIntent()) ? "operation_chat" : rawCase.getIntent().trim());
            item.setExpectedCategories(expectedCategories);
            cases.add(item);
        }
        return cases;
    }

    private List<String> sanitizeCategories(List<String> rawCategories) {
        List<String> categories = new ArrayList<>();
        if (rawCategories == null) {
            return categories;
        }
        for (String category : rawCategories) {
            if (!isBlank(category)) {
                categories.add(category.trim());
            }
        }
        return categories;
    }

    private List<String> parseExpectedCategories(String value) {
        if (isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(value, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
