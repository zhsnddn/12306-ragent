package com.ming.agent12306.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sso")
public class SsoProperties {

    private String serverUrl;
    private String client;
    private String callbackUrl;
}
