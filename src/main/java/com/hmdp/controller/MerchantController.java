package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IMerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 商家身份控制器。
 */
@RestController
@RequestMapping("/merchant")
public class MerchantController {

    @Resource
    private IMerchantService merchantService;

    /**
     * 查询当前登录用户是否为商家，以及可管理的店铺列表。
     */
    @GetMapping("/me")
    public Result me() {
        return merchantService.queryCurrentMerchant();
    }
}
