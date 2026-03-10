package com.ming.agent12306.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ming.agent12306.common.exception.BusinessException;
import com.ming.agent12306.knowledge.entity.KnowledgeChunkEntity;
import com.ming.agent12306.knowledge.entity.KnowledgeDocumentEntity;
import com.ming.agent12306.knowledge.mapper.KnowledgeChunkMapper;
import com.ming.agent12306.knowledge.mapper.KnowledgeDocumentMapper;
import com.ming.agent12306.knowledge.model.KnowledgeDocumentImportResult;
import com.ming.agent12306.knowledge.model.KnowledgeDocumentSummary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 知识文档入库编排服务 */
@Service
public class KnowledgeDocumentService {

    private static final java.util.Set<String> SUPPORTED_EXTENSIONS = java.util.Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "md", "markdown", "txt", "html", "htm"
    );

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ObjectStorageService objectStorageService;
    private final KnowledgeDocumentParser documentParser;
    private final KnowledgeChunker knowledgeChunker;
    private final KnowledgeVectorStore knowledgeVectorStore;

    public KnowledgeDocumentService(
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            ObjectStorageService objectStorageService,
            KnowledgeDocumentParser documentParser,
            KnowledgeChunker knowledgeChunker,
            KnowledgeVectorStore knowledgeVectorStore) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.objectStorageService = objectStorageService;
        this.documentParser = documentParser;
        this.knowledgeChunker = knowledgeChunker;
        this.knowledgeVectorStore = knowledgeVectorStore;
    }

    public KnowledgeDocumentImportResult importDocument(MultipartFile file, String title, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        validateSupportedFile(originalFilename);
        String documentId = UUID.randomUUID().toString();
        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : originalFilename;
        String objectKey = buildObjectKey(documentId, originalFilename);
        LocalDateTime now = LocalDateTime.now();

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setDocumentId(documentId);
        document.setTitle(resolvedTitle);
        document.setFileName(originalFilename);
        document.setCategory(StringUtils.hasText(category) ? category.trim() : null);
        document.setBucketName(objectStorageService.bucketName());
        document.setObjectKey(objectKey);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setParseStatus("UPLOADING");
        document.setProgressMessage("文件上传中");
        document.setChunkCount(0);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        knowledgeDocumentMapper.insert(document);

        try {
            byte[] bytes = file.getBytes();
            objectStorageService.upload(objectKey, file.getContentType(), bytes);
            updateStatus(document, "PARSING", "文件已上传，正在解析内容");
            String parsedText = documentParser.parse(bytes);
            updateStatus(document, "CHUNKING", "文档解析完成，正在文本分块");
            List<String> chunks = knowledgeChunker.chunk(parsedText);
            if (chunks.isEmpty()) {
                throw new BusinessException("文档分块后内容为空");
            }

            List<String> chunkIds = persistChunks(documentId, chunks, now);
            updateStatus(document, "VECTORIZING", "文本分块完成，正在向量化入库");
            knowledgeVectorStore.upsert(documentId, chunkIds, chunks);

            document.setParseStatus("READY");
            document.setProgressMessage("知识库入库完成");
            document.setChunkCount(chunks.size());
            document.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(document);
            return new KnowledgeDocumentImportResult(documentId, resolvedTitle, chunks.size(), document.getBucketName(), objectKey);
        } catch (IOException ex) {
            markFailed(document, "文件读取失败: " + ex.getMessage());
            throw new BusinessException("文件读取失败: " + ex.getMessage());
        } catch (RuntimeException ex) {
            markFailed(document, ex.getMessage());
            throw ex;
        }
    }

    public List<KnowledgeDocumentSummary> listDocuments() {
        return knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt))
                .stream()
                .map(item -> new KnowledgeDocumentSummary(
                        item.getDocumentId(),
                        item.getTitle(),
                        item.getFileName(),
                        item.getCategory(),
                        item.getParseStatus(),
                        item.getProgressMessage(),
                        item.getChunkCount() == null ? 0 : item.getChunkCount(),
                        item.getUploadedAt(),
                        item.getUpdatedAt()
                ))
                .toList();
    }

    public void deleteDocument(String documentId) {
        KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getDocumentId, documentId)
                .last("limit 1"));
        if (document == null) {
            throw new BusinessException("文档不存在");
        }

        List<KnowledgeChunkEntity> chunks = knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunkEntity::getChunkIndex));
        List<String> chunkIds = chunks.stream().map(KnowledgeChunkEntity::getChunkId).toList();

        knowledgeVectorStore.delete(chunkIds);
        objectStorageService.delete(document.getObjectKey());
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId));
        knowledgeDocumentMapper.deleteById(document.getId());
    }

    private List<String> persistChunks(String documentId, List<String> chunks, LocalDateTime now) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    String chunkId = UUID.randomUUID().toString();
                    String content = chunks.get(index);
                    KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
                    entity.setChunkId(chunkId);
                    entity.setDocumentId(documentId);
                    entity.setChunkIndex(index);
                    entity.setContent(content);
                    entity.setContentLength(content.length());
                    entity.setCreatedAt(now);
                    knowledgeChunkMapper.insert(entity);
                    return chunkId;
                })
                .toList();
    }

    private void markFailed(KnowledgeDocumentEntity document, String message) {
        document.setParseStatus("FAILED");
        document.setProgressMessage("文档入库失败");
        document.setErrorMessage(message);
        document.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(document);
    }

    private void updateStatus(KnowledgeDocumentEntity document, String status, String progressMessage) {
        document.setParseStatus(status);
        document.setProgressMessage(progressMessage);
        document.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(document);
    }

    private String buildObjectKey(String documentId, String fileName) {
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "knowledge/" + documentId + "/" + sanitized;
    }

    private void validateSupportedFile(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        String extension = extensionIndex > -1 ? fileName.substring(extensionIndex + 1).toLowerCase() : "";
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("当前仅支持 PDF、Word、Excel、Markdown、TXT、HTML 文档导入；图片文件请先转成 PDF 或 OCR 文本后再上传。");
        }
    }
}
