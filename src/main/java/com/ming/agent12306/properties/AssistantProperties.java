package com.ming.agent12306.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    private String model = "qwen-max";
    private String apiKey;
    private String systemPrompt = "你是12306智能客服助手。遇到票务、车站、经停站相关问题时，优先调用MCP工具核实信息，再用中文给出简洁、准确的回答。";
    private int maxIters = 5;

}
