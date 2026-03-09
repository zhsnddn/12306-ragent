package com.ming.agent12306.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String documentId;
    private String title;
    private String fileName;
    private String category;
    private String bucketName;
    private String objectKey;
    private String contentType;
    private Long fileSize;
    private String parseStatus;
    private String progressMessage;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime uploadedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
