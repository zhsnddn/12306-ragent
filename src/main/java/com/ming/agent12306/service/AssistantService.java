package com.ming.agent12306.service;

import com.ming.agent12306.common.preprocess.AssistantMessagePreprocessor;
import com.ming.agent12306.common.preprocess.AssistantPreprocessResult;
import com.ming.agent12306.common.validation.AssistantRequestValidator;
import com.ming.agent12306.common.constant.AssistantErrorMessagesConstant;
import com.ming.agent12306.common.exception.BusinessException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantProperties assistantProperties;
    private final DashScopeChatModel dashScopeChatModel;
    private final Toolkit toolkit;
    private final AssistantMessagePreprocessor messagePreprocessor;
    private final AssistantRequestValidator requestValidator;

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String message = extractMessage(request);
        requestValidator.validateMessage(message);
        requestValidator.validateApiKey(assistantProperties.getApiKey());

        AssistantPreprocessResult preprocessResult = messagePreprocessor.preprocess(message);
        if (!preprocessResult.success()) {
            throw new BusinessException(preprocessResult.message());
        }

        Msg response = createAgent().call(List.of(createUserMessage(preprocessResult.message()))).block();
        String answer = response == null ? AssistantErrorMessagesConstant.EMPTY_MODEL_RESPONSE : response.getTextContent();
        return new AssistantChatResponse(true, answer);
    }

    public Flux<AssistantStreamEvent> streamChat(AssistantChatRequest request) {
        String message = extractMessage(request);
        requestValidator.validateMessage(message);
        requestValidator.validateApiKey(assistantProperties.getApiKey());

        AssistantPreprocessResult preprocessResult = messagePreprocessor.preprocess(message);
        if (!preprocessResult.success()) {
            return Flux.error(new BusinessException(preprocessResult.message()));
        }

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .build();

        return createAgent()
                .stream(List.of(createUserMessage(preprocessResult.message())), streamOptions)
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

    private String extractMessage(AssistantChatRequest request) {
        return request == null ? null : request.message();
    }
}
