package com.ming.agent12306.memory.model;

import java.time.LocalDateTime;

/** 会话消息视图模型 */
public record ConversationMessage(
        String role,
        String content,
        LocalDateTime createdAt
) {
}
