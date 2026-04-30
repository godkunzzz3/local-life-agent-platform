package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 店铺内容评价统计 DTO。
 *
 * <p>当前黑马点评项目没有独立的评分评价表，店铺口碑主要来自探店笔记和评论互动。
 * Agent 会根据这些字段判断店铺内容热度、用户互动是否不足，并生成运营建议。</p>
 */
@Data
public class ReviewStatsDTO {

    /**
     * 店铺探店笔记数量。
     */
    private Integer blogCount;

    /**
     * 探店笔记累计点赞数。
     */
    private Integer likedCount;

    /**
     * 探店笔记累计评论数。
     */
    private Integer commentCount;

    /**
     * 最近的探店内容摘要，便于 Agent 生成自然语言报告。
     */
    private List<String> recentContents;

    /**
     * 内容互动等级：高 / 中 / 低。
     */
    private String engagementLevel;
}
