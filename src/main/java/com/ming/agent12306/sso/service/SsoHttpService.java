package com.ming.agent12306.sso.service;

import com.ming.agent12306.properties.SsoProperties;
import com.ming.agent12306.sso.model.SsoUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** SSO 服务端 HTTP 对接服务 */
@Service
@RequiredArgsConstructor
public class SsoHttpService {

    private final RestTemplate restTemplate;
    private final SsoProperties ssoProperties;

    public String buildAuthUrl(String back) {
        Assert.hasText(ssoProperties.getCallbackUrl(), "sso.callback-url must not be blank");
        String redirect = UriComponentsBuilder.fromUriString(ssoProperties.getCallbackUrl())
                .queryParam("back", back)
                .build()
                .toUriString();
        return UriComponentsBuilder.fromUriString(ssoProperties.getServerUrl())
                .path("/api/user-service/sso/auth")
                .queryParam("client", ssoProperties.getClient())
                .queryParam("redirect", redirect)
                .build()
                .toUriString();
    }

    @SuppressWarnings("unchecked")
    public String checkTicket(String ticket) {
        String url = UriComponentsBuilder.fromUriString(ssoProperties.getServerUrl())
                .path("/api/user-service/sso/checkTicket")
                .queryParam("client", ssoProperties.getClient())
                .queryParam("ticket", ticket)
                .build()
                .toUriString();
        Map<String, Object> result = restTemplate.getForObject(url, Map.class);
        if (result == null || result.get("data") == null) {
            throw new IllegalStateException("checkTicket failed: " + result);
        }
        return String.valueOf(result.get("data"));
    }

    @SuppressWarnings("unchecked")
    public SsoUserInfo getUserInfo(String loginId) {
        String url = UriComponentsBuilder.fromUriString(ssoProperties.getServerUrl())
                .path("/api/user-service/sso/userinfo")
                .queryParam("loginId", loginId)
                .build()
                .toUriString();
        Map<String, Object> result = restTemplate.getForObject(url, Map.class);
        if (result == null || result.get("data") == null) {
            throw new IllegalStateException("userinfo failed: " + result);
        }
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return new SsoUserInfo(
                String.valueOf(data.get("userId")),
                String.valueOf(data.get("username")),
                String.valueOf(data.get("realName"))
        );
    }
}
