package com.ming.agent12306.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** SSO 接入配置属性 */
@Data
@ConfigurationProperties(prefix = "sso")
public class SsoProperties {

    private String serverUrl;
    private String appOrigin;

    /**
     * 调试模式：跳过单点登录验证
     * 开启后可直接访问受保护接口，无需登录
     */
    private boolean debugMode = false;
}
