package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.AgentKnowledgeDoc;

/**
 * Agent 知识库文档 Mapper。
 *
 * <p>当前使用 MyBatis-Plus BaseMapper 即可满足基础 CRUD。复杂检索先放在 Service
 * 里用 QueryWrapper 组合条件，后续接向量库时再新增专门的检索组件。</p>
 */
public interface AgentKnowledgeDocMapper extends BaseMapper<AgentKnowledgeDoc> {
}
