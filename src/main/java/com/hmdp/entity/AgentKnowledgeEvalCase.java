package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RAG 召回评测用例。
 *
 * <p>这张表保存“商家问题 -> 期望召回分类”的测试集。它不参与正式商家对话，
 * 只用于开发和回归测试：每次调整知识库、Prompt、向量模型或相似度阈值后，
 * 都可以跑同一批用例，观察 Top1 / TopK 命中率是否下降。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_knowledge_eval_case")
public class AgentKnowledgeEvalCase implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。沿用项目 RedisIdWorker 生成方式，避免依赖数据库自增。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 测试问题，例如“帮我设计一张周末秒杀券”。
     */
    private String message;

    /**
     * 业务意图：voucher_plan / order_analysis / review_analysis / operation_chat。
     */
    private String intent;

    /**
     * 期望命中的知识分类 JSON，例如 ["seckill_rule","cost_rule"]。
     */
    private String expectedCategories;

    /**
     * 排序号。前端展示和批量评测时按这个字段升序。
     */
    private Integer sortOrder;

    /**
     * 状态：1启用，0停用。替换保存时旧用例会被软停用，便于后续复盘。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
