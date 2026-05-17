package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RAG 召回评测运行记录。
 *
 * <p>评测用例表保存的是“试卷”，这张表保存的是每次“考试成绩”。
 * 每运行一次 RAG 批量评测，就把 Top1/TopK 命中率、无可靠召回数量和完整结果快照保存下来，
 * 方便后续对比向量模型、知识文档、相似度阈值或 Prompt 调整前后的效果。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_knowledge_eval_run")
public class AgentKnowledgeEvalRun implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。沿用项目 RedisIdWorker，保持和其他 Agent 表一致。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 用例来源：custom/persisted/default/default_fallback。
     */
    private String caseSource;

    /**
     * 本次评测使用的 TopK 数量。
     */
    private Integer limitCount;

    /**
     * 参与评测的用例数量。
     */
    private Integer totalCount;

    /**
     * 第一条召回结果命中预期分类的数量。
     */
    private Integer top1PassCount;

    /**
     * TopK 召回结果中任一条命中预期分类的数量。
     */
    private Integer topkPassCount;

    /**
     * 没有通过质量闸门的召回数量。
     */
    private Integer noReliableHitCount;

    /**
     * Top1 命中率，保存展示字符串，便于前端直接渲染。
     */
    private String top1PassRate;

    /**
     * TopK 命中率，保存展示字符串，便于前端直接渲染。
     */
    private String topkPassRate;

    /**
     * 本次评测使用的向量相似度阈值。
     */
    private BigDecimal vectorMinSimilarity;

    /**
     * 完整评测结果 JSON 快照，用于后续复盘失败用例。
     */
    private String resultSnapshot;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
