package com.ming.agent12306.sso.controller;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.ming.agent12306.common.util.TextEncodingRepairUtil;
import com.ming.agent12306.properties.SsoProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/** SSO 登录与用户态接口控制器 */
@RestController
public class SsoController {

    private final SsoProperties ssoProperties;

    public SsoController(SsoProperties ssoProperties) {
        this.ssoProperties = ssoProperties;
    }

    /**
     * 调试模式登录：跳过SSO直接登录
     */
    @GetMapping("/debug/login")
    public MeResponse debugLogin(HttpServletResponse response) {
        if (!ssoProperties.isDebugMode()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return new MeResponse(null, null, null, false, "调试模式未开启，请在配置中设置 sso.debug-mode=true");
        }

        // 模拟登录
        StpUtil.login("debug-user-001");
        StpUtil.getSession().set("userId", "debug-user-001");
        StpUtil.getSession().set("username", "debug_user");
        StpUtil.getSession().set("realName", "调试用户");

        return new MeResponse(
                "debug-user-001",
                "debug_user",
                "调试用户",
                true,
                "调试登录成功"
        );
    }

    /**
     * 调试模式退出登录
     */
    @GetMapping("/debug/logout")
    public void debugLogout(HttpServletResponse response) {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    @GetMapping("/sso/login")
    public void login(
            HttpServletResponse response,
            @RequestParam(defaultValue = "/") String back) throws Exception {
        response.sendRedirect(buildSsoLoginUrl(back));
    }

    @GetMapping("/api/me")
    public MeResponse me(HttpServletResponse response) throws Exception {
        if (!StpUtil.isLogin()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "not login");
            return null;
        }
        SaSession session = StpUtil.getSession();
        return new MeResponse(
                stringify(session.get("userId")),
                stringify(session.get("username")),
                TextEncodingRepairUtil.repairUtf8Mojibake(stringify(session.get("realName")))
        );
    }

    @GetMapping("/logout")
    public void logout(
            HttpServletResponse response,
            @RequestParam(defaultValue = "/") String back) throws Exception {
        response.sendRedirect(buildSsoLogoutUrl(back));
    }

    public record MeResponse(
            String userId,
            String username,
            String realName,
            boolean debugMode,
            String message
    ) {
        public MeResponse(String userId, String username, String realName) {
            this(userId, username, realName, false, null);
        }
    }

    private String buildSsoLoginUrl(String back) {
        String authEndpoint = UriComponentsBuilder.fromUriString(ssoProperties.getServerUrl())
                .path("/api/user-service/sso/auth")
                .build()
                .toUriString();
        return authEndpoint + "?mode=simple&redirect=" + encodeQueryValue(buildAppUrl(back));
    }

    private String buildSsoLogoutUrl(String back) {
        String signoutEndpoint = UriComponentsBuilder.fromUriString(ssoProperties.getServerUrl())
                .path("/api/user-service/sso/signout")
                .build()
                .toUriString();
        return signoutEndpoint + "?back=" + encodeQueryValue(buildAppUrl(back));
    }

    private String buildAppUrl(String back) {
        return UriComponentsBuilder.fromUriString(ssoProperties.getAppOrigin())
                .path(normalizeBackPath(back))
                .build()
                .toUriString();
    }

    private String normalizeBackPath(String back) {
        if (back == null || back.isBlank()) {
            return "/";
        }
        return back.startsWith("/") ? back : "/" + back;
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
