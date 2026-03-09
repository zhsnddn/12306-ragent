package com.ming.agent12306.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String chunkId;
    private String documentId;
    private Integer chunkIndex;
    private String content;
    private Integer contentLength;
    private LocalDateTime createdAt;
}
