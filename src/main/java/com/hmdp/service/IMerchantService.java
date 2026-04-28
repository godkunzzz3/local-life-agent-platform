package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Merchant;

/**
 * 商家身份服务。
 */
public interface IMerchantService extends IService<Merchant> {

    /**
     * 查询当前登录用户的商家身份和可管理店铺。
     */
    Result queryCurrentMerchant();

    /**
     * 校验当前登录用户是否拥有指定店铺的商家权限。
     */
    boolean hasCurrentUserShopPermission(Long shopId);
}
