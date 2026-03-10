package com.ming.agent12306.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ming.agent12306.common.constant.AssistantErrorMessagesConstant;
import com.ming.agent12306.common.preprocess.TravelIntentExtractor;
import com.ming.agent12306.common.util.StructuredOutputJsonParser;
import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;
import com.ming.agent12306.knowledge.service.KnowledgeRetrievalService;
import com.ming.agent12306.model.extraction.TravelIntentExtraction;
import com.ming.agent12306.plan.aop.PlanStepLog;
import com.ming.agent12306.plan.model.FinalAnswerStepResult;
import com.ming.agent12306.plan.model.KnowledgeRetrieveStepResult;
import com.ming.agent12306.plan.model.PlanStepContext;
import com.ming.agent12306.plan.model.PlanStepType;
import com.ming.agent12306.plan.model.TicketQueryStepResult;
import com.ming.agent12306.properties.AssistantProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** Plan 步骤执行组件 */
@Component
public class PlanStepExecutor {

    private final AssistantProperties assistantProperties;
    private final DashScopeChatModel dashScopeChatModel;
    private final Toolkit toolkit;
    private final StructuredOutputJsonParser jsonParser;
    private final TravelIntentExtractor travelIntentExtractor;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public PlanStepExecutor(
            AssistantProperties assistantProperties,
            DashScopeChatModel dashScopeChatModel,
            Toolkit toolkit,
            StructuredOutputJsonParser jsonParser,
            TravelIntentExtractor travelIntentExtractor,
            KnowledgeRetrievalService knowledgeRetrievalService) {
        this.assistantProperties = assistantProperties;
        this.dashScopeChatModel = dashScopeChatModel;
        this.toolkit = toolkit;
        this.jsonParser = jsonParser;
        this.travelIntentExtractor = travelIntentExtractor;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    @PlanStepLog(PlanStepType.EXTRACT_INTENT)
    public TravelIntentExtraction extractIntent(PlanStepContext context, String planningInput) {
        return travelIntentExtractor.extract(planningInput);
    }

    @PlanStepLog(PlanStepType.QUERY_TICKET)
    public TicketQueryStepResult queryTicket(PlanStepContext context, TravelIntentExtraction extraction) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("任务：查询12306实时票务信息，并且只输出 JSON。\n");
        prompt.append("请优先调用 12306 MCP 工具完成查询。\n");
        prompt.append("字段：success, hasTicket, ticketSummary, trainOptions。\n");
        prompt.append("如果无票，hasTicket=false，ticketSummary 要明确说明无票情况。\n");
        prompt.append("调用工具时，必须严格使用下面提供的标准化参数；不要使用用户原始问题中的“明天”“下午”等相对或模糊时间作为工具参数。\n");
        prompt.append("已知参数：\n");
        prompt.append("- 出发站(from_station)：").append(extraction.getFromStation()).append("\n");
        prompt.append("- 到达站(to_station)：").append(extraction.getToStation()).append("\n");
        prompt.append("- 标准化出行日期(train_date)：").append(extraction.getTravelDateNormalized()).append("\n");
        if (StringUtils.hasText(extraction.getDepartureTimePreference())) {
            prompt.append("- 出发时间偏好（仅用于结果筛选，不是工具参数）：").append(extraction.getDepartureTimePreference()).append("\n");
        }
        if (StringUtils.hasText(extraction.getSeatPreference())) {
            prompt.append("- 席别偏好（仅用于结果筛选）：").append(extraction.getSeatPreference()).append("\n");
        }
        prompt.append("如果查询成功，请优先返回与时间偏好最匹配的车次；如果工具报时间不合理，不要自行编造结果，应直接说明工具查询失败。\n");

        Msg response = createToolAgent("票务查询执行器", "负责查询实时票务并输出 JSON")
                .call(List.of(createUserMessage(prompt.toString())))
                .block();
        TicketQueryStepResult result = response == null ? null : jsonParser.parse(response.getTextContent(), TicketQueryStepResult.class);
        if (result != null) {
            return result;
        }

        TicketQueryStepResult fallback = new TicketQueryStepResult();
        fallback.setSuccess(Boolean.TRUE);
        fallback.setHasTicket(null);
        fallback.setTicketSummary(response == null ? "未获取到票务结果" : response.getTextContent());
        fallback.setTrainOptions(List.of());
        return fallback;
    }

    @PlanStepLog(PlanStepType.RETRIEVE_KNOWLEDGE)
    public KnowledgeRetrieveStepResult retrieveKnowledge(PlanStepContext context, String originalMessage) {
        List<KnowledgeChunkRecall> recalls = knowledgeRetrievalService.search(originalMessage);
        KnowledgeRetrieveStepResult result = new KnowledgeRetrieveStepResult();
        result.setSuccess(!recalls.isEmpty());
        result.setReferences(recalls.stream()
                .map(item -> item.title() + "：" + item.content())
                .toList());
        result.setSummary(recalls.isEmpty()
                ? "知识库未检索到直接相关规则。"
                : recalls.stream().limit(3).map(KnowledgeChunkRecall::content).reduce((a, b) -> a + "\n" + b).orElse(""));
        return result;
    }

    @PlanStepLog(PlanStepType.GENERATE_ANSWER)
    public FinalAnswerStepResult generateAnswer(
            PlanStepContext context,
            String originalMessage,
            TravelIntentExtraction extraction,
            TicketQueryStepResult ticketResult,
            KnowledgeRetrieveStepResult knowledgeResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是12306智能助手，请根据结构化步骤结果生成最终答复，并且只输出 JSON。\n");
        prompt.append("字段：summary, ticketHighlights, recommendations, ruleTips。\n");
        prompt.append("除 summary 外，其余字段必须返回 JSON 数组；即使只有一条也必须用数组包裹。\n");
        prompt.append("JSON 示例：");
        prompt.append("{\"summary\":\"明天下午杭州到南京有多趟高铁可选。\",");
        prompt.append("\"ticketHighlights\":[\"二等座余票较充足\",\"下午时段车次较多\"],");
        prompt.append("\"recommendations\":[\"优先选择更接近下午出发的车次\",\"尽快下单以锁定余票\"],");
        prompt.append("\"ruleTips\":[\"无票时可考虑候补购票\"]}\n");
        prompt.append("用户问题：").append(originalMessage).append("\n");
        prompt.append("结构化参数：").append(summarizeIntent(extraction)).append("\n");
        if (ticketResult != null) {
            prompt.append("票务结果：").append(safe(ticketResult.getTicketSummary())).append("\n");
            prompt.append("是否有票：").append(ticketResult.getHasTicket()).append("\n");
            if (ticketResult.getTrainOptions() != null && !ticketResult.getTrainOptions().isEmpty()) {
                prompt.append("可选车次：").append(String.join("；", ticketResult.getTrainOptions())).append("\n");
            }
        }
        if (knowledgeResult != null && knowledgeResult.getReferences() != null && !knowledgeResult.getReferences().isEmpty()) {
            prompt.append("规则检索结果：").append(String.join("\n", knowledgeResult.getReferences())).append("\n");
        }
        prompt.append("要求：中文回答，先给结论，再给票务要点，再给推荐方案，最后给规则提示；不要输出 markdown 代码块。");

        Msg response = createPlainAgent("任务回答生成器", "根据执行步骤生成最终结构化回答")
                .call(List.of(createUserMessage(prompt.toString())))
                .block();
        FinalAnswerStepResult result = parseFinalAnswer(response == null ? null : response.getTextContent());
        if (result != null) {
            return result;
        }

        FinalAnswerStepResult fallback = new FinalAnswerStepResult();
        fallback.setSummary(response == null ? AssistantErrorMessagesConstant.EMPTY_MODEL_RESPONSE : response.getTextContent());
        fallback.setTicketHighlights(List.of());
        fallback.setRecommendations(List.of());
        fallback.setRuleTips(List.of());
        return fallback;
    }

    private ReActAgent createToolAgent(String name, String description) {
        return ReActAgent.builder()
                .name(name)
                .description(description)
                .sysPrompt(assistantProperties.getSystemPrompt())
                .model(dashScopeChatModel)
                .toolkit(toolkit)
                .maxIters(assistantProperties.getMaxIters())
                .build();
    }

    private ReActAgent createPlainAgent(String name, String description) {
        return ReActAgent.builder()
                .name(name)
                .description(description)
                .sysPrompt("你是结构化输出助手，必须严格按照用户指定的 JSON 字段返回结果。")
                .model(dashScopeChatModel)
                .maxIters(1)
                .build();
    }

    private Msg createUserMessage(String message) {
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(message)
                .build();
    }

    private FinalAnswerStepResult parseFinalAnswer(String content) {
        FinalAnswerStepResult parsed = jsonParser.parse(content, FinalAnswerStepResult.class);
        if (parsed != null) {
            return parsed;
        }
        JsonNode root = jsonParser.parseTree(content);
        if (root == null || !root.isObject()) {
            return null;
        }
        FinalAnswerStepResult fallback = new FinalAnswerStepResult();
        fallback.setSummary(jsonParser.readText(root, "summary"));
        fallback.setTicketHighlights(jsonParser.readTextList(root, "ticketHighlights"));
        fallback.setRecommendations(jsonParser.readTextList(root, "recommendations"));
        fallback.setRuleTips(jsonParser.readTextList(root, "ruleTips"));
        if (!StringUtils.hasText(fallback.getSummary())
                && fallback.getTicketHighlights().isEmpty()
                && fallback.getRecommendations().isEmpty()
                && fallback.getRuleTips().isEmpty()) {
            return null;
        }
        return fallback;
    }

    private String summarizeIntent(TravelIntentExtraction extraction) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(extraction.getFromStation())) {
            builder.append("出发站=").append(extraction.getFromStation()).append("; ");
        }
        if (StringUtils.hasText(extraction.getToStation())) {
            builder.append("到达站=").append(extraction.getToStation()).append("; ");
        }
        if (StringUtils.hasText(extraction.getTravelDateNormalized())) {
            builder.append("日期=").append(extraction.getTravelDateNormalized()).append("; ");
        }
        if (StringUtils.hasText(extraction.getDepartureTimePreference())) {
            builder.append("时间偏好=").append(extraction.getDepartureTimePreference()).append("; ");
        }
        if (StringUtils.hasText(extraction.getSeatPreference())) {
            builder.append("席别偏好=").append(extraction.getSeatPreference()).append("; ");
        }
        builder.append("规则检索=").append(Boolean.TRUE.equals(extraction.getNeedKnowledgeRetrieve()));
        return builder.toString();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "无结果";
    }
}
