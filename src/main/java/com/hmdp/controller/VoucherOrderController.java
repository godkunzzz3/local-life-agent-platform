package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 购买普通代金券。
     *
     * <p>企业项目里 Controller 只做参数承接和结果返回，登录态校验、业务规则、
     * 订单幂等边界都放在 Service 层，避免接口层掺入过多业务细节。</p>
     */
    @PostMapping("{id}")
    public Result buyVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.buyVoucher(voucherId);
    }

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 查询当前登录用户的券订单。
     *
     * <p>用户只能看到自己的订单，userId 一律从登录上下文读取，不能相信前端传参。</p>
     */
    @GetMapping("my")
    public Result queryMyOrders() {
        return voucherOrderService.queryMyOrders();
    }
}
