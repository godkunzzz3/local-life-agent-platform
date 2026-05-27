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
 * Agent 行为评测运行记录。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_eval_run")
public class AgentEvalRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String caseSource;
    private Integer totalCount;
    private Integer passCount;
    private Integer failCount;
    private String intentAccuracy;
    private String toolAccuracy;
    private String confirmAccuracy;
    private String riskAccuracy;
    private BigDecimal overallScore;
    private String summary;
    private LocalDateTime createTime;
}
