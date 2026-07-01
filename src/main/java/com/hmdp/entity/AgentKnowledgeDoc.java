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
 * Agent 知识库文档。
 *
 * <p>RAG 的第一步不是直接接向量库，而是先把“优惠券规则、秒杀规则、行业案例、
 * 平台风控规则”等知识沉淀成可管理的文档。后续做向量检索时，可以基于这些文档切片、
 * 生成 embedding，并把 vectorId 回写到本表。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_knowledge_doc")
public class AgentKnowledgeDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。项目内统一使用 RedisIdWorker 生成，避免后续分布式部署时依赖数据库自增。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 店铺ID。为空表示全局公共知识；非空表示该店铺私有知识。
     */
    private Long shopId;

    /**
     * 知识标题，例如“秒杀券库存控制规则”。
     */
    private String title;

    /**
     * 知识分类：voucher_rule / seckill_rule / industry_case / risk_rule / cost_rule。
     */
    private String category;

    /**
     * 知识正文。第一版直接保存原文，后续可拆成 chunk 表或向量库文档。
     */
    private String content;

    /**
     * 向量库文档 ID。第一版先为空，后续接入 Redis Vector / Chroma 后回填。
     */
    private String vectorId;

    /**
     * 状态：1启用，0停用。
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
