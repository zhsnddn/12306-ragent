package com.ming.agent12306.service;

import com.ming.agent12306.common.validation.AssistantRequestValidator;
import com.ming.agent12306.memory.service.AssistantMemoryService;
import com.ming.agent12306.model.request.AssistantChatRequest;
import com.ming.agent12306.model.response.AssistantChatResponse;
import com.ming.agent12306.model.response.AssistantStructuredAnswer;
import com.ming.agent12306.model.response.AssistantStreamEvent;
import com.ming.agent12306.plan.model.FinalAnswerStepResult;
import com.ming.agent12306.plan.model.PlanningExecutionResult;
import com.ming.agent12306.plan.service.TravelPlanningService;
import com.ming.agent12306.properties.AssistantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** 智能助手核心服务编排层 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final AssistantProperties assistantProperties;
    private final AssistantRequestValidator requestValidator;
    private final AssistantMemoryService assistantMemoryService;
    private final TravelPlanningService travelPlanningService;

    public AssistantChatResponse chat(AssistantChatRequest request) {
        String sessionId = assistantMemoryService.ensureSessionId(request == null ? null : request.sessionId());
        String message = extractMessage(request);
        requestValidator.validateMessage(message);
        requestValidator.validateApiKey(assistantProperties.getApiKey());
        PlanningExecutionResult planningResult = travelPlanningService.execute(sessionId, message);
        return new AssistantChatResponse(true, sessionId, planningResult.answer(), toStructuredAnswer(planningResult.structuredAnswer()));
    }

    public Flux<AssistantStreamEvent> streamChat(AssistantChatRequest request) {
        return Flux.defer(() -> {
            String sessionId = assistantMemoryService.ensureSessionId(request == null ? null : request.sessionId());
            String message = extractMessage(request);
            requestValidator.validateMessage(message);
            requestValidator.validateApiKey(assistantProperties.getApiKey());
            return travelPlanningService.streamExecute(sessionId, message);
        });
    }

    private String extractMessage(AssistantChatRequest request) {
        return request == null ? null : request.message();
    }

    private AssistantStructuredAnswer toStructuredAnswer(FinalAnswerStepResult result) {
        if (result == null) {
            return null;
        }
        return new AssistantStructuredAnswer(
                result.getSummary(),
                result.getTicketHighlights(),
                result.getRecommendations(),
                result.getRuleTips()
        );
    }
}
