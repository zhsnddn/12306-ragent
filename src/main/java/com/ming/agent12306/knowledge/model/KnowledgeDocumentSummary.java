package com.ming.agent12306.knowledge.model;

import java.time.LocalDateTime;

public record KnowledgeDocumentSummary(
        String documentId,
        String title,
        String fileName,
        String category,
        String parseStatus,
        String progressMessage,
        int chunkCount,
        LocalDateTime uploadedAt,
        LocalDateTime updatedAt
) {
}
