package com.ming.agent12306.service;

import com.ming.agent12306.common.preprocess.AssistantMessagePreprocessor;
import com.ming.agent12306.common.preprocess.AssistantPreprocessResult;
import com.ming.agent12306.common.validation.AssistantRequestValidator;
import com.ming.agent12306.common.constant.AssistantErrorMessagesConstant;
import com.ming.agent12306.common.exception.BusinessException;
import com.ming.agent12306.memory.model.ConversationContext;
import com.ming.agent12306.memory.service.AssistantMemoryService;
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
    private final AssistantMemoryService assistantMemoryService;

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String sessionId = assistantMemoryService.ensureSessionId(request == null ? null : request.sessionId());
        String message = extractMessage(request);
        requestValidator.validateMessage(message);
        requestValidator.validateApiKey(assistantProperties.getApiKey());

        ConversationContext preprocessContext = assistantMemoryService.loadContext(sessionId, message);
        String preprocessInput = assistantMemoryService.buildPreprocessInput(message, preprocessContext);
        AssistantPreprocessResult preprocessResult = messagePreprocessor.preprocess(preprocessInput);
        if (!preprocessResult.success()) {
            throw new BusinessException(preprocessResult.message());
        }

        ConversationContext context = assistantMemoryService.loadContext(sessionId, preprocessResult.message());
        assistantMemoryService.appendMessage(sessionId, "user", message);
        String prompt = assistantMemoryService.buildPromptWithMemory(preprocessResult.message(), context);

        Msg response = createAgent().call(List.of(createUserMessage(prompt))).block();
        String answer = response == null ? AssistantErrorMessagesConstant.EMPTY_MODEL_RESPONSE : response.getTextContent();
        assistantMemoryService.appendMessage(sessionId, "assistant", answer);
        assistantMemoryService.summarizeIfNecessary(sessionId);
        return new AssistantChatResponse(true, sessionId, answer);
    }

    public Flux<AssistantStreamEvent> streamChat(AssistantChatRequest request) {
        return Flux.defer(() -> {
            String sessionId = assistantMemoryService.ensureSessionId(request == null ? null : request.sessionId());
            String message = extractMessage(request);
            requestValidator.validateMessage(message);
            requestValidator.validateApiKey(assistantProperties.getApiKey());

            ConversationContext preprocessContext = assistantMemoryService.loadContext(sessionId, message);
            String preprocessInput = assistantMemoryService.buildPreprocessInput(message, preprocessContext);
            AssistantPreprocessResult preprocessResult = messagePreprocessor.preprocess(preprocessInput);
            if (!preprocessResult.success()) {
                return Flux.just(errorEvent(preprocessResult.message()));
            }

            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.ALL)
                    .incremental(true)
                    .build();

            ConversationContext context = assistantMemoryService.loadContext(sessionId, preprocessResult.message());
            assistantMemoryService.appendMessage(sessionId, "user", message);
            String prompt = assistantMemoryService.buildPromptWithMemory(preprocessResult.message(), context);

            return createAgent()
                    .stream(List.of(createUserMessage(prompt)), streamOptions)
                    .doOnNext(event -> {
                        if (event.isLast() && event.getMessage() != null && event.getMessage().getTextContent() != null) {
                            assistantMemoryService.appendMessage(sessionId, "assistant", event.getMessage().getTextContent());
                            assistantMemoryService.summarizeIfNecessary(sessionId);
                        }
                    })
                    .map(this::toStreamEvent)
                    .onErrorResume(ex -> Flux.just(errorEvent(ex.getMessage())));
        });
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

    private AssistantStreamEvent errorEvent(String message) {
        return new AssistantStreamEvent(
                "error",
                true,
                "ASSISTANT",
                message == null || message.isBlank() ? "系统繁忙，请稍后重试" : message,
                null
        );
    }

    private String extractMessage(AssistantChatRequest request) {
        return request == null ? null : request.message();
    }
}
