package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Override
    public Result queryCurrentUserInfo() {
        Long userId = UserHolder.getUser().getId();
        UserInfo info = getById(userId);
        if (info == null) {
            // 前端编辑页第一次打开时可能没有 tb_user_info 记录，返回一个带 userId 的空对象更好处理。
            info = new UserInfo();
            info.setUserId(userId);
        } else {
            info.setCreateTime(null);
            info.setUpdateTime(null);
        }
        return Result.ok(info);
    }

    @Override
    public Result updateCurrentUserInfo(UserInfo userInfo) {
        Long userId = UserHolder.getUser().getId();
        userInfo.setUserId(userId);

        UserInfo old = getById(userId);
        if (old == null) {
            // 个人简介、城市、性别、生日等扩展资料存在 tb_user_info，首次编辑就插入。
            save(userInfo);
        } else {
            // 只更新当前登录用户自己的扩展资料，避免前端传入别人的 userId。
            updateById(userInfo);
        }
        return Result.ok();
    }

    @Override
    public Result queryCredits() {
        Long userId = UserHolder.getUser().getId();
        UserInfo info = getOrCreateInfo(userId);
        return Result.ok(info.getCredits() == null ? 0 : info.getCredits());
    }

    @Override
    public Result openVip() {
        Long userId = UserHolder.getUser().getId();
        UserInfo info = getOrCreateInfo(userId);
        // 当前表里 level 是 Boolean 类型，这里按是否开通会员处理。
        info.setLevel(true);
        updateById(info);
        return Result.ok();
    }

    private UserInfo getOrCreateInfo(Long userId) {
        UserInfo info = getById(userId);
        if (info == null) {
            info = new UserInfo();
            info.setUserId(userId);
            info.setCredits(0);
            info.setLevel(false);
            save(info);
        }
        return info;
    }
}
