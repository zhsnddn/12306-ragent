package com.ming.agent12306.memory.model;

import java.util.List;

public record ConversationContext(
        String sessionId,
        String summary,
        List<ConversationMessage> recentMessages,
        List<String> recalledMemories
) {
}
