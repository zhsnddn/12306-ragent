package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.properties.AssistantKnowledgeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkerTest {

    @Test
    void shouldSplitLargeContentIntoMultipleChunks() {
        AssistantKnowledgeProperties properties = new AssistantKnowledgeProperties();
        properties.setChunkSize(80);
        properties.setChunkOverlap(20);
        KnowledgeChunker chunker = new KnowledgeChunker(properties);

        String content = "第一段介绍12306退票规则。".repeat(10)
                + "\n\n"
                + "第二段介绍候补购票规则。".repeat(10)
                + "\n\n"
                + "第三段介绍改签流程。".repeat(10);

        List<String> chunks = chunker.chunk(content);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(item -> !item.isBlank()));
    }
}
