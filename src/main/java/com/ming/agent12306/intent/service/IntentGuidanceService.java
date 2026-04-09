package com.ming.agent12306.intent.service;

import com.ming.agent12306.intent.config.IntentProperties;
import com.ming.agent12306.intent.model.AmbiguityGroup;
import com.ming.agent12306.intent.model.GuidanceDecision;
import com.ming.agent12306.intent.model.IntentNode;
import com.ming.agent12306.intent.model.NodeScore;
import com.ming.agent12306.intent.model.SubQuestionIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 歧义引导服务
 */
@Service
public class IntentGuidanceService {

    private static final Logger log = LoggerFactory.getLogger(IntentGuidanceService.class);

    private final IntentProperties intentProperties;

    public IntentGuidanceService(IntentProperties intentProperties) {
        this.intentProperties = intentProperties;
    }

    /**
     * 检测是否需要歧义引导
     *
     * @param question     用户原始问题
     * @param subIntents    子问题意图列表
     * @return 引导决策
     */
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        // 1. 检查引导功能是否启用
        if (!intentProperties.getGuidance().isEnabled()) {
            return GuidanceDecision.none();
        }

        // 2. 仅当单个子问题且有多个候选时检测歧义
        if (subIntents == null || subIntents.size() != 1 || subIntents.get(0).getNodeScores().isEmpty()) {
            return GuidanceDecision.none();
        }

        List<NodeScore> scores = subIntents.get(0).getNodeScores();
        if (scores.size() < 2) {
            return GuidanceDecision.none();
        }

        // 3. 查找歧义组（同名但不同类别的意图）
        AmbiguityGroup group = findAmbiguityGroup(scores);
        if (group == null) {
            return GuidanceDecision.none();
        }

        // 4. 检查用户问题是否已包含选项名称（避免重复询问）
        List<String> systemNames = resolveOptionNames(group.optionIds());
        if (shouldSkipGuidance(question, systemNames)) {
            return GuidanceDecision.none();
        }

        // 5. 构建引导 Prompt
        String prompt = buildGuidancePrompt(group.topicName(), group.optionIds());
        log.info("[guidance] triggered for topic={}, options={}", group.topicName(), group.optionIds().size());

        return GuidanceDecision.prompt(prompt);
    }

    /**
     * 查找歧义组：同名但不同 Category 的意图
     */
    private AmbiguityGroup findAmbiguityGroup(List<NodeScore> scores) {
        // 按节点名称分组
        Map<String, List<String>> nameToIds = new HashMap<>();
        for (NodeScore score : scores) {
            String name = score.getNode().getName();
            nameToIds.computeIfAbsent(name, k -> new ArrayList<>()).add(score.getNode().getId());
        }

        // 查找有歧义的组（同名多个ID，且分数相近）
        double ratio = intentProperties.getGuidance().getAmbiguityScoreRatio();
        double maxScore = scores.get(0).getScore();

        for (Map.Entry<String, List<String>> entry : nameToIds.entrySet()) {
            if (entry.getValue().size() > 1) {
                // 检查最高分与次高分的比例
                if (scores.size() >= 2) {
                    double secondScore = scores.get(1).getScore();
                    if (secondScore >= maxScore * ratio) {
                        return new AmbiguityGroup(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        return null;
    }

    /**
     * 解析选项名称
     */
    private List<String> resolveOptionNames(List<String> optionIds) {
        // 提取 Category 名称作为选项名称
        return optionIds.stream()
                .map(id -> {
                    // id 格式: "cat_ticket/topic_route_ticket"
                    if (id.contains("/")) {
                        String catId = id.split("/")[0];
                        return catId.replace("cat_", "");
                    }
                    return id;
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断是否应该跳过引导（用户问题已包含选项信息）
     */
    private boolean shouldSkipGuidance(String question, List<String> systemNames) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String lowerQuestion = question.toLowerCase();
        for (String name : systemNames) {
            if (lowerQuestion.contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建引导话术
     */
    private String buildGuidancePrompt(String topicName, List<String> optionIds) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("关于「").append(topicName).append("」，系统检索到了以下相关内容：\n");

        int maxOptions = Math.min(optionIds.size(), intentProperties.getGuidance().getMaxOptions());
        for (int i = 0; i < maxOptions; i++) {
            String id = optionIds.get(i);
            String catName = id.contains("/") ? id.split("/")[0].replace("cat_", "") : id;
            prompt.append(i + 1).append(". ").append(catName).append("-").append(topicName).append("\n");
        }

        prompt.append("\n请问你具体想了解哪个？\n");
        prompt.append("请回复数字选择（可多选，如 1,2），或回复「都/全部」了解所有内容。");

        return prompt.toString();
    }
}
