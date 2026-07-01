package com.hmdp.agent.skill.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 商家知识问答 Skill 输入。
 */
@Data
@Accessors(chain = true)
public class KnowledgeQaSkillInput {

    private Long shopId;

    /**
     * RAG 分类路由意图，不作为商家隔离字段。
     */
    private String intent;

    private String question;

    private Integer topK;
}
