package com.hmdp.service;

import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

    Result queryCurrentUserInfo();

    Result updateCurrentUserInfo(UserInfo userInfo);

    Result queryCredits();

    Result openVip();
}
