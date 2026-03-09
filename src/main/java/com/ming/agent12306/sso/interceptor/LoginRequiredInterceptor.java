package com.ming.agent12306.sso.interceptor;

import com.ming.agent12306.sso.support.SsoSessionSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (SsoSessionSupport.getLoginUser(session) != null) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "not login");
        return false;
    }
}
