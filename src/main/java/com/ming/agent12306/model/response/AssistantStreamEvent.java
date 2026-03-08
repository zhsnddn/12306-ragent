package com.ming.agent12306.model.response;

public record AssistantStreamEvent(
        String type,
        boolean last,
        String role,
        String text,
        String messageId
) {
}
