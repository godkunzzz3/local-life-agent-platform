package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.AgentKnowledgeEvalCase;

/**
 * RAG 召回评测用例 Mapper。
 *
 * <p>当前只需要 MyBatis-Plus 基础 CRUD。评测用例的业务校验、批量替换和 DTO 转换
 * 都放在 Service 层，避免 Mapper 层混入业务逻辑。</p>
 */
public interface AgentKnowledgeEvalCaseMapper extends BaseMapper<AgentKnowledgeEvalCase> {
}
