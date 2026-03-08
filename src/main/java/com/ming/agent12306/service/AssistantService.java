package com.ming.agent12306.service;

import com.ming.agent12306.model.request.AssistantChatRequest;
import com.ming.agent12306.model.response.AssistantChatResponse;
import com.ming.agent12306.model.response.AssistantStreamEvent;
import com.ming.agent12306.properties.AssistantProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AssistantService {

    private final AssistantProperties assistantProperties;
    private final DashScopeChatModel dashScopeChatModel;
    private final Toolkit toolkit;

    public AssistantService(
            AssistantProperties assistantProperties,
            DashScopeChatModel dashScopeChatModel,
            Toolkit toolkit) {
        this.assistantProperties = assistantProperties;
        this.dashScopeChatModel = dashScopeChatModel;
        this.toolkit = toolkit;
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String message = extractMessage(request);
        if (!StringUtils.hasText(message)) {
            return new AssistantChatResponse(false, "message 不能为空");
        }
        if (!hasApiKey()) {
            return new AssistantChatResponse(false, "未配置 assistant.api-key 或环境变量 DASHSCOPE_API_KEY");
        }

        Msg response = createAgent().call(List.of(createUserMessage(message))).block();
        String answer = response == null ? "未获取到模型响应" : response.getTextContent();
        return new AssistantChatResponse(true, answer);
    }

    public Flux<AssistantStreamEvent> streamChat(AssistantChatRequest request) {
        String message = extractMessage(request);
        if (!StringUtils.hasText(message)) {
            return Flux.just(new AssistantStreamEvent("error", true, MsgRole.SYSTEM.name(), "message 不能为空", null));
        }
        if (!hasApiKey()) {
            return Flux.just(new AssistantStreamEvent(
                    "error",
                    true,
                    MsgRole.SYSTEM.name(),
                    "未配置 assistant.api-key 或环境变量 DASHSCOPE_API_KEY",
                    null
            ));
        }

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .build();

        return createAgent()
                .stream(List.of(createUserMessage(message)), streamOptions)
                .map(this::toStreamEvent);
    }

    private ReActAgent createAgent() {
        return ReActAgent.builder()
                .name("12306智能客服")
                .description("一个接入12306 MCP工具的智能客服助手")
                .sysPrompt(assistantProperties.getSystemPrompt())
                .model(dashScopeChatModel)
                .toolkit(toolkit)
                .maxIters(assistantProperties.getMaxIters())
                .build();
    }

    private Msg createUserMessage(String message) {
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(message)
                .build();
    }

    private AssistantStreamEvent toStreamEvent(Event event) {
        Msg message = event.getMessage();
        return new AssistantStreamEvent(
                event.getType().name().toLowerCase(),
                event.isLast(),
                message == null || message.getRole() == null ? null : message.getRole().name(),
                message == null ? null : message.getTextContent(),
                event.getMessageId()
        );
    }

    private boolean hasApiKey() {
        return StringUtils.hasText(assistantProperties.getApiKey());
    }

    private String extractMessage(AssistantChatRequest request) {
        return request == null ? null : request.message();
    }
}
