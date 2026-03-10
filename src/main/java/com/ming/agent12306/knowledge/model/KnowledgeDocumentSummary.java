package com.ming.agent12306.knowledge.model;

import java.time.LocalDateTime;

/** 知识文档摘要视图模型 */
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
