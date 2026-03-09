package com.ming.agent12306.knowledge.model;

public record KnowledgeDocumentImportResult(
        String documentId,
        String title,
        int chunkCount,
        String bucketName,
        String objectKey
) {
}
