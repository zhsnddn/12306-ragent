package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;

import java.util.List;

public interface KnowledgeVectorStore {

    void upsert(String documentId, List<String> chunkIds, List<String> chunks);

    void delete(List<String> chunkIds);

    List<KnowledgeChunkRecall> search(String query, int topK, double minScore);
}
