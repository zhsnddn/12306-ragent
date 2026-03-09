package com.ming.agent12306.sso.service;

import com.ming.agent12306.properties.SsoProperties;
import com.ming.agent12306.sso.model.SsoUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class SsoHttpServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private SsoHttpService ssoHttpService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        SsoProperties properties = new SsoProperties();
        properties.setServerUrl("http://127.0.0.1:9006");
        properties.setClient("agent12306-client");
        properties.setCallbackUrl("http://127.0.0.1:8080/sso/callback");

        ssoHttpService = new SsoHttpService(restTemplate, properties);
    }

    @Test
    void shouldBuildAuthUrl() {
        String url = ssoHttpService.buildAuthUrl("/api/me");
        assertEquals(
                "http://127.0.0.1:9006/api/user-service/sso/auth?client=agent12306-client&redirect=http://127.0.0.1:8080/sso/callback?back=/api/me",
                url
        );
    }

    @Test
    void shouldCheckTicketAndFetchUserInfo() {
        mockServer.expect(once(), requestTo("http://127.0.0.1:9006/api/user-service/sso/checkTicket?client=agent12306-client&ticket=ticket-123"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"code\":200,\"msg\":\"ok\",\"data\":\"1683025552364568576\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(once(), requestTo("http://127.0.0.1:9006/api/user-service/sso/userinfo?loginId=1683025552364568576"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"code\":200,\"msg\":\"ok\",\"data\":{\"userId\":\"1683025552364568576\",\"username\":\"admin\",\"realName\":\"徐万里\"}}",
                        MediaType.APPLICATION_JSON
                ));

        String loginId = ssoHttpService.checkTicket("ticket-123");
        assertEquals("1683025552364568576", loginId);

        SsoUserInfo userInfo = ssoHttpService.getUserInfo(loginId);
        assertEquals("1683025552364568576", userInfo.userId());
        assertEquals("admin", userInfo.username());
        assertEquals("徐万里", userInfo.realName());

        mockServer.verify();
    }
}
