package com.ming.agent12306.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 智能助手对话响应模型 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantChatResponse(
        boolean success,
        String sessionId,
        String answer,
        AssistantStructuredAnswer structuredAnswer
) {
}
