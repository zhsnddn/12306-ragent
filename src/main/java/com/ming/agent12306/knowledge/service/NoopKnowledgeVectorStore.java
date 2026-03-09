package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "assistant.knowledge.milvus", name = "enabled", havingValue = "false")
public class NoopKnowledgeVectorStore implements KnowledgeVectorStore {

    @Override
    public void upsert(String documentId, List<String> chunkIds, List<String> chunks) {
    }

    @Override
    public void delete(List<String> chunkIds) {
    }

    @Override
    public List<KnowledgeChunkRecall> search(String query, int topK, double minScore) {
        return List.of();
    }
}
