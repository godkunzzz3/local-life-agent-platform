package com.hmdp.controller;

import com.hmdp.dto.MerchantOperationReportRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMerchantAgentFacadeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 商家运营 Agent 控制器。
 *
 * <p>Controller 只负责 HTTP 参数承接和结果返回。真正的跨表编排、报告生成、
 * 建议落库和审计记录全部下沉到 Facade Service，保持接口层轻薄。</p>
 */
@RestController
@RequestMapping("/merchant-agent")
public class MerchantAgentController {

    @Resource
    private IMerchantAgentFacadeService merchantAgentFacadeService;

    /**
     * 生成店铺运营报告。
     *
     * <p>这是 MerchantOperationAgent 的第一个接口。当前先使用 Java 规则生成报告，
     * 后续接入大模型后，可以把这里查询出来的店铺、订单、优惠券、评价统计作为 Agent 工具结果。</p>
     */
    @PostMapping("/shops/{shopId}/operation-report")
    public Result generateOperationReport(@PathVariable("shopId") Long shopId,
                                           @RequestBody(required = false) MerchantOperationReportRequest request) {
        String dateRange = request == null ? null : request.getDateRange();
        return merchantAgentFacadeService.generateOperationReport(shopId, dateRange);
    }
}
