package com.ming.agent12306.plan.service;

import com.ming.agent12306.common.constant.AssistantErrorMessagesConstant;
import com.ming.agent12306.common.exception.BusinessException;
import com.ming.agent12306.knowledge.model.KnowledgeChunkRecall;
import com.ming.agent12306.memory.model.ConversationContext;
import com.ming.agent12306.memory.service.AssistantMemoryService;
import com.ming.agent12306.model.extraction.TravelIntentExtraction;
import com.ming.agent12306.model.response.AssistantStreamEvent;
import com.ming.agent12306.plan.model.FinalAnswerStepResult;
import com.ming.agent12306.plan.model.KnowledgeRetrieveStepResult;
import com.ming.agent12306.plan.model.PlanStepContext;
import com.ming.agent12306.plan.model.PlanStepType;
import com.ming.agent12306.plan.model.PlanningExecutionResult;
import com.ming.agent12306.plan.model.TicketQueryStepResult;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.plan.model.SubTask;
import io.agentscope.core.plan.model.SubTaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class TravelPlanningService {

    private static final Logger log = LoggerFactory.getLogger(TravelPlanningService.class);

    private final PlanNotebook planNotebook;
    private final AssistantMemoryService assistantMemoryService;
    private final PlanStepExecutor planStepExecutor;

    public TravelPlanningService(
            PlanNotebook planNotebook,
            AssistantMemoryService assistantMemoryService,
            PlanStepExecutor planStepExecutor) {
        this.planNotebook = planNotebook;
        this.assistantMemoryService = assistantMemoryService;
        this.planStepExecutor = planStepExecutor;
    }

    public PlanningExecutionResult execute(String sessionId, String originalMessage) {
        List<AssistantStreamEvent> events = new ArrayList<>();
        PlanStepContext stepContext = new PlanStepContext(sessionId);
        log.info("[plan] session={} start goal={}", sessionId, abbreviate(originalMessage));
        try {
            createPlan(sessionId, originalMessage, events);

            ConversationContext preprocessContext = assistantMemoryService.loadContext(sessionId, originalMessage);
            String planningInput = assistantMemoryService.buildPreprocessInput(originalMessage, preprocessContext);

            TravelIntentExtraction extraction = runStep(
                    stepContext,
                    PlanStepType.EXTRACT_INTENT,
                    events,
                    "开始提取出行参数",
                    () -> {
                        TravelIntentExtraction extracted = planStepExecutor.extractIntent(stepContext, planningInput);
                        validateExtraction(extracted);
                        return extracted;
                    },
                    this::summarizeIntent
            );

            assistantMemoryService.appendMessage(sessionId, "user", originalMessage);

            TicketQueryStepResult ticketResult = null;
            if (!"OTHER".equalsIgnoreCase(extraction.getQueryType())) {
                ticketResult = runStep(
                        stepContext,
                        PlanStepType.QUERY_TICKET,
                        events,
                        "开始调用票务工具",
                        () -> planStepExecutor.queryTicket(stepContext, extraction),
                        result -> safe(result == null ? null : result.getTicketSummary())
                );
            } else {
                finishSubtask(sessionId, PlanStepType.QUERY_TICKET, "本轮无需票务查询", events);
            }

            KnowledgeRetrieveStepResult knowledgeResult = new KnowledgeRetrieveStepResult();
            if (Boolean.TRUE.equals(extraction.getNeedKnowledgeRetrieve())
                    || (ticketResult != null && Boolean.FALSE.equals(ticketResult.getHasTicket()) && Boolean.TRUE.equals(extraction.getFallbackToRuleWhenNoTicket()))) {
                knowledgeResult = runStep(
                        stepContext,
                        PlanStepType.RETRIEVE_KNOWLEDGE,
                        events,
                        "开始检索12306规则知识",
                        () -> planStepExecutor.retrieveKnowledge(stepContext, originalMessage),
                        result -> safe(result == null ? null : result.getSummary())
                );
            } else {
                finishSubtask(sessionId, PlanStepType.RETRIEVE_KNOWLEDGE, "本轮无需规则检索", events);
            }

            TicketQueryStepResult finalTicketResult = ticketResult;
            KnowledgeRetrieveStepResult finalKnowledgeResult = knowledgeResult;
            FinalAnswerStepResult finalAnswer = runStep(
                    stepContext,
                    PlanStepType.GENERATE_ANSWER,
                    events,
                    "开始生成最终答案",
                    () -> normalizeFinalAnswer(planStepExecutor.generateAnswer(stepContext, originalMessage, extraction, finalTicketResult, finalKnowledgeResult)),
                    this::buildAnswerText
            );
            String answer = buildAnswerText(finalAnswer);
            planNotebook.finishPlan("DONE", answer).block();
            log.info("[plan] session={} finished answer={}", sessionId, abbreviate(answer));

            assistantMemoryService.appendMessage(sessionId, "assistant", answer);
            assistantMemoryService.summarizeIfNecessary(sessionId);

            events.add(new AssistantStreamEvent("agent_result", true, "ASSISTANT", answer, null));
            return new PlanningExecutionResult(answer, finalAnswer, events);
        } catch (Exception ex) {
            log.error("[plan] session={} failed reason={}", sessionId, ex.getMessage(), ex);
            try {
                planNotebook.finishPlan("FAILED", ex.getMessage() == null ? "计划执行失败" : ex.getMessage()).block();
            } catch (Exception finishEx) {
                log.warn("[plan] session={} failed to finish notebook reason={}", sessionId, finishEx.getMessage());
            }
            throw ex;
        }
    }

    public Flux<AssistantStreamEvent> streamExecute(String sessionId, String originalMessage) {
        return Flux.fromIterable(execute(sessionId, originalMessage).events());
    }

    private void createPlan(String sessionId, String originalMessage, List<AssistantStreamEvent> events) {
        List<SubTask> subtasks = List.of(
                new SubTask("EXTRACT_INTENT", "提取出发站、到达站、日期、偏好和规则需求", "得到结构化出行参数"),
                new SubTask("QUERY_TICKET", "调用 12306 MCP 工具查询实时票务信息", "得到实时票务结果"),
                new SubTask("RETRIEVE_KNOWLEDGE", "检索12306规则知识库", "得到规则摘要和参考片段"),
                new SubTask("GENERATE_ANSWER", "汇总票务查询和规则检索结果生成回答", "输出最终建议")
        );
        planNotebook.createPlanWithSubTasks(
                "12306任务编排",
                "针对用户问题进行任务拆解与流程编排",
                "输出可执行的票务建议与规则说明",
                subtasks
        ).block();

        String planMarkdown = planNotebook.getCurrentPlan() == null ? "计划创建完成" : planNotebook.getCurrentPlan().toMarkdown(false);
        log.info("[plan] session={} created {}", sessionId, abbreviate(planMarkdown));
        events.add(new AssistantStreamEvent("plan", false, "ASSISTANT", "已创建任务计划\n" + planMarkdown, null));
        markTodo(sessionId, PlanStepType.EXTRACT_INTENT, events);
        markTodo(sessionId, PlanStepType.QUERY_TICKET, events);
        markTodo(sessionId, PlanStepType.RETRIEVE_KNOWLEDGE, events);
        markTodo(sessionId, PlanStepType.GENERATE_ANSWER, events);
    }

    private void validateExtraction(TravelIntentExtraction extraction) {
        if (extraction == null) {
            throw new BusinessException("参数提取失败，请换一种问法重试。");
        }
        if (Boolean.TRUE.equals(extraction.getNeedClarification())) {
            throw new BusinessException(StringUtils.hasText(extraction.getClarificationQuestion())
                    ? extraction.getClarificationQuestion()
                    : AssistantErrorMessagesConstant.MISSING_TRAVEL_INFO);
        }
        if ("TICKET_QUERY".equalsIgnoreCase(extraction.getQueryType())) {
            if (!StringUtils.hasText(extraction.getFromStation())
                    || !StringUtils.hasText(extraction.getToStation())
                    || !StringUtils.hasText(extraction.getTravelDateNormalized())) {
                throw new BusinessException(AssistantErrorMessagesConstant.MISSING_TRAVEL_INFO);
            }
            validateTravelDate(extraction.getTravelDateNormalized());
        }
    }

    private void validateTravelDate(String date) {
        try {
            LocalDate travelDate = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            LocalDate latestAllowedDate = today.plusDays(14);
            if (travelDate.isBefore(today) || travelDate.isAfter(latestAllowedDate)) {
                throw new BusinessException("出行日期超出12306可查询范围，请提供 " + today + " 到 " + latestAllowedDate + " 之间的日期。");
            }
        } catch (DateTimeParseException ex) {
            throw new BusinessException(AssistantErrorMessagesConstant.INVALID_TRAVEL_DATE);
        }
    }

    private void updateSubtaskState(String sessionId, PlanStepType stepType, SubTaskState state, List<AssistantStreamEvent> events, String text) {
        planNotebook.updateSubtaskState(stepType.index(), state.getValue()).block();
        log.info("[plan] session={} step={} state={} detail={}", sessionId, stepType.code(), state.getValue(), abbreviate(text));
        events.add(new AssistantStreamEvent("plan_step", false, "ASSISTANT", text, null));
    }

    private void finishSubtask(String sessionId, PlanStepType stepType, String outcome, List<AssistantStreamEvent> events) {
        planNotebook.finishSubtask(stepType.index(), outcome).block();
        log.info("[plan] session={} step={} state=done outcome={}", sessionId, stepType.code(), abbreviate(outcome));
        events.add(new AssistantStreamEvent("plan_step", false, "ASSISTANT", outcome, null));
    }

    private void abandonSubtask(String sessionId, PlanStepType stepType, String reason, List<AssistantStreamEvent> events) {
        planNotebook.updateSubtaskState(stepType.index(), SubTaskState.ABANDONED.getValue()).block();
        String detail = "执行失败：" + safe(reason);
        log.warn("[plan] session={} step={} state=abandoned reason={}", sessionId, stepType.code(), abbreviate(detail));
        events.add(new AssistantStreamEvent("plan_step", false, "ASSISTANT", detail, null));
    }

    private void markTodo(String sessionId, PlanStepType stepType, List<AssistantStreamEvent> events) {
        updateSubtaskState(sessionId, stepType, SubTaskState.TODO, events, "等待执行");
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

    private String buildAnswerText(FinalAnswerStepResult result) {
        StringBuilder builder = new StringBuilder();
        if (result == null) {
            return AssistantErrorMessagesConstant.EMPTY_MODEL_RESPONSE;
        }
        if (StringUtils.hasText(result.getSummary())) {
            builder.append("结论：").append(result.getSummary().trim());
        }
        if (result.getTicketHighlights() != null && !result.getTicketHighlights().isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("票务要点：\n");
            result.getTicketHighlights().forEach(item -> builder.append("- ").append(item).append("\n"));
        }
        if (result.getRecommendations() != null && !result.getRecommendations().isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("推荐方案：\n");
            result.getRecommendations().forEach(item -> builder.append("- ").append(item).append("\n"));
        }
        if (result.getRuleTips() != null && !result.getRuleTips().isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("规则提示：\n");
            result.getRuleTips().forEach(item -> builder.append("- ").append(item).append("\n"));
        }
        return builder.toString().trim();
    }

    private FinalAnswerStepResult normalizeFinalAnswer(FinalAnswerStepResult result) {
        if (result == null) {
            FinalAnswerStepResult empty = new FinalAnswerStepResult();
            empty.setSummary(AssistantErrorMessagesConstant.EMPTY_MODEL_RESPONSE);
            empty.setTicketHighlights(List.of());
            empty.setRecommendations(List.of());
            empty.setRuleTips(List.of());
            return empty;
        }
        result.setTicketHighlights(normalizeLines(result.getTicketHighlights()));
        result.setRecommendations(normalizeLines(result.getRecommendations()));
        result.setRuleTips(normalizeLines(result.getRuleTips()));
        return result;
    }

    private List<String> normalizeLines(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<String> normalized = items.stream()
                .filter(StringUtils::hasText)
                .flatMap(item -> item.lines())
                .map(String::trim)
                .map(line -> line.replaceFirst("^[-*\\d.\\s、]+", "").trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        return normalized.isEmpty() ? Collections.emptyList() : normalized;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "无结果";
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "无";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private <T> T runStep(
            PlanStepContext context,
            PlanStepType stepType,
            List<AssistantStreamEvent> events,
            String startText,
            Supplier<T> action,
            Function<T, String> outcomeMapper) {
        updateSubtaskState(context.sessionId(), stepType, SubTaskState.IN_PROGRESS, events, startText);
        try {
            T result = action.get();
            String outcome = safe(outcomeMapper.apply(result));
            finishSubtask(context.sessionId(), stepType, outcome, events);
            return result;
        } catch (Exception ex) {
            abandonSubtask(context.sessionId(), stepType, ex.getMessage(), events);
            throw ex;
        }
    }
}
