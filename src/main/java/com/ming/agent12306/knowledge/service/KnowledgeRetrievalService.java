package com.ming.agent12306.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ming.agent12306.knowledge.entity.KnowledgeDocumentEntity;
import com.ming.agent12306.knowledge.mapper.KnowledgeDocumentMapper;
import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;
import com.ming.agent12306.properties.AssistantKnowledgeProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeRetrievalService {

    private final AssistantKnowledgeProperties knowledgeProperties;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public KnowledgeRetrievalService(
            AssistantKnowledgeProperties knowledgeProperties,
            KnowledgeVectorStore knowledgeVectorStore,
            KnowledgeDocumentMapper knowledgeDocumentMapper) {
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
    }

    public String enrichPrompt(String query, String currentPrompt) {
        if (!knowledgeProperties.isEnabled() || !StringUtils.hasText(query)) {
            return currentPrompt;
        }

        List<KnowledgeChunkRecall> recalls = search(query);
        if (recalls.isEmpty()) {
            return currentPrompt;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("12306 知识库参考资料：\n");
        builder.append("以下内容来自已导入的 12306 规则文档。对于规则、说明、流程类问题请优先参考；如与实时票务工具结果冲突，以实时工具结果为准。\n");
        for (KnowledgeChunkRecall recall : recalls) {
            builder.append("- 来源：").append(recall.title());
            if (StringUtils.hasText(recall.category())) {
                builder.append("（").append(recall.category()).append("）");
            }
            builder.append("\n  内容：").append(recall.content()).append("\n");
        }
        builder.append("\n").append(currentPrompt);
        return builder.toString();
    }

    public List<KnowledgeChunkRecall> search(String query) {
        List<KnowledgeChunkRecall> recalls = knowledgeVectorStore.search(
                query,
                knowledgeProperties.getRecallTopK(),
                knowledgeProperties.getRecallScoreThreshold()
        );
        if (recalls.isEmpty()) {
            return List.of();
        }

        List<String> documentIds = recalls.stream()
                .map(KnowledgeChunkRecall::documentId)
                .distinct()
                .toList();
        Map<String, KnowledgeDocumentEntity> documentMap = knowledgeDocumentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDocumentEntity>().in(KnowledgeDocumentEntity::getDocumentId, documentIds))
                .stream()
                .collect(Collectors.toMap(KnowledgeDocumentEntity::getDocumentId, Function.identity(), (left, right) -> left));

        return recalls.stream()
                .map(item -> {
                    KnowledgeDocumentEntity document = documentMap.get(item.documentId());
                    return new KnowledgeChunkRecall(
                            item.documentId(),
                            document == null ? item.documentId() : document.getTitle(),
                            document == null ? null : document.getCategory(),
                            item.content()
                    );
                })
                .toList();
    }
}
