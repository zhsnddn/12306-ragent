package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;
import com.ming.agent12306.properties.AssistantKnowledgeProperties;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.MilvusStore;
import io.milvus.v2.common.IndexParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 基于 Milvus 的知识向量存储实现 */
@Component
@ConditionalOnProperty(prefix = "assistant.knowledge.milvus", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MilvusKnowledgeVectorStore implements KnowledgeVectorStore, AutoCloseable {

    private final EmbeddingModel embeddingModel;
    private final MilvusStore milvusStore;

    public MilvusKnowledgeVectorStore(
            AssistantKnowledgeProperties knowledgeProperties,
            EmbeddingModel embeddingModel) throws VectorStoreException {
        this.embeddingModel = embeddingModel;
        this.milvusStore = MilvusStore.builder()
                .uri(knowledgeProperties.getMilvus().getUri())
                .collectionName(knowledgeProperties.getMilvus().getCollectionName())
                .dimensions(knowledgeProperties.getMilvus().getDimensions())
                .token(knowledgeProperties.getMilvus().getToken())
                .metricType(IndexParam.MetricType.COSINE)
                .build();
    }

    @Override
    public void upsert(String documentId, List<String> chunkIds, List<String> chunks) {
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            String chunkId = chunkIds.get(index);
            TextBlock textBlock = TextBlock.builder().text(chunk).build();
            double[] embedding = embeddingModel.embed(textBlock).block();
            if (embedding == null) {
                continue;
            }
            Document document = new Document(new DocumentMetadata(textBlock, documentId, chunkId));
            document.setEmbedding(embedding);
            documents.add(document);
        }
        if (!documents.isEmpty()) {
            milvusStore.add(documents).block();
        }
    }

    @Override
    public void delete(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        chunkIds.forEach(chunkId -> milvusStore.delete(chunkId).block());
    }

    @Override
    public List<KnowledgeChunkRecall> search(String query, int topK, double minScore) {
        TextBlock textBlock = TextBlock.builder().text(query).build();
        double[] embedding = embeddingModel.embed(textBlock).block();
        if (embedding == null) {
            return List.of();
        }

        List<Document> documents = milvusStore.search(embedding, topK, minScore).block();
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .filter(document -> document.getMetadata() != null)
                .map(document -> new KnowledgeChunkRecall(
                        document.getMetadata().getDocId(),
                        null,
                        null,
                        document.getMetadata().getContentText()
                ))
                .toList();
    }

    @Override
    public void close() {
        milvusStore.close();
    }
}
