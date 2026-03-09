package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.properties.AssistantKnowledgeProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunker {

    private final AssistantKnowledgeProperties knowledgeProperties;

    public KnowledgeChunker(AssistantKnowledgeProperties knowledgeProperties) {
        this.knowledgeProperties = knowledgeProperties;
    }

    public List<String> chunk(String content) {
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(knowledgeProperties.getChunkSize(), 200);
        int overlap = Math.max(0, Math.min(knowledgeProperties.getChunkOverlap(), chunkSize / 2));
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            if (end < normalized.length()) {
                int sentenceBreak = findSentenceBreak(normalized, end, start + (chunkSize / 2));
                if (sentenceBreak > start) {
                    end = sentenceBreak;
                }
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private String normalize(String content) {
        return content == null ? "" : content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private int findSentenceBreak(String content, int end, int minIndex) {
        for (int index = end; index > minIndex; index--) {
            char current = content.charAt(index - 1);
            if (current == '\n' || current == '。' || current == '！' || current == '？' || current == '.') {
                return index;
            }
        }
        return end;
    }
}
