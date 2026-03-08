package com.ming.agent12306.common.preprocess;

import com.ming.agent12306.common.constant.AssistantErrorMessagesConstant;
import com.ming.agent12306.common.constant.TravelQueryTypesConstant;
import com.ming.agent12306.model.extraction.TravelIntentExtraction;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class AssistantMessagePreprocessor {

    private final DashScopeChatModel dashScopeChatModel;

    public AssistantMessagePreprocessor(DashScopeChatModel dashScopeChatModel) {
        this.dashScopeChatModel = dashScopeChatModel;
    }

    public AssistantPreprocessResult preprocess(String message) {
        TravelIntentExtraction extraction = extractTravelIntent(message);
        if (extraction == null) {
            return new AssistantPreprocessResult(true, message);
        }

        if (TravelQueryTypesConstant.OTHER.equalsIgnoreCase(extraction.getQueryType())) {
            return new AssistantPreprocessResult(true, message);
        }

        if (TravelQueryTypesConstant.ROUTE_QUERY.equalsIgnoreCase(extraction.getQueryType())) {
            return preprocessRouteQuery(message, extraction);
        }

        if (!TravelQueryTypesConstant.TICKET_QUERY.equalsIgnoreCase(extraction.getQueryType())) {
            return new AssistantPreprocessResult(true, message);
        }

        if (Boolean.TRUE.equals(extraction.getNeedClarification())) {
            String clarificationQuestion = extraction.getClarificationQuestion();
            return new AssistantPreprocessResult(
                    false,
                    StringUtils.hasText(clarificationQuestion) ? clarificationQuestion : AssistantErrorMessagesConstant.MISSING_TRAVEL_INFO
            );
        }

        String normalizedDate = extraction.getTravelDateNormalized();
        if (!StringUtils.hasText(normalizedDate)) {
            return new AssistantPreprocessResult(false, AssistantErrorMessagesConstant.MISSING_TRAVEL_DATE);
        }

        String validationError = validateTravelDate(normalizedDate);
        if (validationError != null) {
            return new AssistantPreprocessResult(false, validationError);
        }

        return new AssistantPreprocessResult(true, buildStructuredTravelPrompt(message, extraction));
    }

    private AssistantPreprocessResult preprocessRouteQuery(String message, TravelIntentExtraction extraction) {
        if (Boolean.TRUE.equals(extraction.getNeedClarification())) {
            String clarificationQuestion = extraction.getClarificationQuestion();
            return new AssistantPreprocessResult(
                    false,
                    StringUtils.hasText(clarificationQuestion) ? clarificationQuestion : "请补充车次、出发站、到达站和出行日期后再查询经停站。"
            );
        }

        String trainCode = extraction.getTrainCode();
        if (!StringUtils.hasText(trainCode)) {
            return new AssistantPreprocessResult(false, "请提供明确的车次，例如 G814。");
        }

        return new AssistantPreprocessResult(true, buildStructuredRoutePrompt(message, extraction));
    }

    private TravelIntentExtraction extractTravelIntent(String message) {
        ReActAgent extractor = ReActAgent.builder()
                .name("出行参数提取器")
                .description("提取12306票务查询所需的关键参数")
                .sysPrompt(buildExtractionPrompt())
                .model(dashScopeChatModel)
                .maxIters(1)
                .build();

        Msg response = extractor.call(List.of(createUserMessage(message)), TravelIntentExtraction.class).block();
        if (response == null || !response.hasStructuredData()) {
            return null;
        }
        return response.getStructuredData(TravelIntentExtraction.class);
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
                + "queryType 只能是 " + TravelQueryTypesConstant.TICKET_QUERY + "、" + TravelQueryTypesConstant.ROUTE_QUERY + " 或 " + TravelQueryTypesConstant.OTHER + "。"
                + "如果是余票查询，输出 " + TravelQueryTypesConstant.TICKET_QUERY + "，尽量提取 fromStation、toStation、travelDateRaw、travelDateNormalized、seatPreference。"
                + "如果是经停站查询，输出 " + TravelQueryTypesConstant.ROUTE_QUERY + "，尽量提取 trainCode、fromStation、toStation、travelDateRaw、travelDateNormalized。"
                + "travelDateNormalized 必须输出 yyyy-MM-dd。"
                + "当前日期是 " + today + "，12306 可查询日期范围是 " + today + " 到 " + latestAllowedDate + "。"
                + "如果用户日期不明确、缺失、或无法确定具体到某一天，则 needClarification=true，并给出 clarificationQuestion。"
                + "如果不是查票问题，则 queryType=" + TravelQueryTypesConstant.OTHER + "。";
    }

    private String validateTravelDate(String date) {
        try {
            LocalDate travelDate = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            LocalDate latestAllowedDate = today.plusDays(14);
            if (travelDate.isBefore(today) || travelDate.isAfter(latestAllowedDate)) {
                return "出行日期超出12306可查询范围，请提供 " + today + " 到 " + latestAllowedDate + " 之间的日期。";
            }
            return null;
        } catch (DateTimeParseException ex) {
            return AssistantErrorMessagesConstant.INVALID_TRAVEL_DATE;
        }
    }

    private String buildStructuredTravelPrompt(String originalMessage, TravelIntentExtraction extraction) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户原始问题：").append(originalMessage).append("\n");
        builder.append("已结构化提取到以下参数，请优先依据这些参数调用MCP工具核实信息：");
        if (StringUtils.hasText(extraction.getFromStation())) {
            builder.append("\n- 出发站：").append(extraction.getFromStation());
        }
        if (StringUtils.hasText(extraction.getToStation())) {
            builder.append("\n- 到达站：").append(extraction.getToStation());
        }
        if (StringUtils.hasText(extraction.getTravelDateNormalized())) {
            builder.append("\n- 出行日期：").append(extraction.getTravelDateNormalized());
        }
        if (StringUtils.hasText(extraction.getSeatPreference())) {
            builder.append("\n- 席别偏好：").append(extraction.getSeatPreference());
        }
        builder.append("\n请基于上述结构化参数和MCP工具返回结果，用中文给出准确答复。");
        return builder.toString();
    }

    private String buildStructuredRoutePrompt(String originalMessage, TravelIntentExtraction extraction) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户原始问题：").append(originalMessage).append("\n");
        builder.append("这是经停站查询，请优先调用 MCP 的 get-train-route-stations 工具。");
        if (StringUtils.hasText(extraction.getTrainCode())) {
            builder.append("\n- 车次：").append(extraction.getTrainCode());
        }
        if (StringUtils.hasText(extraction.getFromStation())) {
            builder.append("\n- 出发站：").append(extraction.getFromStation());
        }
        if (StringUtils.hasText(extraction.getToStation())) {
            builder.append("\n- 到达站：").append(extraction.getToStation());
        }
        if (StringUtils.hasText(extraction.getTravelDateNormalized())) {
            builder.append("\n- 出行日期：").append(extraction.getTravelDateNormalized());
        }
        builder.append("\n请基于工具查询结果，用中文返回完整经停站信息。");
        return builder.toString();
    }
}
