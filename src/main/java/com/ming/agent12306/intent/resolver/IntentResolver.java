package com.ming.agent12306.intent.resolver;

import com.ming.agent12306.intent.config.IntentProperties;
import com.ming.agent12306.intent.fallback.FallbackResponseService;
import com.ming.agent12306.intent.model.GuidanceDecision;
import com.ming.agent12306.intent.model.IntentKind;
import com.ming.agent12306.intent.model.IntentNode;
import com.ming.agent12306.intent.model.IntentTreeData;
import com.ming.agent12306.intent.model.NodeScore;
import com.ming.agent12306.intent.model.SubQuestionIntent;
import com.ming.agent12306.intent.service.IntentClassifier;
import com.ming.agent12306.intent.service.IntentGuidanceService;
import com.ming.agent12306.intent.service.IntentTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 意图解析入口
 */
@Component
public class IntentResolver {

    private static final Logger log = LoggerFactory.getLogger(IntentResolver.class);

    private final IntentTreeService intentTreeService;
    private final IntentClassifier intentClassifier;
    private final IntentGuidanceService intentGuidanceService;
    private final FallbackResponseService fallbackResponseService;
    private final IntentProperties intentProperties;

    public IntentResolver(
            IntentTreeService intentTreeService,
            IntentClassifier intentClassifier,
            IntentGuidanceService intentGuidanceService,
            FallbackResponseService fallbackResponseService,
            IntentProperties intentProperties) {
        this.intentTreeService = intentTreeService;
        this.intentClassifier = intentClassifier;
        this.intentGuidanceService = intentGuidanceService;
        this.fallbackResponseService = fallbackResponseService;
        this.intentProperties = intentProperties;
    }

    /**
     * 解析用户问题的意图
     *
     * @param question 用户问题
     * @return 解析结果
     */
    public IntentResolutionResult resolve(String question) {
        if (!StringUtils.hasText(question)) {
            return IntentResolutionResult.fallback(fallbackResponseService.getFallbackResponse(question));
        }

        // 1. 获取意图树
        IntentTreeData treeData = intentTreeService.getIntentTree();
        if (treeData == null || treeData.getLeafNodes().isEmpty()) {
            log.warn("[resolver] intent tree is empty");
            return IntentResolutionResult.fallback(fallbackResponseService.getFallbackResponse(question));
        }

        // 2. 调用 LLM 分类器
        List<NodeScore> scores = intentClassifier.classifyTargets(question, treeData);
        if (scores.isEmpty()) {
            log.info("[resolver] no intent matched, use fallback");
            return IntentResolutionResult.fallback(fallbackResponseService.getFallbackResponse(question));
        }

        // 3. 过滤和限制
        double minScore = intentProperties.getMinScore();
        int maxCount = intentProperties.getMaxIntentCount();

        List<NodeScore> filtered = scores.stream()
                .filter(s -> s.getScore() >= minScore)
                .limit(maxCount)
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            log.info("[resolver] all scores below threshold, use fallback");
            return IntentResolutionResult.fallback(fallbackResponseService.getFallbackResponse(question));
        }

        // 4. 构建子问题意图
        SubQuestionIntent subIntent = SubQuestionIntent.builder()
                .subQuestion(question)
                .nodeScores(filtered)
                .build();

        // 5. 检测歧义
        GuidanceDecision guidance = intentGuidanceService.detectAmbiguity(question, List.of(subIntent));
        if (guidance.shouldGuide()) {
            log.info("[resolver] ambiguity detected, return guidance");
            return IntentResolutionResult.guidance(guidance.prompt());
        }

        // 6. 返回正常结果
        log.info("[resolver] resolved intents={}", filtered.size());
        return IntentResolutionResult.success(filtered);
    }

    /**
     * 意图解析结果
     */
    public static class IntentResolutionResult {
        private final ResultType type;
        private final List<NodeScore> nodeScores;
        private final String fallbackText;
        private final String guidancePrompt;

        private IntentResolutionResult(ResultType type, List<NodeScore> nodeScores,
                                       String fallbackText, String guidancePrompt) {
            this.type = type;
            this.nodeScores = nodeScores;
            this.fallbackText = fallbackText;
            this.guidancePrompt = guidancePrompt;
        }

        public static IntentResolutionResult success(List<NodeScore> scores) {
            return new IntentResolutionResult(ResultType.SUCCESS, scores, null, null);
        }

        public static IntentResolutionResult guidance(String prompt) {
            return new IntentResolutionResult(ResultType.GUIDANCE, null, null, prompt);
        }

        public static IntentResolutionResult fallback(String text) {
            return new IntentResolutionResult(ResultType.FALLBACK, null, text, null);
        }

        public ResultType getType() {
            return type;
        }

        public List<NodeScore> getNodeScores() {
            return nodeScores;
        }

        public String getFallbackText() {
            return fallbackText;
        }

        public String getGuidancePrompt() {
            return guidancePrompt;
        }

        public boolean isSuccess() {
            return type == ResultType.SUCCESS;
        }

        public boolean isGuidance() {
            return type == ResultType.GUIDANCE;
        }

        public boolean isFallback() {
            return type == ResultType.FALLBACK;
        }

        /**
         * 获取主要的 IntentKind
         */
        public IntentKind getPrimaryKind() {
            if (nodeScores == null || nodeScores.isEmpty()) {
                return null;
            }
            return nodeScores.get(0).getNode().getKind();
        }
    }

    public enum ResultType {
        SUCCESS,  // 正常解析结果
        GUIDANCE, // 需要歧义引导
        FALLBACK  // 兜底响应
    }
}
