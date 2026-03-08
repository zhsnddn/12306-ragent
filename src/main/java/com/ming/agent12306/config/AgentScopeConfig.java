package com.ming.agent12306.config;

import com.ming.agent12306.properties.AssistantProperties;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import com.ming.agent12306.properties.McpProperties;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Configuration
public class AgentScopeConfig {

    @Bean
    public DashScopeChatModel dashScopeChatModel(AssistantProperties assistantProperties) {
        return DashScopeChatModel.builder()
                .apiKey(assistantProperties.getApiKey())
                .modelName(assistantProperties.getModel())
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(AssistantProperties assistantProperties) {
        return DashScopeTextEmbedding.builder()
                .apiKey(assistantProperties.getApiKey())
                .modelName("text-embedding-v4")
                .dimensions(1024)
                .build();
    }

    @Bean(destroyMethod = "close")
    public McpClientWrapper mcpClientWrapper(McpProperties mcpProperties) {
        if (!mcpProperties.isEnabled()) {
            return null;
        }

        Path jarPath = Path.of(mcpProperties.getJarPath()).toAbsolutePath().normalize();
        if (!Files.exists(jarPath)) {
            throw new IllegalStateException("MCP jar not found: " + jarPath);
        }

        return McpClientBuilder.create(mcpProperties.getName())
                .stdioTransport(
                        mcpProperties.getCommand(),
                        List.of(
                                "-Dspring.main.banner-mode=off",
                                "-Dlogging.level.root=OFF",
                                "-jar",
                                jarPath.toString()
                        ),
                        java.util.Map.of()
                )
                .timeout(Duration.ofSeconds(mcpProperties.getTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(mcpProperties.getInitializationTimeoutSeconds()))
                .buildSync();
    }

    @Bean
    public Toolkit toolkit(McpClientWrapper mcpClientWrapper) {
        Toolkit toolkit = new Toolkit();
        if (mcpClientWrapper != null) {
            mcpClientWrapper.initialize().block();
            toolkit.registerMcpClient(mcpClientWrapper).block();
        }
        return toolkit;
    }
}
