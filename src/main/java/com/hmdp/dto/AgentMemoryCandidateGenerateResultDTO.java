package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Agent Memory 候选生成结果。
 */
@Data
@Accessors(chain = true)
public class AgentMemoryCandidateGenerateResultDTO {

    private Integer hitCount;

    private List<AgentMemoryCandidateDTO> candidates;
}
