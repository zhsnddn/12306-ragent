package com.ming.agent12306.sso.support;

import com.ming.agent12306.sso.model.SsoUserInfo;
import jakarta.servlet.http.HttpSession;

/** SSO 会话读取辅助工具 */
public final class SsoSessionSupport {

    public static final String LOGIN_USER_SESSION_KEY = "loginUser";

    private SsoSessionSupport() {
    }

    public static SsoUserInfo getLoginUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object loginUser = session.getAttribute(LOGIN_USER_SESSION_KEY);
        return loginUser instanceof SsoUserInfo userInfo ? userInfo : null;
    }
}
