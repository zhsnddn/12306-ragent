package com.ming.agent12306.knowledge.model;

/** 知识分块召回结果模型 */
public record KnowledgeChunkRecall(
        String documentId,
        String title,
        String category,
        String content
) {
}
