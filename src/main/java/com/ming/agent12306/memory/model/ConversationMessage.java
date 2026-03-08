package com.ming.agent12306.memory.model;

import java.time.LocalDateTime;

public record ConversationMessage(
        String role,
        String content,
        LocalDateTime createdAt
) {
}
