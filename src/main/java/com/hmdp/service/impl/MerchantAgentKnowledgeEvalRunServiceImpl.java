package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentKnowledgeEvalRun;
import com.hmdp.mapper.AgentKnowledgeEvalRunMapper;
import com.hmdp.service.IMerchantAgentKnowledgeEvalRunService;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 评测运行记录服务实现。
 *
 * <p>业务逻辑很简单：把每次评测结果里的核心指标抽出来做结构化字段，
 * 再把完整结果保存为 JSON 快照。结构化字段用于列表和趋势展示，JSON 快照用于复盘失败样本。</p>
 */
@Slf4j
@Service
public class MerchantAgentKnowledgeEvalRunServiceImpl
        extends ServiceImpl<AgentKnowledgeEvalRunMapper, AgentKnowledgeEvalRun>
        implements IMerchantAgentKnowledgeEvalRunService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Long recordRun(Map<String, Object> evalResult) {
        if (evalResult == null || evalResult.isEmpty()) {
            return null;
        }
        try {
            Long runId = redisIdWorker.nextId("agent");
            AgentKnowledgeEvalRun run = new AgentKnowledgeEvalRun()
                    .setId(runId)
                    .setCaseSource(stringValue(evalResult.get("caseSource"), "default"))
                    .setLimitCount(intValue(evalResult.get("limit")))
                    .setTotalCount(intValue(evalResult.get("total")))
                    .setTop1PassCount(intValue(evalResult.get("top1PassCount")))
                    .setTopkPassCount(intValue(evalResult.get("topKPassCount")))
                    .setNoReliableHitCount(intValue(evalResult.get("noReliableHitCount")))
                    .setTop1PassRate(stringValue(evalResult.get("top1PassRate"), "0.00%"))
                    .setTopkPassRate(stringValue(evalResult.get("topKPassRate"), "0.00%"))
                    .setVectorMinSimilarity(decimalValue(evalResult.get("vectorMinSimilarity")))
                    .setResultSnapshot(JSONUtil.toJsonStr(evalResult));
            save(run);
            return runId;
        } catch (Exception e) {
            // 评测历史属于旁路观测能力，不能因为历史落库失败导致评测接口不可用。
            log.warn("保存 RAG 评测运行记录失败", e);
            return null;
        }
    }

    @Override
    public Result queryRecentRuns(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 30);
        List<AgentKnowledgeEvalRun> runs = query()
                .orderByDesc("create_time")
                .last("LIMIT " + safeLimit)
                .list();
        return Result.ok(toRows(runs));
    }

    @Override
    public Result queryRunDetail(Long runId) {
        if (runId == null) {
            return Result.fail("评测运行id不能为空");
        }
        AgentKnowledgeEvalRun run = getById(runId);
        if (run == null) {
            return Result.fail("评测运行记录不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", toRow(run));
        result.put("snapshot", parseSnapshot(run.getResultSnapshot()));
        return Result.ok(result);
    }

    private List<Map<String, Object>> toRows(List<AgentKnowledgeEvalRun> runs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentKnowledgeEvalRun run : runs) {
            rows.add(toRow(run));
        }
        return rows;
    }

    private Map<String, Object> toRow(AgentKnowledgeEvalRun run) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("runId", String.valueOf(run.getId()));
        row.put("caseSource", run.getCaseSource());
        row.put("limit", run.getLimitCount());
        row.put("total", run.getTotalCount());
        row.put("top1PassCount", run.getTop1PassCount());
        row.put("topKPassCount", run.getTopkPassCount());
        row.put("noReliableHitCount", run.getNoReliableHitCount());
        row.put("top1PassRate", run.getTop1PassRate());
        row.put("topKPassRate", run.getTopkPassRate());
        row.put("vectorMinSimilarity", run.getVectorMinSimilarity());
        row.put("createTime", run.getCreateTime());
        return row;
    }

    private Object parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSONUtil.parseObj(snapshot);
        } catch (Exception e) {
            log.warn("解析 RAG 评测快照失败，runSnapshot={}", snapshot, e);
            return new LinkedHashMap<>();
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
