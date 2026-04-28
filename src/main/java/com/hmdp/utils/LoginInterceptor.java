package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // RefreshTokenInterceptor 会先解析 token 并放入 ThreadLocal，这里只负责判断是否已登录。
        UserDTO user = UserHolder.getUser();

        if (user == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
