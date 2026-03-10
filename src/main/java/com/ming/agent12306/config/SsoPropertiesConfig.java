package com.ming.agent12306.config;

import com.ming.agent12306.properties.SsoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** SSO 配置属性注册类 */
@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoPropertiesConfig {
}
