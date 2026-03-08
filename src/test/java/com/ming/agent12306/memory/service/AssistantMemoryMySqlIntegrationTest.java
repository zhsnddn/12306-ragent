package com.ming.agent12306.memory.service;

import com.ming.agent12306.memory.mapper.ConversationMessageMapper;
import com.ming.agent12306.memory.mapper.ConversationSessionMapper;
import com.ming.agent12306.memory.model.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/agent12306_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true",
        "spring.datasource.username=root",
        "spring.datasource.password=123456",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "assistant.memory.enabled=true",
        "assistant.memory.milvus.enabled=false",
        "assistant.api-key=test-key"
})
class AssistantMemoryMySqlIntegrationTest {

    @Autowired
    private AssistantMemoryService assistantMemoryService;

    @Autowired
    private ConversationSessionMapper conversationSessionMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @BeforeEach
    void setUp() {
        conversationMessageMapper.delete(null);
        conversationSessionMapper.delete(null);
    }

    @Test
    void shouldPersistMessagesAndLoadRecentContextFromMySql() {
        String sessionId = assistantMemoryService.ensureSessionId(null);

        assistantMemoryService.appendMessage(sessionId, "user", "北京到上海");
        assistantMemoryService.appendMessage(sessionId, "assistant", "请问哪天出发");
        assistantMemoryService.appendMessage(sessionId, "user", "明天");

        ConversationContext context = assistantMemoryService.loadContext(sessionId, "查一下明天北京到上海");

        assertNotNull(context);
        assertEquals(sessionId, context.sessionId());
        assertEquals(3, context.recentMessages().size());
        assertEquals("北京到上海", context.recentMessages().get(0).content());
        assertEquals("明天", context.recentMessages().get(2).content());
        assertEquals(0, context.recalledMemories().size());
    }
}
