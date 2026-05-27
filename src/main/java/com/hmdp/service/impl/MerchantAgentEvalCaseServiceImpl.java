package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AgentEvalCaseItemDTO;
import com.hmdp.dto.AgentEvalRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentEvalCase;
import com.hmdp.mapper.AgentEvalCaseMapper;
import com.hmdp.service.IMerchantAgentEvalCaseService;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 行为评测用例服务实现。
 */
@Service
public class MerchantAgentEvalCaseServiceImpl
        extends ServiceImpl<AgentEvalCaseMapper, AgentEvalCase>
        implements IMerchantAgentEvalCaseService {

    private static final int MAX_EVALUATION_CASES = 50;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result queryEnabledCases() {
        List<AgentEvalCaseItemDTO> items = listEnabledCaseItems();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cases", items);
        result.put("total", items.size());
        result.put("maxCaseCount", MAX_EVALUATION_CASES);
        return Result.ok(result);
    }

    @Override
    public Result replaceEnabledCases(AgentEvalRequest request) {
        List<AgentEvalCaseItemDTO> cases = sanitizeCases(request == null ? null : request.getCases());
        if (cases.isEmpty()) {
            return Result.fail("请至少配置一条完整 Agent Eval 用例");
        }

        update(new UpdateWrapper<AgentEvalCase>()
                .eq("status", 1)
                .set("status", 0));

        List<AgentEvalCase> entities = new ArrayList<>();
        int sortOrder = 1;
        for (AgentEvalCaseItemDTO item : cases) {
            entities.add(new AgentEvalCase()
                    .setId(redisIdWorker.nextId("agent"))
                    .setCaseName(defaultString(item.getCaseName(), "Agent Eval 用例 " + sortOrder))
                    .setUserInput(item.getUserInput())
                    .setExpectedIntent(item.getExpectedIntent())
                    .setExpectedTools(JSONUtil.toJsonStr(sanitizeStringList(item.getExpectedTools())))
                    .setExpectedNeedConfirm(Boolean.TRUE.equals(item.getExpectedNeedConfirm()) ? 1 : 0)
                    .setExpectedRiskLevel(normalizeRiskLevel(item.getExpectedRiskLevel()))
                    .setExpectedKeywords(JSONUtil.toJsonStr(sanitizeStringList(item.getExpectedKeywords())))
                    .setCaseType(defaultString(item.getCaseType(), "rule"))
                    .setSortOrder(sortOrder++)
                    .setStatus(1));
        }
        saveBatch(entities);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("savedCount", entities.size());
        result.put("cases", toItems(entities));
        result.put("maxCaseCount", MAX_EVALUATION_CASES);
        return Result.ok(result);
    }

    @Override
    public List<AgentEvalCaseItemDTO> listEnabledCaseItems() {
        return toItems(query()
                .eq("status", 1)
                .orderByAsc("sort_order")
                .orderByAsc("create_time")
                .list());
    }

    private List<AgentEvalCaseItemDTO> toItems(List<AgentEvalCase> entities) {
        List<AgentEvalCaseItemDTO> items = new ArrayList<>();
        for (AgentEvalCase entity : entities) {
            AgentEvalCaseItemDTO item = new AgentEvalCaseItemDTO();
            item.setCaseId(entity.getId());
            item.setCaseName(entity.getCaseName());
            item.setUserInput(entity.getUserInput());
            item.setExpectedIntent(entity.getExpectedIntent());
            item.setExpectedTools(parseStringList(entity.getExpectedTools()));
            item.setExpectedNeedConfirm(Integer.valueOf(1).equals(entity.getExpectedNeedConfirm()));
            item.setExpectedRiskLevel(normalizeRiskLevel(entity.getExpectedRiskLevel()));
            item.setExpectedKeywords(parseStringList(entity.getExpectedKeywords()));
            item.setCaseType(entity.getCaseType());
            item.setSortOrder(entity.getSortOrder());
            item.setStatus(entity.getStatus());
            items.add(item);
        }
        return items;
    }

    private List<AgentEvalCaseItemDTO> sanitizeCases(List<AgentEvalCaseItemDTO> rawCases) {
        List<AgentEvalCaseItemDTO> cases = new ArrayList<>();
        if (rawCases == null) {
            return cases;
        }
        for (AgentEvalCaseItemDTO rawCase : rawCases) {
            if (cases.size() >= MAX_EVALUATION_CASES) {
                break;
            }
            if (rawCase == null || isBlank(rawCase.getUserInput()) || isBlank(rawCase.getExpectedIntent())) {
                continue;
            }
            AgentEvalCaseItemDTO item = new AgentEvalCaseItemDTO();
            item.setCaseName(trimToMax(defaultString(rawCase.getCaseName(), "Agent Eval 用例"), 128));
            item.setUserInput(trimToMax(rawCase.getUserInput().trim(), 512));
            item.setExpectedIntent(rawCase.getExpectedIntent().trim());
            item.setExpectedTools(sanitizeStringList(rawCase.getExpectedTools()));
            item.setExpectedNeedConfirm(Boolean.TRUE.equals(rawCase.getExpectedNeedConfirm()));
            item.setExpectedRiskLevel(normalizeRiskLevel(rawCase.getExpectedRiskLevel()));
            item.setExpectedKeywords(sanitizeStringList(rawCase.getExpectedKeywords()));
            item.setCaseType(defaultString(rawCase.getCaseType(), "rule"));
            cases.add(item);
        }
        return cases;
    }

    private List<String> sanitizeStringList(List<String> rawValues) {
        List<String> values = new ArrayList<>();
        if (rawValues == null) {
            return values;
        }
        for (String value : rawValues) {
            if (!isBlank(value)) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private List<String> parseStringList(String value) {
        if (isBlank(value)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(value, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String normalizeRiskLevel(String value) {
        return isBlank(value) ? "LOW" : value.trim().toUpperCase();
    }

    private String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String trimToMax(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
