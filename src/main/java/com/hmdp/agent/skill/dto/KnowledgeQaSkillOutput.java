package com.hmdp.agent.skill.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家知识问答 Skill 输出。
 */
@Data
@Accessors(chain = true)
public class KnowledgeQaSkillOutput {

    private Long shopId;

    private String intent;

    private String question;

    private String answer;

    private List<Object> retrievedChunks = new ArrayList<>();

    private Double confidence;

    private Boolean noReliableHit = true;

    private Integer topK;

    private Map<String, Object> metadata = new LinkedHashMap<>();
}
