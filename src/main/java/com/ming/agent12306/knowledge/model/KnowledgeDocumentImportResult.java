package com.ming.agent12306.knowledge.model;

/** 知识文档导入结果模型 */
public record KnowledgeDocumentImportResult(
        String documentId,
        String title,
        int chunkCount,
        String bucketName,
        String objectKey
) {
}
