package com.ming.agent12306.controller;

import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;
import com.ming.agent12306.knowledge.model.KnowledgeDocumentImportResult;
import com.ming.agent12306.knowledge.model.KnowledgeDocumentSummary;
import com.ming.agent12306.knowledge.service.KnowledgeDocumentService;
import com.ming.agent12306.knowledge.service.KnowledgeRetrievalService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 知识库管理接口控制器 */
@Validated
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public KnowledgeController(
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    @PostMapping("/documents")
    public KnowledgeDocumentImportResult importDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category) {
        return knowledgeDocumentService.importDocument(file, title, category);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocumentSummary> listDocuments() {
        return knowledgeDocumentService.listDocuments();
    }

    @DeleteMapping("/documents/{documentId}")
    public void deleteDocument(@PathVariable("documentId") String documentId) {
        knowledgeDocumentService.deleteDocument(documentId);
    }

    @GetMapping("/search")
    public List<KnowledgeChunkRecall> search(@RequestParam("q") @NotBlank String query) {
        return knowledgeRetrievalService.search(query);
    }
}
