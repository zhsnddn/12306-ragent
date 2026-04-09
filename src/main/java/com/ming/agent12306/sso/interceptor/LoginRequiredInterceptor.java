package com.ming.agent12306.sso.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.ming.agent12306.properties.SsoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 登录态校验拦截器 */
@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginRequiredInterceptor.class);

    private final SsoProperties ssoProperties;

    public LoginRequiredInterceptor(SsoProperties ssoProperties) {
        this.ssoProperties = ssoProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 调试模式：跳过登录验证
        if (ssoProperties.isDebugMode()) {
            log.debug("[auth] debug mode enabled, skipping login check for {}", request.getRequestURI());
            // 在调试模式下，创建一个虚拟的登录会话
            if (!StpUtil.isLogin()) {
                StpUtil.login("debug-user-001");
                StpUtil.getSession().set("userId", "debug-user-001");
                StpUtil.getSession().set("username", "debug_user");
                StpUtil.getSession().set("realName", "调试用户");
            }
            return true;
        }

        if (StpUtil.isLogin()) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "not login");
        return false;
    }
}
