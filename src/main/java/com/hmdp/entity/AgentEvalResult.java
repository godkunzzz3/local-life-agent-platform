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
 * Agent 行为评测明细。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_eval_result")
public class AgentEvalResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long runId;
    private Long caseId;
    private String caseName;
    private String userInput;
    private String expectedIntent;
    private String actualIntent;
    private String expectedTools;
    private String actualTools;
    private Integer expectedNeedConfirm;
    private Integer actualNeedConfirm;
    private String expectedRiskLevel;
    private String actualRiskLevel;
    private Integer intentPassed;
    private Integer toolPassed;
    private Integer confirmPassed;
    private Integer riskPassed;
    private Integer passed;
    private BigDecimal score;
    private String diagnosis;
    private String detailJson;
    private LocalDateTime createTime;
}
