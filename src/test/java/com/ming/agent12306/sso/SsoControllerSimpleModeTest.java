package com.ming.agent12306.sso;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;
import com.ming.agent12306.properties.SsoProperties;
import com.ming.agent12306.sso.controller.SsoController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SsoControllerSimpleModeTest {

    private static final String LOGIN_ID = "1683025552364568576";

    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private SaTokenContext originalContext;
    private SaTokenDaoDefaultImpl testDao;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SsoController ssoController;

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalContext = SaManager.getSaTokenContext();

        SaManager.setConfig(new SaTokenConfig()
                .setTokenName("satoken")
                .setTimeout(1800)
                .setActiveTimeout(-1)
                .setIsConcurrent(true)
                .setIsShare(true)
                .setIsReadCookie(true)
                .setIsReadHeader(true)
                .setIsWriteHeader(false)
                .setIsPrint(false)
                .setIsLog(false));

        testDao = new SaTokenDaoDefaultImpl();
        SaManager.setSaTokenDao(testDao);
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SaTokenContextForThreadLocalStorage.setBox(
                new SaRequestForServlet(request),
                new SaResponseForServlet(response),
                new SaStorageForServlet(request)
        );

        SsoProperties ssoProperties = new SsoProperties();
        ssoProperties.setServerUrl("http://localhost:9001");
        ssoProperties.setAppOrigin("http://localhost:8080");
        ssoController = new SsoController(ssoProperties);
    }

    @AfterEach
    void tearDown() {
        try {
            StpUtil.logout(LOGIN_ID);
        } catch (Exception ignored) {
            // Ignore cleanup failures when login never completed.
        }
        SaTokenContextForThreadLocalStorage.clearBox();
        if (testDao != null) {
            testDao.destroy();
        }
        SaManager.setConfig(originalConfig);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setSaTokenContext(originalContext);
    }

    @Test
    void meReturns401WhenNoSaTokenLoginExists() throws Exception {
        SsoController.MeResponse result = ssoController.me(response);

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void loginRedirectUsesSimpleModeAgainstLocalhostUserService() throws Exception {
        ssoController.login(response, "/assistant");

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "http://localhost:9001/api/user-service/sso/auth?mode=simple&redirect=http%3A%2F%2Flocalhost%3A8080%2Fassistant"
        );
    }

    @Test
    void meReturnsCurrentUserFromSharedSaTokenSession() throws Exception {
        StpUtil.login(LOGIN_ID);
        SaSession session = StpUtil.getSession();
        session.set("userId", LOGIN_ID);
        session.set("username", "admin");
        session.set("realName", "徐万里");

        SsoController.MeResponse result = ssoController.me(response);

        assertThat(result.userId()).isEqualTo(LOGIN_ID);
        assertThat(result.username()).isEqualTo("admin");
        assertThat(result.realName()).isEqualTo("徐万里");
    }

    @Test
    void meRepairsLegacyMojibakeAlreadyStoredInSharedSaTokenSession() throws Exception {
        StpUtil.login(LOGIN_ID);
        SaSession session = StpUtil.getSession();
        session.set("userId", LOGIN_ID);
        session.set("username", "admin");
        session.set("realName", "å¾ä¸‡é‡Œ");

        SsoController.MeResponse result = ssoController.me(response);

        assertThat(result.realName()).isEqualTo("徐万里");
    }
}
