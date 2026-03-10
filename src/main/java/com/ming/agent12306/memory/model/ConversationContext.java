package com.ming.agent12306.memory.model;

import java.util.List;

/** 会话上下文聚合模型 */
public record ConversationContext(
        String sessionId,
        String summary,
        List<ConversationMessage> recentMessages,
        List<String> recalledMemories
) {
}
