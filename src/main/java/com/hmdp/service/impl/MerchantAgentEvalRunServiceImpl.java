package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AgentEvalResultDTO;
import com.hmdp.dto.AgentEvalRunSummaryDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.AgentEvalResult;
import com.hmdp.entity.AgentEvalRun;
import com.hmdp.mapper.AgentEvalRunMapper;
import com.hmdp.service.IMerchantAgentEvalResultService;
import com.hmdp.service.IMerchantAgentEvalRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 行为评测运行记录服务实现。
 */
@Slf4j
@Service
public class MerchantAgentEvalRunServiceImpl
        extends ServiceImpl<AgentEvalRunMapper, AgentEvalRun>
        implements IMerchantAgentEvalRunService {

    @Resource
    private IMerchantAgentEvalResultService agentEvalResultService;

    @Override
    public Result queryRecentRuns(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 50);
        List<AgentEvalRun> runs;
        try {
            runs = query()
                    .orderByDesc("create_time")
                    .last("LIMIT " + safeLimit)
                    .list();
        } catch (RuntimeException e) {
            log.warn("Agent Eval runs storage unavailable, return empty history.", e);
            return Result.ok(new ArrayList<>());
        }
        List<AgentEvalRunSummaryDTO> rows = new ArrayList<>();
        for (AgentEvalRun run : runs) {
            rows.add(toSummary(run));
        }
        return Result.ok(rows);
    }

    @Override
    public Result queryRunDetail(Long runId) {
        if (runId == null) {
            return Result.fail("Agent Eval 运行id不能为空");
        }
        AgentEvalRun run = getById(runId);
        if (run == null) {
            return Result.fail("Agent Eval 运行记录不存在");
        }
        List<AgentEvalResult> results = agentEvalResultService.list(new QueryWrapper<AgentEvalResult>()
                .eq("run_id", runId)
                .orderByAsc("id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", toSummary(run));
        result.put("items", toResultDTOs(results));
        return Result.ok(result);
    }

    private AgentEvalRunSummaryDTO toSummary(AgentEvalRun run) {
        AgentEvalRunSummaryDTO dto = new AgentEvalRunSummaryDTO();
        dto.setRunId(run.getId());
        dto.setCaseSource(run.getCaseSource());
        dto.setTotalCount(run.getTotalCount());
        dto.setPassCount(run.getPassCount());
        dto.setFailCount(run.getFailCount());
        dto.setIntentAccuracy(run.getIntentAccuracy());
        dto.setToolAccuracy(run.getToolAccuracy());
        dto.setConfirmAccuracy(run.getConfirmAccuracy());
        dto.setRiskAccuracy(run.getRiskAccuracy());
        dto.setOverallScore(run.getOverallScore());
        dto.setSummary(run.getSummary());
        dto.setCreateTime(run.getCreateTime());
        return dto;
    }

    private List<AgentEvalResultDTO> toResultDTOs(List<AgentEvalResult> results) {
        List<AgentEvalResultDTO> items = new ArrayList<>();
        for (AgentEvalResult result : results) {
            AgentEvalResultDTO dto = new AgentEvalResultDTO();
            dto.setResultId(result.getId());
            dto.setCaseId(result.getCaseId());
            dto.setCaseName(result.getCaseName());
            dto.setUserInput(result.getUserInput());
            dto.setExpectedIntent(result.getExpectedIntent());
            dto.setActualIntent(result.getActualIntent());
            dto.setExpectedTools(parseStringList(result.getExpectedTools()));
            dto.setActualTools(parseStringList(result.getActualTools()));
            dto.setExpectedNeedConfirm(Integer.valueOf(1).equals(result.getExpectedNeedConfirm()));
            dto.setActualNeedConfirm(Integer.valueOf(1).equals(result.getActualNeedConfirm()));
            dto.setExpectedRiskLevel(result.getExpectedRiskLevel());
            dto.setActualRiskLevel(result.getActualRiskLevel());
            dto.setIntentPassed(Integer.valueOf(1).equals(result.getIntentPassed()));
            dto.setToolPassed(Integer.valueOf(1).equals(result.getToolPassed()));
            dto.setConfirmPassed(Integer.valueOf(1).equals(result.getConfirmPassed()));
            dto.setRiskPassed(Integer.valueOf(1).equals(result.getRiskPassed()));
            dto.setPassed(Integer.valueOf(1).equals(result.getPassed()));
            dto.setScore(result.getScore());
            dto.setDiagnosis(result.getDiagnosis());
            items.add(dto);
        }
        return items;
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(value, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
