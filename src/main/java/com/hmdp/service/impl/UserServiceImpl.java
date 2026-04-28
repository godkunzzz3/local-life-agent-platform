package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
     @Resource
     private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result senDCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone))
        {   return  Result.fail("手机号格式失败");
        }
        String code = RandomUtil.randomNumbers(6);
         //保存验证码到redis

      stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功，验证码：{}",code);
         return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone =loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone))
        {   return  Result.fail("手机号格式失败");
        }
        Object cachecode =stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code =loginForm.getCode();

        if(cachecode ==null|| !cachecode.equals(code)){

            return  Result.fail("验证码错误");
        }
        User user =query().eq("phone",phone).one();
        if(user ==null)
        {
            user= createUserwithphone(phone);
        }


        //保存信息到redis中
        String token =UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().
                setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        String tokenkey = LOGIN_USER_KEY +token;
        stringRedisTemplate.opsForHash().putAll(tokenkey,userMap);
        stringRedisTemplate.expire(tokenkey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (token != null && !token.trim().isEmpty()) {
            // 登录状态存在 Redis 中，退出登录时删除 token 对应的用户 Hash 即可。
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        return Result.ok();
    }

    @Override
    public Result sign() {
        Long userId = com.hmdp.utils.UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        // BitMap 的下标从 0 开始，所以 1 号对应 offset=0。置为 true 表示当天已签到。
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = com.hmdp.utils.UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        int dayOfMonth = now.getDayOfMonth();

        // 取出本月从 1 号到今天的签到位，返回一个无符号整数。例如今天是 10 号，就取 u10。
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                org.springframework.data.redis.connection.BitFieldSubCommands.create()
                        .get(org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }

        int count = 0;
        // 从最低位开始向前数，连续遇到 1 就说明连续签到，遇到 0 立刻结束。
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result updateUser(User user, String token) {
        Long userId = com.hmdp.utils.UserHolder.getUser().getId();
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setNickName(user.getNickName());
        updateUser.setIcon(user.getIcon());

        // 昵称和头像属于 tb_user。这里只允许修改当前登录用户自己的展示信息。
        updateById(updateUser);

        if (token != null && !token.trim().isEmpty()) {
            String tokenKey = LOGIN_USER_KEY + token;
            User freshUser = getById(userId);
            UserDTO userDTO = BeanUtil.copyProperties(freshUser, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                    .setIgnoreNullValue(true)
                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            // 登录态也缓存在 Redis Hash 中，资料更新后同步刷新，前端 /user/me 才能立刻看到新昵称/头像。
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        }
        return Result.ok();
    }

    private User createUserwithphone(String phone) {
        User user =new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return  user;
    }
}
