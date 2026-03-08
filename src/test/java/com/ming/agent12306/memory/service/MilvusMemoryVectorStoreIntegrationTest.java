package com.ming.agent12306.memory.service;

import com.ming.agent12306.properties.AssistantMemoryProperties;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilvusMemoryVectorStoreIntegrationTest {

    @Test
    void shouldSearchRelevantSummariesBySession() throws Exception {
        AssistantMemoryProperties properties = new AssistantMemoryProperties();
        properties.getMilvus().setEnabled(true);
        properties.getMilvus().setUri("http://127.0.0.1:19530");
        properties.getMilvus().setCollectionName("assistant_memory_test");
        properties.getMilvus().setDimensions(1024);

        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public Mono<double[]> embed(ContentBlock contentBlock) {
                double[] vector = new double[1024];
                String text = contentBlock instanceof TextBlock textBlock ? textBlock.getText() : contentBlock.toString();
                if (text.contains("北京") || text.contains("车票") || text.contains("高铁")) {
                    vector[0] = 1.0D;
                }
                if (text.contains("酒店") || text.contains("发票")) {
                    vector[1] = 1.0D;
                }
                if (text.contains("儿童") || text.contains("学生")) {
                    vector[2] = 1.0D;
                }
                return Mono.just(vector);
            }

            @Override
            public String getModelName() {
                return "mock-embedding";
            }

            @Override
            public int getDimensions() {
                return 1024;
            }
        };

        try (MilvusMemoryVectorStore vectorStore = new MilvusMemoryVectorStore(properties, embeddingModel)) {
            String sessionId = "session-it";
            String ticketChunkId = "memory-session-it-ticket";
            String hotelChunkId = "memory-session-it-hotel";
            String otherSessionChunkId = "memory-session-other-ticket";

            vectorStore.deleteSummaryVector(ticketChunkId);
            vectorStore.deleteSummaryVector(hotelChunkId);
            vectorStore.deleteSummaryVector(otherSessionChunkId);

            vectorStore.upsertSummaryVector(sessionId, ticketChunkId, "用户想查明天北京到上海的高铁车票");
            vectorStore.upsertSummaryVector(sessionId, hotelChunkId, "用户还询问了酒店发票报销问题");
            vectorStore.upsertSummaryVector("session-other", otherSessionChunkId, "其他会话提到了北京车票");

            List<String> result = vectorStore.searchRelevantSummaries(sessionId, "帮我找北京车票相关记录", 2, 0.0D);

            assertFalse(result.isEmpty());
            assertTrue(result.stream().anyMatch(item -> item.contains("北京到上海")));
            assertFalse(result.stream().anyMatch(item -> item.contains("其他会话")));

            vectorStore.deleteSummaryVector(ticketChunkId);
            vectorStore.deleteSummaryVector(hotelChunkId);
            vectorStore.deleteSummaryVector(otherSessionChunkId);
        }
    }
}
