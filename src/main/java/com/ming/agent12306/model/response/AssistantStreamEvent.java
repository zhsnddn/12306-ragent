package com.ming.agent12306.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantStreamEvent(
        String type,
        boolean last,
        String role,
        String text,
        String messageId
) {
}
