package com.ming.agent12306.common.preprocess;

import com.ming.agent12306.common.constant.TravelQueryTypesConstant;
import com.ming.agent12306.common.util.StructuredOutputJsonParser;
import com.ming.agent12306.model.extraction.TravelIntentExtraction;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 出行参数提取预处理组件
 */
@Component
public class TravelIntentExtractor {

    private final DashScopeChatModel dashScopeChatModel;
    private final StructuredOutputJsonParser jsonParser;

    public TravelIntentExtractor(
            DashScopeChatModel dashScopeChatModel,
            StructuredOutputJsonParser jsonParser) {
        this.dashScopeChatModel = dashScopeChatModel;
        this.jsonParser = jsonParser;
    }

    public TravelIntentExtraction extract(String message) {
        ReActAgent extractor = ReActAgent.builder()
                .name("出行参数提取器")
                .description("提取12306票务查询和规则问答所需的关键参数")
                .sysPrompt(buildExtractionPrompt())
                .model(dashScopeChatModel)
                .maxIters(1)
                .build();

        Msg response = extractor.call(List.of(createUserMessage(message))).block();
        if (response == null || !StringUtils.hasText(response.getTextContent())) {
            return null;
        }
        return jsonParser.parse(response.getTextContent(), TravelIntentExtraction.class);
    }

    private Msg createUserMessage(String message) {
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(message)
                .build();
    }

    private String buildExtractionPrompt() {
        LocalDate today = LocalDate.now();
        LocalDate latestAllowedDate = today.plusDays(14);
        return "你是12306票务参数提取器。"
                + "请从用户问题中提取结构化字段。"
                + "必须只输出一个 JSON 对象，不要输出 markdown，不要输出解释说明。"
                + "queryType 只能是 " + TravelQueryTypesConstant.TICKET_QUERY + "、" + TravelQueryTypesConstant.ROUTE_QUERY + " 或 " + TravelQueryTypesConstant.OTHER + "。"
                + "如果是余票查询，输出 " + TravelQueryTypesConstant.TICKET_QUERY + "，尽量提取 fromStation、toStation、travelDateRaw、travelDateNormalized、departureTimePreference、seatPreference。"
                + "如果是经停站查询，输出 " + TravelQueryTypesConstant.ROUTE_QUERY + "，尽量提取 trainCode、fromStation、toStation、travelDateRaw、travelDateNormalized。"
                + "如果用户同时要查规则、候补、退改签、购票说明，请将 needKnowledgeRetrieve 设为 true。"
                + "如果用户表达了“如果没票再告诉我候补规则”这类回退策略，请将 fallbackToRuleWhenNoTicket 设为 true。"
                + "travelDateNormalized 必须输出 yyyy-MM-dd。"
                + "像上午、下午、晚上、早点出发、晚点出发，这些都属于 departureTimePreference，是偏好，不是必填项。"
                + "当前日期是 " + today + "，12306 可查询日期范围是 " + today + " 到 " + latestAllowedDate + "。"
                + "如果用户日期不明确、缺失、或无法确定具体到某一天，则 needClarification=true，并给出 clarificationQuestion。"
                + "请输出字段：queryType, trainCode, fromStation, toStation, travelDateRaw, travelDateNormalized, departureTimePreference, seatPreference, needKnowledgeRetrieve, fallbackToRuleWhenNoTicket, needClarification, clarificationQuestion。"
                + "字段缺失时填 null。";
    }
}
