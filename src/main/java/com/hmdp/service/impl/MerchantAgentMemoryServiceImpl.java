package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.AgentMemoryDTO;
import com.hmdp.dto.AgentMemoryPromptDTO;
import com.hmdp.dto.AgentMemoryRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.AgentMemory;
import com.hmdp.mapper.AgentMemoryMapper;
import com.hmdp.service.AgentMemoryValidator;
import com.hmdp.service.IMerchantAgentMemoryService;
import com.hmdp.service.IMerchantService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家运营 Agent Memory 服务实现。
 */
@Slf4j
@Service
public class MerchantAgentMemoryServiceImpl
        extends ServiceImpl<AgentMemoryMapper, AgentMemory>
        implements IMerchantAgentMemoryService {

    private static final int PROMPT_MEMORY_LIMIT = 10;
    private static final int PROMPT_MAX_LENGTH = 1200;

    @Resource
    private IMerchantService merchantService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private AgentMemoryValidator agentMemoryValidator;

    @Override
    public Result queryMemories(Long shopId, Integer status, String memoryType) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权查看该店铺 Memory");
        }
        QueryWrapper<AgentMemory> wrapper = new QueryWrapper<AgentMemory>()
                .eq("shop_id", shopId)
                .orderByDesc("update_time");
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (!isBlank(memoryType)) {
            wrapper.eq("memory_type", memoryValidator().normalizeMemoryType(memoryType));
        }
        List<AgentMemory> memories = list(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memories", toDtos(memories));
        result.put("total", memories == null ? 0 : memories.size());
        return Result.ok(result);
    }

    @Override
    public Result createMemory(Long shopId, AgentMemoryRequest request) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        if (!merchantService.hasCurrentUserShopPermission(shopId)) {
            return Result.fail("无权管理该店铺 Memory");
        }
        String validation = validateRequest(request);
        if (validation != null) {
            return Result.fail(validation);
        }
        AgentMemory memory = new AgentMemory()
                .setId(redisIdWorker.nextId("agent"))
                .setShopId(shopId)
                .setMerchantId(currentMerchantId())
                .setMemoryType(memoryValidator().normalizeMemoryType(request.getMemoryType()))
                .setMemoryKey(request.getMemoryKey().trim())
                .setMemoryValue(request.getMemoryValue().trim())
                .setConfidence(new BigDecimal("100.00"))
                .setSourceType("manual")
                .setStatus(resolveStatus(request.getStatus(), 1));
        save(memory);
        return Result.ok(toDto(memory));
    }

    @Override
    public Result updateMemory(Long memoryId, AgentMemoryRequest request) {
        if (memoryId == null) {
            return Result.fail("Memory id不能为空");
        }
        AgentMemory oldMemory = getById(memoryId);
        if (oldMemory == null) {
            return Result.fail("Memory 不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(oldMemory.getShopId())) {
            return Result.fail("无权管理该 Memory");
        }
        String validation = validateRequest(request);
        if (validation != null) {
            return Result.fail(validation);
        }
        AgentMemory update = new AgentMemory()
                .setId(memoryId)
                .setMemoryType(memoryValidator().normalizeMemoryType(request.getMemoryType()))
                .setMemoryKey(request.getMemoryKey().trim())
                .setMemoryValue(request.getMemoryValue().trim())
                .setStatus(resolveStatus(request.getStatus(), oldMemory.getStatus()));
        updateById(update);
        AgentMemory fresh = getById(memoryId);
        return Result.ok(toDto(fresh == null ? update : fresh));
    }

    @Override
    public Result deleteMemory(Long memoryId) {
        if (memoryId == null) {
            return Result.fail("Memory id不能为空");
        }
        AgentMemory memory = getById(memoryId);
        if (memory == null) {
            return Result.fail("Memory 不存在");
        }
        if (!merchantService.hasCurrentUserShopPermission(memory.getShopId())) {
            return Result.fail("无权管理该 Memory");
        }
        updateById(new AgentMemory().setId(memoryId).setStatus(0));
        return Result.ok();
    }

    @Override
    public List<AgentMemoryPromptDTO> listPromptMemories(Long shopId) {
        if (shopId == null) {
            return new ArrayList<>();
        }
        List<AgentMemory> rows;
        try {
            rows = query()
                    .eq("shop_id", shopId)
                    .eq("status", 1)
                    .in("memory_type", AgentMemoryValidator.TYPE_PREFERENCE, AgentMemoryValidator.TYPE_CONSTRAINT)
                    .orderByDesc("update_time")
                    .last("LIMIT " + PROMPT_MEMORY_LIMIT)
                    .list();
        } catch (RuntimeException e) {
            log.warn("Agent Memory storage unavailable, skip prompt memory load.");
            return new ArrayList<>();
        }
        List<AgentMemoryPromptDTO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (AgentMemory row : rows) {
            if (row == null || isBlank(row.getMemoryValue())) {
                continue;
            }
            result.add(new AgentMemoryPromptDTO()
                    .setMemoryType(row.getMemoryType())
                    .setMemoryKey(row.getMemoryKey())
                    .setMemoryValue(row.getMemoryValue()));
        }
        return result;
    }

    @Override
    public String buildMemoryPrompt(List<AgentMemoryPromptDTO> memories) {
        String rule = "使用规则：商家偏好记忆只代表表达风格、活动偏好或运营约束，不是真实业务数据；"
                + "当商家记忆和工具查询结果冲突时，以工具查询结果为准；如果没有偏好记忆，不要编造。";
        if (memories == null || memories.isEmpty()) {
            return "暂无商家偏好记忆。\n" + rule;
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (AgentMemoryPromptDTO memory : memories) {
            if (memory == null || isBlank(memory.getMemoryValue()) || count >= PROMPT_MEMORY_LIMIT) {
                continue;
            }
            String line = "- [" + defaultString(memory.getMemoryType(), AgentMemoryValidator.TYPE_PREFERENCE) + "] "
                    + defaultString(memory.getMemoryKey(), "preference")
                    + "："
                    + memory.getMemoryValue().trim()
                    + "\n";
            if (builder.length() + line.length() + rule.length() > PROMPT_MAX_LENGTH) {
                break;
            }
            builder.append(line);
            count++;
        }
        if (builder.length() == 0) {
            return "暂无商家偏好记忆。\n" + rule;
        }
        builder.append(rule);
        return builder.toString();
    }

    @Override
    public Map<String, Object> buildMemoryLoadSummary(List<AgentMemoryPromptDTO> memories) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        if (memories != null) {
            for (AgentMemoryPromptDTO memory : memories) {
                if (memory == null) {
                    continue;
                }
                if (!isBlank(memory.getMemoryKey())) {
                    keys.add(memory.getMemoryKey());
                }
                if (summary.length() < 240 && !isBlank(memory.getMemoryValue())) {
                    summary.append(defaultString(memory.getMemoryKey(), "memory"))
                            .append(":")
                            .append(shortText(memory.getMemoryValue(), 40))
                            .append("; ");
                }
            }
        }
        result.put("hitCount", memories == null ? 0 : memories.size());
        result.put("memoryKeys", keys);
        result.put("truncatedSummary", shortText(summary.toString(), 240));
        return result;
    }

    private String validateRequest(AgentMemoryRequest request) {
        if (request == null) {
            return "Memory 请求不能为空";
        }
        return memoryValidator().validate(request);
    }

    private List<AgentMemoryDTO> toDtos(List<AgentMemory> memories) {
        List<AgentMemoryDTO> result = new ArrayList<>();
        if (memories == null) {
            return result;
        }
        for (AgentMemory memory : memories) {
            result.add(toDto(memory));
        }
        return result;
    }

    private AgentMemoryDTO toDto(AgentMemory memory) {
        if (memory == null) {
            return null;
        }
        return new AgentMemoryDTO()
                .setMemoryId(memory.getId())
                .setShopId(memory.getShopId())
                .setMerchantId(memory.getMerchantId())
                .setMemoryType(memory.getMemoryType())
                .setMemoryKey(memory.getMemoryKey())
                .setMemoryValue(memory.getMemoryValue())
                .setConfidence(memory.getConfidence())
                .setSourceType(memory.getSourceType())
                .setSourceSessionId(memory.getSourceSessionId())
                .setStatus(memory.getStatus())
                .setCreateTime(memory.getCreateTime())
                .setUpdateTime(memory.getUpdateTime());
    }

    private Long currentMerchantId() {
        UserDTO user = UserHolder.getUser();
        return user == null ? 0L : user.getId();
    }

    private Integer resolveStatus(Integer status, Integer defaultStatus) {
        if (status == null) {
            return defaultStatus == null ? 1 : defaultStatus;
        }
        return Integer.valueOf(0).equals(status) ? 0 : 1;
    }

    private String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
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

    private AgentMemoryValidator memoryValidator() {
        if (agentMemoryValidator == null) {
            agentMemoryValidator = new AgentMemoryValidator();
        }
        return agentMemoryValidator;
    }
}
