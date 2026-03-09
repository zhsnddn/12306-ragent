package com.ming.agent12306.config;

import com.ming.agent12306.properties.SsoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SsoProperties.class)
public class SsoPropertiesConfig {
}
