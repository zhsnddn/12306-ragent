package com.ming.agent12306.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantChatResponse(
        boolean success,
        String answer
) {
}
