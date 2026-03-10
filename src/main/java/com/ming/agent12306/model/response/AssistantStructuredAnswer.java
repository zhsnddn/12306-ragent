package com.ming.agent12306.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** 智能助手结构化回答模型 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssistantStructuredAnswer(
        String summary,
        List<String> ticketHighlights,
        List<String> recommendations,
        List<String> ruleTips
) {
}
