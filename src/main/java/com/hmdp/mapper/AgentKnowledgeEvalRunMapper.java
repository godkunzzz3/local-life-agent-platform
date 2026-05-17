package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.AgentKnowledgeEvalRun;

/**
 * RAG 评测运行记录 Mapper。
 *
 * <p>当前只需要 MyBatis-Plus BaseMapper 提供的基础 CRUD。
 * 复杂趋势统计可以等数据量和页面需求明确后再补专门 SQL。</p>
 */
public interface AgentKnowledgeEvalRunMapper extends BaseMapper<AgentKnowledgeEvalRun> {
}
