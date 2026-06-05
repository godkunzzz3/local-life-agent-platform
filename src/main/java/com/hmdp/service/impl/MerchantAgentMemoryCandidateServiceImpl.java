package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.agent.MerchantAgentMemoryCandidateExtractor;
import com.hmdp.dto.AgentMemoryCandidateConfirmRequest;
import com.hmdp.dto.AgentMemoryCandidateDTO;
import com.hmdp.dto.AgentMemoryCandidateGenerateRequest;
import com.hmdp.dto.AgentMemoryCandidateGenerateResultDTO;
import com.hmdp.dto.AgentMemoryCandidateRequest;
import com.hmdp.dto.AgentMemoryDTO;
import com.hmdp.dto.AgentMemoryRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AgentMemoryCandidate;
import com.hmdp.mapper.AgentMemoryCandidateMapper;
import com.hmdp.service.AgentMemoryValidator;
import com.hmdp.service.AgentWorkflowRecorderService;
import com.hmdp.service.IMerchantAgentMemoryCandidateService;
import com.hmdp.service.IMerchantAgentMemoryService;
import com.hmdp.service.IMerchantService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家运营 Agent 候选记忆服务实现。
 */
@Slf4j
@Service
public class MerchantAgentMemoryCandidateServiceImpl
        extends ServiceImpl<AgentMemoryCandidateMapper, AgentMemoryCandidate>
        implements IMerchantAgentMemoryCandidateService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DELETED = "DELETED";

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("80.00");

    @Resource
    private IMerchantService merchantService;
    @Resource
    private IMerchantAgentMemoryService agentMemoryService;
    @Resource
    private AgentWorkflowRecorderService agentWorkflowRecorderService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private AgentMemoryValidator agentMemoryValidator;
    @Resource
    private MerchantAgentMemoryCandidateExtractor candidateExtractor;

    @Override
    public Result queryCandidates(Long shopId, String status, Integer limit) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权查看该店铺候选记忆");
        }
        QueryWrapper<AgentMemoryCandidate> wrapper = new QueryWrapper<AgentMemoryCandidate>()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .last("LIMIT " + normalizeLimit(limit));
        String normalizedStatus = normalizeStatus(status);
        if (!"ALL".equals(normalizedStatus)) {
            wrapper.eq("status", normalizedStatus);
        }
        List<AgentMemoryCandidate> candidates = list(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidates", toDtos(candidates));
        result.put("total", candidates == null ? 0 : candidates.size());
        return Result.ok(result);
    }

    @Override
    public Result generateCandidates(Long shopId, AgentMemoryCandidateGenerateRequest request) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (request == null || isBlank(request.getText())) {
            return Result.fail("候选记忆生成文本不能为空");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺候选记忆");
        }
        Long merchantId = currentMerchantId();
        Long workflowRunId = startWorkflowRun(request.getSessionId(), shopId, merchantId,
                "manual_generate", request.getText());
        List<AgentMemoryCandidateDTO> savedDtos = new ArrayList<>();
        List<AgentMemoryCandidateRequest> extracted = candidateExtractor().extract(request.getText());
        for (AgentMemoryCandidateRequest draft : extracted) {
            String validation = validateCandidateRequest(draft);
            if (validation != null) {
                log.warn("Skip invalid memory candidate: {}", validation);
                continue;
            }
            AgentMemoryCandidate candidate = new AgentMemoryCandidate()
                    .setId(redisIdWorker.nextId("agent"))
                    .setShopId(shopId)
                    .setMerchantId(merchantId)
                    .setSessionId(request.getSessionId())
                    .setSourceMessageId(request.getSourceMessageId())
                    .setCandidateType(memoryValidator().normalizeMemoryType(draft.getCandidateType()))
                    .setMemoryKey(draft.getMemoryKey().trim())
                    .setMemoryValue(draft.getMemoryValue().trim())
                    .setReason(shortText(draft.getReason(), 512))
                    .setConfidence(resolveConfidence(draft.getConfidence()))
                    .setStatus(STATUS_PENDING);
            save(candidate);
            savedDtos.add(toDto(candidate));
        }
        recordCandidateGenerateStep(workflowRunId, request.getSessionId(), shopId, savedDtos);
        finishWorkflowRun(workflowRunId, "候选记忆生成完成，命中 " + savedDtos.size() + " 条");
        return Result.ok(new AgentMemoryCandidateGenerateResultDTO()
                .setHitCount(savedDtos.size())
                .setCandidates(savedDtos));
    }

    @Override
    public Result updateCandidate(Long candidateId, AgentMemoryCandidateRequest request) {
        AgentMemoryCandidate candidate = getAccessibleCandidate(candidateId);
        if (candidate == null) {
            return Result.fail("候选记忆不存在或无权操作");
        }
        if (!STATUS_PENDING.equals(candidate.getStatus())) {
            return Result.fail("只有 PENDING 候选记忆可以编辑");
        }
        String validation = validateCandidateRequest(request);
        if (validation != null) {
            return Result.fail(validation);
        }
        AgentMemoryCandidate update = new AgentMemoryCandidate()
                .setId(candidateId)
                .setCandidateType(memoryValidator().normalizeMemoryType(request.getCandidateType()))
                .setMemoryKey(request.getMemoryKey().trim())
                .setMemoryValue(request.getMemoryValue().trim())
                .setReason(shortText(request.getReason(), 512))
                .setConfidence(resolveConfidence(request.getConfidence()));
        updateById(update);
        AgentMemoryCandidate fresh = getById(candidateId);
        return Result.ok(toDto(fresh == null ? update : fresh));
    }

    @Override
    @Transactional
    public Result confirmCandidate(Long candidateId, AgentMemoryCandidateConfirmRequest request) {
        AgentMemoryCandidate candidate = getAccessibleCandidate(candidateId);
        if (candidate == null) {
            return Result.fail("候选记忆不存在或无权操作");
        }
        if (!STATUS_PENDING.equals(candidate.getStatus())) {
            return Result.fail("只有 PENDING 候选记忆可以确认");
        }
        AgentMemoryRequest memoryRequest = toMemoryRequest(candidate, request);
        String validation = memoryValidator().validate(memoryRequest);
        if (validation != null) {
            return Result.fail(validation);
        }
        Result memoryResult = agentMemoryService.createMemory(candidate.getShopId(), memoryRequest);
        if (!Boolean.TRUE.equals(memoryResult.getSuccess())) {
            return memoryResult;
        }
        Long createdMemoryId = resolveCreatedMemoryId(memoryResult.getData());
        updateById(new AgentMemoryCandidate()
                .setId(candidateId)
                .setStatus(STATUS_CREATED));
        recordCandidateConfirmWorkflow(candidate, createdMemoryId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidate", toDto(new AgentMemoryCandidate()
                .setId(candidateId)
                .setShopId(candidate.getShopId())
                .setMerchantId(candidate.getMerchantId())
                .setSessionId(candidate.getSessionId())
                .setSourceMessageId(candidate.getSourceMessageId())
                .setCandidateType(memoryRequest.getMemoryType())
                .setMemoryKey(memoryRequest.getMemoryKey())
                .setMemoryValue(memoryRequest.getMemoryValue())
                .setReason(candidate.getReason())
                .setConfidence(candidate.getConfidence())
                .setStatus(STATUS_CREATED)
                .setCreateTime(candidate.getCreateTime())
                .setUpdateTime(candidate.getUpdateTime())));
        result.put("memory", memoryResult.getData());
        result.put("createdMemoryId", createdMemoryId);
        return Result.ok(result);
    }

    @Override
    public Result rejectCandidate(Long candidateId) {
        return updatePendingStatus(candidateId, STATUS_REJECTED, "只有 PENDING 候选记忆可以拒绝");
    }

    @Override
    public Result deleteCandidate(Long candidateId) {
        return updatePendingStatus(candidateId, STATUS_DELETED, "只有 PENDING 候选记忆可以删除");
    }

    private Result updatePendingStatus(Long candidateId, String nextStatus, String nonPendingMessage) {
        AgentMemoryCandidate candidate = getAccessibleCandidate(candidateId);
        if (candidate == null) {
            return Result.fail("候选记忆不存在或无权操作");
        }
        if (!STATUS_PENDING.equals(candidate.getStatus())) {
            return Result.fail(nonPendingMessage);
        }
        updateById(new AgentMemoryCandidate().setId(candidateId).setStatus(nextStatus));
        return Result.ok();
    }

    private AgentMemoryCandidate getAccessibleCandidate(Long candidateId) {
        if (candidateId == null) {
            return null;
        }
        AgentMemoryCandidate candidate = getById(candidateId);
        if (candidate == null) {
            return null;
        }
        if (!merchantService.hasCurrentUserShopPermission(candidate.getShopId())) {
            return null;
        }
        return candidate;
    }

    private String validateCandidateRequest(AgentMemoryCandidateRequest request) {
        if (request == null) {
            return "候选记忆请求不能为空";
        }
        AgentMemoryRequest memoryRequest = new AgentMemoryRequest();
        memoryRequest.setMemoryType(request.getCandidateType());
        memoryRequest.setMemoryKey(request.getMemoryKey());
        memoryRequest.setMemoryValue(request.getMemoryValue());
        return memoryValidator().validate(memoryRequest);
    }

    private AgentMemoryRequest toMemoryRequest(AgentMemoryCandidate candidate, AgentMemoryCandidateConfirmRequest request) {
        AgentMemoryRequest memoryRequest = new AgentMemoryRequest();
        memoryRequest.setMemoryType(!isBlank(request == null ? null : request.getCandidateType())
                ? request.getCandidateType()
                : candidate.getCandidateType());
        memoryRequest.setMemoryKey(!isBlank(request == null ? null : request.getMemoryKey())
                ? request.getMemoryKey()
                : candidate.getMemoryKey());
        memoryRequest.setMemoryValue(!isBlank(request == null ? null : request.getMemoryValue())
                ? request.getMemoryValue()
                : candidate.getMemoryValue());
        memoryRequest.setStatus(1);
        return memoryRequest;
    }

    private void recordCandidateGenerateStep(Long runId, Long sessionId, Long shopId, List<AgentMemoryCandidateDTO> candidates) {
        if (agentWorkflowRecorderService == null) {
            return;
        }
        try {
            Map<String, Object> output = buildCandidateSummary(candidates);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("shopId", shopId);
            input.put("source", "rule");
            agentWorkflowRecorderService.recordStep(runId, sessionId, shopId, 1,
                    "MEMORY_CANDIDATE_GENERATE", "生成候选记忆", "MEMORY_CANDIDATE_GENERATE", null,
                    "success", input, output, "规则生成候选记忆", null, null);
        } catch (RuntimeException e) {
            log.warn("记录 MEMORY_CANDIDATE_GENERATE 失败", e);
        }
    }

    private void recordCandidateConfirmWorkflow(AgentMemoryCandidate candidate, Long createdMemoryId) {
        if (agentWorkflowRecorderService == null || candidate == null) {
            return;
        }
        try {
            Long runId = startWorkflowRun(candidate.getSessionId(), candidate.getShopId(), candidate.getMerchantId(),
                    "merchant_confirm", candidate.getMemoryKey());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("candidateId", String.valueOf(candidate.getId()));
            output.put("shopId", candidate.getShopId());
            output.put("memoryKey", candidate.getMemoryKey());
            output.put("status", STATUS_CREATED);
            output.put("createdMemoryId", createdMemoryId == null ? null : String.valueOf(createdMemoryId));
            agentWorkflowRecorderService.recordStep(runId, candidate.getSessionId(), candidate.getShopId(), 1,
                    "MEMORY_CANDIDATE_CONFIRM", "确认候选记忆", "MEMORY_CANDIDATE_CONFIRM", null,
                    "success", null, output, "商家确认候选记忆并写入正式 Memory", null, null);
            finishWorkflowRun(runId, "候选记忆已确认");
        } catch (RuntimeException e) {
            log.warn("记录 MEMORY_CANDIDATE_CONFIRM 失败", e);
        }
    }

    private Long startWorkflowRun(Long sessionId, Long shopId, Long merchantId, String triggerType, String userMessage) {
        if (agentWorkflowRecorderService == null) {
            return null;
        }
        try {
            return agentWorkflowRecorderService.startRun(sessionId, shopId, merchantId,
                    "memory_candidate", triggerType, userMessage, "memory_candidate");
        } catch (RuntimeException e) {
            log.warn("记录候选记忆 Workflow Run 失败", e);
            return null;
        }
    }

    private void finishWorkflowRun(Long runId, String summary) {
        if (agentWorkflowRecorderService != null) {
            try {
                agentWorkflowRecorderService.finishRun(runId, summary);
            } catch (RuntimeException e) {
                log.warn("结束候选记忆 Workflow Run 失败", e);
            }
        }
    }

    private Map<String, Object> buildCandidateSummary(List<AgentMemoryCandidateDTO> candidates) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        if (candidates != null) {
            for (AgentMemoryCandidateDTO candidate : candidates) {
                if (candidate == null) {
                    continue;
                }
                if (!isBlank(candidate.getMemoryKey())) {
                    keys.add(candidate.getMemoryKey());
                }
                if (summary.length() < 240 && !isBlank(candidate.getMemoryValue())) {
                    summary.append(candidate.getMemoryKey()).append(":")
                            .append(shortText(candidate.getMemoryValue(), 40))
                            .append("; ");
                }
            }
        }
        result.put("hitCount", candidates == null ? 0 : candidates.size());
        result.put("candidateKeys", keys);
        result.put("truncatedSummary", shortText(summary.toString(), 240));
        return result;
    }

    private List<AgentMemoryCandidateDTO> toDtos(List<AgentMemoryCandidate> candidates) {
        List<AgentMemoryCandidateDTO> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        for (AgentMemoryCandidate candidate : candidates) {
            result.add(toDto(candidate));
        }
        return result;
    }

    private AgentMemoryCandidateDTO toDto(AgentMemoryCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        return new AgentMemoryCandidateDTO()
                .setCandidateId(candidate.getId())
                .setShopId(candidate.getShopId())
                .setMerchantId(candidate.getMerchantId())
                .setSessionId(candidate.getSessionId())
                .setSourceMessageId(candidate.getSourceMessageId())
                .setCandidateType(candidate.getCandidateType())
                .setMemoryKey(candidate.getMemoryKey())
                .setMemoryValue(candidate.getMemoryValue())
                .setReason(candidate.getReason())
                .setConfidence(candidate.getConfidence())
                .setStatus(candidate.getStatus())
                .setCreateTime(candidate.getCreateTime())
                .setUpdateTime(candidate.getUpdateTime());
    }

    private Long resolveCreatedMemoryId(Object data) {
        if (data instanceof AgentMemoryDTO) {
            return ((AgentMemoryDTO) data).getMemoryId();
        }
        return null;
    }

    private BigDecimal resolveConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return DEFAULT_CONFIDENCE;
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (confidence.compareTo(new BigDecimal("100.00")) > 0) {
            return new BigDecimal("100.00");
        }
        return confidence;
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            return STATUS_PENDING;
        }
        return status.trim().toUpperCase();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long currentMerchantId() {
        UserDTO user = UserHolder.getUser();
        return user == null ? 0L : user.getId();
    }

    private AgentMemoryValidator memoryValidator() {
        if (agentMemoryValidator == null) {
            agentMemoryValidator = new AgentMemoryValidator();
        }
        return agentMemoryValidator;
    }

    private MerchantAgentMemoryCandidateExtractor candidateExtractor() {
        if (candidateExtractor == null) {
            candidateExtractor = new MerchantAgentMemoryCandidateExtractor(memoryValidator());
        }
        return candidateExtractor;
    }

    private String shortText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\r", " ").replace("\n", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
