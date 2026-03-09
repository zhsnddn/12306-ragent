package com.ming.agent12306.plan.model;

import com.ming.agent12306.model.response.AssistantStreamEvent;

import java.util.List;

public record PlanningExecutionResult(
        String answer,
        FinalAnswerStepResult structuredAnswer,
        List<AssistantStreamEvent> events
) {
}
