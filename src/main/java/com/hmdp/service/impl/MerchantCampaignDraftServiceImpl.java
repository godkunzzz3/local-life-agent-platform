package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.AgentCampaignDraft;
import com.hmdp.mapper.AgentCampaignDraftMapper;
import com.hmdp.service.IMerchantCampaignDraftService;
import org.springframework.stereotype.Service;

/**
 * 商家运营 Agent 活动草稿服务实现。
 */
@Service
public class MerchantCampaignDraftServiceImpl extends ServiceImpl<AgentCampaignDraftMapper, AgentCampaignDraft> implements IMerchantCampaignDraftService {
}
