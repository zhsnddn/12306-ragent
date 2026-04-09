package com.ming.agent12306.intent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ming.agent12306.common.util.StructuredOutputJsonParser;
import com.ming.agent12306.intent.config.IntentProperties;
import com.ming.agent12306.intent.model.IntentNode;
import com.ming.agent12306.intent.model.IntentTreeData;
import com.ming.agent12306.intent.model.NodeScore;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * LLM 意图分类器
 */
@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private final DashScopeChatModel dashScopeChatModel;
    private final IntentProperties intentProperties;
    private final StructuredOutputJsonParser jsonParser;

    public IntentClassifier(
            DashScopeChatModel dashScopeChatModel,
            IntentProperties intentProperties,
            StructuredOutputJsonParser jsonParser) {
        this.dashScopeChatModel = dashScopeChatModel;
        this.intentProperties = intentProperties;
        this.jsonParser = jsonParser;
    }

    /**
     * 对用户问题进行意图分类
     *
     * @param question 用户问题
     * @param data     意图树数据
     * @return 排序后的节点打分列表
     */
    public List<NodeScore> classifyTargets(String question, IntentTreeData data) {
        if (!StringUtils.hasText(question) || data == null || data.getLeafNodes().isEmpty()) {
            return List.of();
        }

        // Step 1: 构造 Prompt
        String systemPrompt = buildPrompt(data.getLeafNodes());

        // Step 2: 调用 LLM
        ReActAgent classifier = ReActAgent.builder()
                .name("意图分类助手")
                .description("12306票务助手意图分类")
                .sysPrompt(systemPrompt)
                .model(dashScopeChatModel)
                .maxIters(1)
                .build();

        Msg response = classifier.call(List.of(createUserMessage(question))).block();
        String raw = response == null ? null : response.getTextContent();

        if (!StringUtils.hasText(raw)) {
            log.warn("[classifier] empty response from LLM");
            return List.of();
        }

        // Step 3: 解析 JSON 返回
        List<NodeScore> scores = parseScoreResult(raw, data.getId2Node());

        // Step 4: 按分数降序排序
        scores.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());

        log.info("[classifier] question={}, results={}", abbreviate(question), scores.size());
        return scores;
    }

    private String buildPrompt(List<IntentNode> leafNodes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是12306票务助手专业的意图分类助手，擅长理解用户的问题并准确匹配到对应的意图节点。\n\n");

        prompt.append("【评分标准】（必须严格遵循）：\n");
        prompt.append("- score > 0.8: 强匹配，用户问题与意图高度相关\n");
        prompt.append("- score 0.4-0.8: 中等匹配，可能相关但需要更多信息\n");
        prompt.append("- score < 0.4: 弱匹配，基本不相关\n\n");

        prompt.append("【选择规则】：\n");
        prompt.append("1. 默认选择 1 个主意图（最高分）\n");
        prompt.append("2. 如果存在多个分数相近的意图（差距 < 0.2），标记为歧义候选\n");
        prompt.append("3. 最多选择 3 个意图，超过时只保留最高分的 3 个\n\n");

        prompt.append("【输出格式】（必须是有效的 JSON 数组）：\n");
        prompt.append("[{\"id\": \"节点ID\", \"score\": 0.95, \"reason\": \"判断理由\"}]\n\n");

        prompt.append("【12306意图节点列表】：\n");
        for (IntentNode node : leafNodes) {
            prompt.append("- ID: ").append(node.getId()).append("\n");
            prompt.append("  名称: ").append(node.getName()).append("\n");
            prompt.append("  描述: ").append(node.getDescription()).append("\n");
            prompt.append("  类型: ").append(node.getKind()).append("\n");
            if (node.getExamples() != null && !node.getExamples().isEmpty()) {
                prompt.append("  示例: ").append(String.join("；", node.getExamples())).append("\n");
            }
            prompt.append("\n");
        }

        return prompt.toString();
    }

    private List<NodeScore> parseScoreResult(String raw, java.util.Map<String, IntentNode> id2Node) {
        List<NodeScore> results = new ArrayList<>();

        try {
            // 尝试解析为 JSON 数组
            JsonNode root = jsonParser.parseTree(raw);
            if (root == null || !root.isArray()) {
                log.warn("[classifier] response is not JSON array: {}", abbreviate(raw));
                return results;
            }

            for (JsonNode item : root) {
                String id = jsonParser.readText(item, "id");
                Double score = jsonParser.readDouble(item, "score");
                String reason = jsonParser.readText(item, "reason");

                if (StringUtils.hasText(id) && score != null && id2Node.containsKey(id)) {
                    results.add(NodeScore.builder()
                            .node(id2Node.get(id))
                            .score(score)
                            .reason(reason)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[classifier] parse failed: {}, raw={}", e.getMessage(), abbreviate(raw));
        }

        return results;
    }

    private Msg createUserMessage(String message) {
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(message)
                .build();
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "无";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 100 ? normalized.substring(0, 100) + "..." : normalized;
    }
}
