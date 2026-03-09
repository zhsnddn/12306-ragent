package com.ming.agent12306.sso.controller;

import com.ming.agent12306.sso.model.SsoUserInfo;
import com.ming.agent12306.sso.service.SsoHttpService;
import com.ming.agent12306.sso.support.SsoSessionSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SsoController {

    private final SsoHttpService ssoHttpService;

    public SsoController(SsoHttpService ssoHttpService) {
        this.ssoHttpService = ssoHttpService;
    }

    @GetMapping("/sso/login")
    public void login(
            HttpServletResponse response,
            @RequestParam(defaultValue = "/") String back) throws Exception {
        response.sendRedirect(ssoHttpService.buildAuthUrl(back));
    }

    @GetMapping("/sso/callback")
    public void callback(
            HttpServletResponse response,
            HttpSession session,
            @RequestParam String ticket,
            @RequestParam(defaultValue = "/") String back) throws Exception {
        String loginId = ssoHttpService.checkTicket(ticket);
        SsoUserInfo userInfo = ssoHttpService.getUserInfo(loginId);
        session.setAttribute(SsoSessionSupport.LOGIN_USER_SESSION_KEY, userInfo);
        response.sendRedirect(back);
    }

    @GetMapping("/api/me")
    public SsoUserInfo me(HttpSession session, HttpServletResponse response) throws Exception {
        SsoUserInfo userInfo = SsoSessionSupport.getLoginUser(session);
        if (userInfo != null) {
            return userInfo;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "not login");
        return null;
    }

    @GetMapping("/logout")
    public void logout(
            HttpServletResponse response,
            HttpSession session,
            @RequestParam(defaultValue = "/") String back) throws Exception {
        session.invalidate();
        response.sendRedirect(back);
    }
}
