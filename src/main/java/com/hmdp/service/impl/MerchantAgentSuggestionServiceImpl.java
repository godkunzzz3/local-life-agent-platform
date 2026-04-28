package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentSuggestion;
import com.hmdp.mapper.AgentSuggestionMapper;
import com.hmdp.service.IMerchantAgentSuggestionService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent 建议服务实现。
 */
@Service
public class MerchantAgentSuggestionServiceImpl extends ServiceImpl<AgentSuggestionMapper, AgentSuggestion> implements IMerchantAgentSuggestionService {
}
