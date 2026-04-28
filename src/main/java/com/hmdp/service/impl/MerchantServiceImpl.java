package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Merchant;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.MerchantMapper;
import com.hmdp.service.IMerchantService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家身份服务实现。
 *
 * <p>当前项目不拆商家账号体系，仍复用用户登录。这个 Service 只负责判断当前用户
 * 是否在 tb_merchant 中拥有店铺授权。</p>
 */
@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements IMerchantService {

    @Resource
    private IShopService shopService;

    @Override
    public Result queryCurrentMerchant() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        List<Merchant> merchants = query()
                .eq("user_id", user.getId())
                .eq("status", 1)
                .list();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", String.valueOf(user.getId()));
        result.put("nickName", user.getNickName());
        result.put("isMerchant", !merchants.isEmpty());

        List<Map<String, Object>> shops = new ArrayList<>();
        for (Merchant merchant : merchants) {
            Shop shop = shopService.getById(merchant.getShopId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("merchantId", String.valueOf(merchant.getId()));
            row.put("shopId", merchant.getShopId());
            row.put("shopName", shop == null ? "未知店铺" : shop.getName());
            row.put("shopImage", shop == null ? "" : firstImage(shop.getImages()));
            row.put("role", merchant.getRole());
            shops.add(row);
        }
        result.put("shops", shops);
        return Result.ok(result);
    }

    @Override
    public boolean hasCurrentUserShopPermission(Long shopId) {
        UserDTO user = UserHolder.getUser();
        if (user == null || shopId == null) {
            return false;
        }
        return query()
                .eq("user_id", user.getId())
                .eq("shop_id", shopId)
                .eq("status", 1)
                .count() > 0;
    }

    private String firstImage(String images) {
        if (images == null || images.trim().isEmpty()) {
            return "/imgs/icons/default-icon.png";
        }
        return images.split(",")[0];
    }
}
