package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.senDCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        return userService.logout(request.getHeader("authorization"));
    }

    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    @GetMapping("/me")
    public Result me(){
        // 1. 从保安刚才放好信息的 ThreadLocal (UserHolder) 里获取当前用户
        UserDTO user = com.hmdp.utils.UserHolder.getUser();

        // 2. 将用户信息包装在 Result.ok() 中返回给前端
        return Result.ok(user);
    }

    @PutMapping
    public Result updateUser(@RequestBody User user, HttpServletRequest request) {
        return userService.updateUser(user, request.getHeader("authorization"));
    }

    @PostMapping
    public Result updateUserByPost(@RequestBody User user, HttpServletRequest request) {
        // 有些前端页面用 POST 提交编辑表单，这里兼容一下，逻辑仍然走同一个 Service。
        return userService.updateUser(user, request.getHeader("authorization"));
    }

    @GetMapping("/info")
    public Result currentUserInfo() {
        return userInfoService.queryCurrentUserInfo();
    }

    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfo userInfo) {
        return userInfoService.updateCurrentUserInfo(userInfo);
    }

    @PostMapping("/info")
    public Result updateInfoByPost(@RequestBody UserInfo userInfo) {
        return userInfoService.updateCurrentUserInfo(userInfo);
    }

    @GetMapping("/credits")
    public Result credits() {
        return userInfoService.queryCredits();
    }

    @PostMapping("/vip")
    public Result openVip() {
        return userInfoService.openVip();
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
