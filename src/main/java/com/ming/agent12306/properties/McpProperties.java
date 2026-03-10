package com.ming.agent12306.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** MCP 工具配置属性 */
@Data
@ConfigurationProperties(prefix = "assistant.mcp")
public class McpProperties {

    private boolean enabled = true;
    private String name = "ticketing-mcp";
    private String command = "java";
    private String jarPath = "../12306-mcp/target/12306-mcp-0.0.1-SNAPSHOT.jar";
    private long timeoutSeconds = 30;
    private long initializationTimeoutSeconds = 30;
}
