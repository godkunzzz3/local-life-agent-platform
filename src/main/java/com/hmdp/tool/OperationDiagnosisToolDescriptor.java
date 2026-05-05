package com.hmdp.tool;

import com.hmdp.dto.AgentToolDefinitionDTO;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 综合运营诊断工具描述。
 *
 * <p>综合诊断不是单一数据库查询，而是店铺、订单、优惠券、评价多个工具结果的组合。
 * 单独暴露描述，是为了让 Agent 知道“泛运营问题”应该走综合诊断链路。</p>
 */
@Component
public class OperationDiagnosisToolDescriptor implements AgentToolDescriptor {

    @Override
    public AgentToolDefinitionDTO definition() {
        return new AgentToolDefinitionDTO()
                .setName("operation_diagnosis_tool")
                .setDisplayName("综合运营诊断工具")
                .setDescription("组合店铺画像、订单分析、优惠券结构和评价内容，生成综合运营上下文。")
                .setCategory("operation")
                .setAccessLevel("read")
                .setRequireMerchantConfirm(false)
                .setWriteDatabase(false)
                .setInputSchema("{\"shopId\":\"店铺ID\",\"dateRange\":\"统计时间范围\"}")
                .setOutputSchema("综合 Map：shopProfile、orderAnalysis、voucherAnalysis、reviewAnalysis、recommendations")
                .setRiskLevel("low")
                .setExamples(Collections.singletonList("商家询问整体经营情况或不知道该问什么时，调用综合诊断工具"));
    }
}
