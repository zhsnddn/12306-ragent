package com.ming.agent12306.memory.service;

import com.ming.agent12306.properties.AssistantMemoryProperties;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.MilvusStore;
import io.milvus.v2.common.IndexParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 基于 Milvus 的记忆向量存储实现 */
@Component
@ConditionalOnProperty(prefix = "assistant.memory.milvus", name = "enabled", havingValue = "true")
public class MilvusMemoryVectorStore implements MemoryVectorStore, AutoCloseable {

    private final EmbeddingModel embeddingModel;
    private final MilvusStore milvusStore;

    public MilvusMemoryVectorStore(
            AssistantMemoryProperties memoryProperties,
            EmbeddingModel embeddingModel) throws VectorStoreException {
        this.embeddingModel = embeddingModel;
        this.milvusStore = MilvusStore.builder()
                .uri(memoryProperties.getMilvus().getUri())
                .collectionName(memoryProperties.getMilvus().getCollectionName())
                .dimensions(memoryProperties.getMilvus().getDimensions())
                .token(memoryProperties.getMilvus().getToken())
                .metricType(IndexParam.MetricType.COSINE)
                .build();
    }

    @Override
    public void upsertSummaryVector(String sessionId, String docId, String summaryText) {
        deleteSummaryVector(docId);

        TextBlock textBlock = TextBlock.builder().text(summaryText).build();
        double[] embedding = embeddingModel.embed(textBlock).block();
        if (embedding == null) {
            return;
        }

        Document document = new Document(new DocumentMetadata(textBlock, sessionId, docId));
        document.setEmbedding(embedding);
        milvusStore.add(List.of(document)).block();
    }

    @Override
    public void deleteSummaryVector(String docId) {
        milvusStore.delete(docId).block();
    }

    @Override
    public List<String> searchRelevantSummaries(String sessionId, String query, int topK, double minScore) {
        TextBlock textBlock = TextBlock.builder().text(query).build();
        double[] embedding = embeddingModel.embed(textBlock).block();
        if (embedding == null) {
            return List.of();
        }

        List<Document> documents = milvusStore.search(embedding, Math.max(topK * 3, topK), minScore).block();
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .filter(document -> document.getMetadata() != null)
                .filter(document -> Objects.equals(sessionId, document.getMetadata().getDocId()))
                .map(Document::getMetadata)
                .map(DocumentMetadata::getContentText)
                .filter(Objects::nonNull)
                .distinct()
                .limit(topK)
                .toList();
    }

    @Override
    public void close() {
        milvusStore.close();
    }
}
