package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 安全生成活动草稿的结果。
 */
@Data
@Accessors(chain = true)
public class MerchantCampaignDraftSkillResultDTO {

    private Long draftId;

    private Long suggestionId;

    private Long shopId;

    private String draftStatus;

    private Map<String, Object> draftContent = new LinkedHashMap<>();

    private List<String> riskWarnings = new ArrayList<>();

    private List<String> confirmFields = new ArrayList<>();

    private Boolean needHumanConfirm = true;

    private String riskLevel;

    private Map<String, Object> metadata = new LinkedHashMap<>();
}
